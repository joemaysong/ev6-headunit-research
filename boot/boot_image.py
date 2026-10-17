"""Offline legacy Android boot parser and hash-locked local patch generator.

No ADB, Fastboot, partition write, or device discovery is performed here.
"""
import argparse
import gzip
import hashlib
import json
import struct
from pathlib import Path

STOCK_BOOT = "dc4ec8bd8cef335515fda4a11b723b1155e169f4d90ddc79a499cb00c5bacc0b"
STOCK_ADBD = "1d2b7a6e468817a607361f4d619f842e509125bd0adcbeb207039bfed90c3572"
PATCHES = (
    (0x91F6 - 0x8000, "0ef072ec", "002000bf", "skip capability bounding-set drop"),
    (0x9240 - 0x8000, "4ff4fa60", "4ff00000", "set GID argument to zero"),
    (0x924C - 0x8000, "4ff4fa60", "4ff00000", "set UID argument to zero"),
)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def align(value, boundary):
    return (value + boundary - 1) // boundary * boundary


def cpio_members(data):
    position = 0
    while position + 110 <= len(data):
        header = data[position:position + 110]
        if header[:6] != b"070701":
            raise ValueError("Expected unchecksummed newc archive")
        values = [int(header[6 + i * 8:14 + i * 8], 16) for i in range(13)]
        size, namesize = values[6], values[11]
        if namesize < 1:
            raise ValueError("Invalid cpio name length")
        name_end = position + 110 + namesize
        if name_end > len(data) or data[name_end - 1] != 0:
            raise ValueError("Truncated cpio name")
        name = data[position + 110:name_end - 1].decode("utf-8")
        start = align(name_end, 4)
        end = align(start + size, 4)
        if end > len(data):
            raise ValueError("Truncated cpio member")
        yield name, start, size, position, end
        position = end
        if name == "TRAILER!!!":
            return
    raise ValueError("cpio trailer missing")


def parse(data):
    if len(data) < 2048 or data[:8] != b"ANDROID!":
        raise ValueError("Not a legacy Android boot image")
    ks, ka, rs, ra, ss, sa, ta, page, dt, unused = struct.unpack_from("<10I", data, 8)
    if page < 2048 or page > 65536 or page & (page - 1):
        raise ValueError("Unsupported page size")
    if ss or dt:
        raise ValueError("This generator supports the verified no-second/no-dt header")
    ro = page + align(ks, page)
    end = ro + align(rs, page)
    if end > len(data):
        raise ValueError("Truncated boot image")
    kernel, ramdisk = data[page:page + ks], data[ro:ro + rs]
    image_id = hashlib.sha1()
    for blob in (kernel, ramdisk, b""):
        image_id.update(blob)
        image_id.update(struct.pack("<I", len(blob)))
    if data[576:596] != image_id.digest():
        raise ValueError("Android image ID mismatch")
    cpio = gzip.decompress(ramdisk)
    members = list(cpio_members(cpio))
    return dict(page=page, kernel=kernel, ramdisk=ramdisk, cpio=cpio,
                members=members, ramdisk_offset=ro, logical_end=end)


def rebuild(original, cpio):
    info = parse(original)
    if any(original[info["logical_end"]:]):
        raise ValueError("Unknown nonzero boot footer; refusing to discard it")
    list(cpio_members(cpio))
    ramdisk = gzip.compress(cpio, compresslevel=9, mtime=0)
    header = bytearray(original[:info["page"]])
    struct.pack_into("<I", header, 16, len(ramdisk))
    digest = hashlib.sha1()
    for blob in (info["kernel"], ramdisk, b""):
        digest.update(blob)
        digest.update(struct.pack("<I", len(blob)))
    header[576:608] = digest.digest() + bytes(12)
    output = bytearray(header)
    for blob in (info["kernel"], ramdisk):
        output.extend(blob)
        output.extend(bytes((-len(blob)) % info["page"]))
    if len(output) > len(original):
        raise ValueError("Repacked image exceeds original partition capture")
    output.extend(bytes(len(original) - len(output)))
    rebuilt = parse(bytes(output))
    if rebuilt["kernel"] != info["kernel"] or rebuilt["cpio"] != cpio:
        raise ValueError("Repacked payload verification failed")
    return bytes(output)


def root_candidate(original):
    if sha(original) != STOCK_BOOT:
        raise ValueError("Stock boot hash does not match the single verified profile")
    info = parse(original)
    entries = [entry for entry in info["members"] if entry[0] == "sbin/adbd"]
    if len(entries) != 1:
        raise ValueError("Expected exactly one original adbd")
    _, start, size, _, _ = entries[0]
    old = info["cpio"][start:start + size]
    if sha(old) != STOCK_ADBD:
        raise ValueError("Original adbd hash mismatch")
    patched = bytearray(info["cpio"])
    allowed = set()
    for offset, before, after, _ in PATCHES:
        if old[offset:offset + 4] != bytes.fromhex(before):
            raise ValueError("Unexpected instruction at a hash-locked patch site")
        patched[start + offset:start + offset + 4] = bytes.fromhex(after)
        allowed.update(range(start + offset, start + offset + 4))
    changed = {i for i, (a, b) in enumerate(zip(info["cpio"], patched)) if a != b}
    if not changed <= allowed or len(changed) != 10:
        raise ValueError("Patch exceeded the verified ten-byte scope")
    return rebuild(original, bytes(patched)), len(changed)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("inspect", "repack", "root"))
    parser.add_argument("input", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    original = args.input.read_bytes()
    info = parse(original)
    report = dict(mode=args.mode, input_sha256=sha(original), bytes=len(original),
                  page_size=info["page"], kernel_bytes=len(info["kernel"]),
                  gzip_ramdisk_bytes=len(info["ramdisk"]),
                  cpio_members=len(info["members"]),
                  trailing_nonzero_bytes=sum(bool(v) for v in original[info["logical_end"]:]),
                  no_device_operation=True)
    if args.mode != "inspect":
        if args.output is None:
            parser.error("--output is required")
        resolved = args.output.resolve()
        if resolved == Path("/dev") or Path("/dev") in resolved.parents or resolved.exists():
            parser.error("Output must be a new ordinary local file outside /dev")
        if args.mode == "root":
            result, count = root_candidate(original)
            report["changed_adbd_bytes"] = count
        else:
            result = rebuild(original, info["cpio"])
        with resolved.open("xb") as output:
            output.write(result)
        report.update(output_sha256=sha(result), bytes=len(result),
                      local_generation_only=True,
                      runtime_verification="See historical validation; this invocation did not boot anything")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
