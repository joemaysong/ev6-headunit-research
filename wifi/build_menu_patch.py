"""Offline Settings_W ODEX patch; no OEM input or output is redistributed."""
import argparse
import hashlib
import json
import struct
import zlib
from pathlib import Path

STOCK_MD5 = "9ae14ab3c2ddbfd1608abb14f44f5b0a"
EXPECTED_SHA256 = "ae13b928bd5a99aa4d7cb2f17fbcc5a2d21ac5294b164717ce78bc91bc29be07"
CLICK = bytes.fromhex(
    "f4834001f8201a0093000c031f039203f8100f0003000a0314041c000c7f33431300"
    "f4834c017110171603000c046e20d300430012026a023f0a00000000000000000000"
)


def patch(original):
    if hashlib.md5(original).hexdigest() != STOCK_MD5:
        raise ValueError("Settings_W ODEX differs from the verified firmware")
    result = bytearray(original)
    dex_offset, dex_size = struct.unpack_from("<II", result, 8)
    dex = bytearray(result[dex_offset:dex_offset + dex_size])
    hidden = 1071960
    if dex[hidden:hidden + 4] != bytes.fromhex("3d010400"):
        raise ValueError("Wi-Fi visibility branch mismatch")
    dex[hidden:hidden + 4] = bytes(4)
    if len(CLICK) != 0xC6540 - 0xC64FC:
        raise ValueError("Click dispatch patch length mismatch")
    dex[0xC64FC:0xC6540] = CLICK
    dex[12:32] = hashlib.sha1(dex[32:]).digest()
    struct.pack_into("<I", dex, 8, zlib.adler32(dex[12:]) & 0xFFFFFFFF)
    result[dex_offset:dex_offset + dex_size] = dex
    if hashlib.sha256(result).hexdigest() != EXPECTED_SHA256:
        raise ValueError("Patch did not reproduce the validated ODEX")
    return bytes(result)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("original", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    dest = args.output.resolve()
    if dest.exists() or dest == Path("/dev") or Path("/dev") in dest.parents:
        parser.error("Output must be a new ordinary local file")
    result = patch(args.original.read_bytes())
    with dest.open("xb") as output:
        output.write(result)
    print(json.dumps(dict(sha256=hashlib.sha256(result).hexdigest(),
                          bytes=len(result), no_device_operation=True)))


if __name__ == "__main__":
    main()
