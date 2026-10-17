"""Verify and extract private gzip-chunk backups without writing to a device."""
import argparse
import gzip
import hashlib
import json
import os
import struct
import zlib
from pathlib import Path


def child(root, name):
    root = root.resolve()
    result = (root / name).resolve()
    if root not in result.parents or not result.is_file():
        raise ValueError("Backup member is missing or outside the archive directory")
    return result


def chunks(root, device):
    if device not in ("mmcblk0", "mmcblk0boot0", "mmcblk0boot1"):
        raise ValueError("Unsupported source device")
    manifest = json.loads(child(root, "current-emmc/" + device + "/manifest.json").read_text())
    if not manifest.get("complete"):
        raise ValueError("Capture is incomplete")
    end = 0
    for index, record in enumerate(manifest["chunks"]):
        if record["index"] != index or record["offset"] != end or record["raw_bytes"] < 1:
            raise ValueError("Chunk coverage is not contiguous")
        child(root, record["path"])
        end += record["raw_bytes"]
    if end != manifest["expected_bytes"]:
        raise ValueError("Chunk coverage differs from expected device length")
    return manifest


def gpt(buffer, header_offset, base, total):
    header = bytearray(buffer[header_offset:header_offset + 512])
    if len(header) != 512 or header[:8] != b"EFI PART":
        raise ValueError("GPT signature missing")
    size, expected_crc = struct.unpack_from("<II", header, 12)
    if not 92 <= size <= 512:
        raise ValueError("GPT header size invalid")
    struct.pack_into("<I", header, 16, 0)
    if zlib.crc32(header[:size]) & 0xFFFFFFFF != expected_crc:
        raise ValueError("GPT header CRC mismatch")
    if total % 512 or total < 68 * 512 or base % 512:
        raise ValueError("Unsupported disk geometry")
    last_lba = total // 512 - 1
    current, alternate, usable_first, usable_last = struct.unpack_from("<4Q", header, 24)
    actual = (base + header_offset) // 512
    if current != actual or current not in (1, last_lba) or alternate != (last_lba if current == 1 else 1):
        raise ValueError("GPT header location mismatch")
    if not 2 <= usable_first <= usable_last < last_lba:
        raise ValueError("GPT usable range invalid")
    lba = struct.unpack_from("<Q", header, 72)[0]
    count, entry_size, expected_entries = struct.unpack_from("<III", header, 80)
    if not 0 < count <= 4096 or not 128 <= entry_size <= 4096 or count * entry_size > 1048576:
        raise ValueError("GPT table size unsupported")
    table_last = lba + (count * entry_size + 511) // 512 - 1
    if lba < 2 or table_last >= last_lba or not (table_last < usable_first or lba > usable_last):
        raise ValueError("GPT table overlaps usable space or a header")
    begin = lba * 512 - base
    if begin < 0 or begin + count * entry_size > len(buffer):
        raise ValueError("GPT table outside captured verification window")
    entries = buffer[begin:begin + count * entry_size]
    if zlib.crc32(entries) & 0xFFFFFFFF != expected_entries:
        raise ValueError("GPT entries CRC mismatch")
    parts = []
    for index in range(count):
        entry = entries[index * entry_size:(index + 1) * entry_size]
        if entry[:16] == bytes(16):
            continue
        first, last = struct.unpack_from("<QQ", entry, 32)
        name = entry[56:128].decode("utf-16-le").rstrip("\0")
        if not usable_first <= first <= last <= usable_last or not name:
            raise ValueError("GPT partition range invalid")
        parts.append(dict(name=name, offset=first * 512, bytes=(last - first + 1) * 512))
    parts.sort(key=lambda part: part["offset"])
    if len({part["name"] for part in parts}) != len(parts):
        raise ValueError("GPT names are ambiguous")
    if any(a["offset"] + a["bytes"] > b["offset"] for a, b in zip(parts, parts[1:])):
        raise ValueError("GPT partitions overlap")
    return parts


def compressed_hash(path):
    result = hashlib.sha256()
    with path.open("rb") as file:
        while data := file.read(4 * 1024 * 1024):
            result.update(data)
    return result.hexdigest()


def raw_blocks(root, manifest):
    for record in manifest["chunks"]:
        path = child(root, record["path"])
        if compressed_hash(path) != record["gzip_sha256"]:
            raise ValueError("Compressed chunk hash mismatch")
        digest, size = hashlib.sha256(), 0
        with gzip.open(path, "rb") as file:
            while data := file.read(4 * 1024 * 1024):
                size += len(data)
                if size > record["raw_bytes"]:
                    raise ValueError("Uncompressed chunk exceeds declared length")
                digest.update(data)
                yield data
        if size != record["raw_bytes"] or digest.hexdigest() != record["raw_sha256"]:
            raise ValueError("Raw chunk verification failed")


def verify(root):
    root = root.resolve()
    main = chunks(root, "mmcblk0")
    with gzip.open(child(root, main["chunks"][0]["path"]), "rb") as file:
        head = file.read(min(1048576, main["expected_bytes"]))
    parts = gpt(head, 512, 0, main["expected_bytes"])
    hashes = [hashlib.sha256() for part in parts]
    observed = [0 for part in parts]
    full, position, tail = hashlib.sha256(), 0, b""
    for data in raw_blocks(root, main):
        full.update(data)
        for index, part in enumerate(parts):
            begin = max(position, part["offset"])
            end = min(position + len(data), part["offset"] + part["bytes"])
            if begin < end:
                hashes[index].update(data[begin - position:end - position])
                observed[index] += end - begin
        position += len(data)
        tail = (tail + data)[-1048576:]
    if full.hexdigest() != main["raw_sha256"] or position != main["expected_bytes"]:
        raise ValueError("Whole user-area hash mismatch")
    backup = gpt(tail, len(tail) - 512, position - len(tail), position)
    if backup != parts:
        raise ValueError("Primary and backup GPT layouts disagree")
    for index, part in enumerate(parts):
        if observed[index] != part["bytes"]:
            raise ValueError("Partition coverage incomplete")
        part["sha256"] = hashes[index].hexdigest()
    boot_results = []
    for device in ("mmcblk0boot0", "mmcblk0boot1"):
        manifest = chunks(root, device)
        digest, length = hashlib.sha256(), 0
        for data in raw_blocks(root, manifest):
            digest.update(data)
            length += len(data)
        if digest.hexdigest() != manifest["raw_sha256"] or length != manifest["expected_bytes"]:
            raise ValueError("Boot region verification failed")
        boot_results.append(dict(device=device, bytes=length, sha256=digest.hexdigest()))
    return dict(complete_user_area_and_boot_regions=True, atomic_snapshot=False,
                main_sha256=full.hexdigest(), gpt_primary_and_backup_crc_valid=True,
                partitions=parts, boot_regions=boot_results,
                raw_bytes=position + sum(item["bytes"] for item in boot_results),
                not_included=["RPMB", "Other ECUs", "Volatile RAM"])


def extract(root, partition, output):
    output = output.resolve()
    if output.exists() or output == Path("/dev") or Path("/dev") in output.parents:
        raise ValueError("Output must be a new ordinary file outside /dev")
    verification = json.loads(child(root, "verification.json").read_text())
    matches = [part for part in verification["partitions"] if part["name"] == partition]
    if len(matches) != 1:
        raise ValueError("Partition name not uniquely verified")
    part = matches[0]
    manifest = chunks(root, "mmcblk0")
    digest, observed, position = hashlib.sha256(), 0, 0
    temporary = output.with_name(output.name + ".partial")
    # Keep a failed partial for diagnosis; never replace an existing output.
    with temporary.open("xb") as file:
        for data in raw_blocks(root, manifest):
            begin = max(position, part["offset"])
            end = min(position + len(data), part["offset"] + part["bytes"])
            if begin < end:
                piece = data[begin - position:end - position]
                file.write(piece)
                digest.update(piece)
                observed += len(piece)
            position += len(data)
    if observed != part["bytes"] or digest.hexdigest() != part["sha256"]:
        raise ValueError("Extraction hash mismatch; partial output retained")
    os.link(temporary, output)
    temporary.unlink()
    return dict(bytes=observed, sha256=digest.hexdigest(), device_written=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("operation", choices=("verify", "extract"))
    parser.add_argument("backup", type=Path)
    parser.add_argument("--partition")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if args.operation == "verify":
        result = verify(args.backup)
        target = args.backup / "verification.json"
        if target.exists():
            if json.loads(target.read_text()) != result:
                raise SystemExit("Existing verification differs; preserve it for review")
        else:
            with target.open("x") as file:
                file.write(json.dumps(result, indent=2) + "\n")
    else:
        if not args.partition or args.output is None:
            parser.error("extract requires --partition and --output")
        result = extract(args.backup, args.partition, args.output)
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
