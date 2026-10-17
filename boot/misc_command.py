"""Generate an offline 32-byte bootloader-command change in a private misc copy.

No device command is executed. Restoring a command requires the private original.
"""
import argparse
import hashlib
import json
from pathlib import Path

MISC_BYTES = 4 * 1024 * 1024
BOOTLOADER = b"boot-bootloader".ljust(32, b"\0")


def candidate(original):
    if len(original) != MISC_BYTES:
        raise ValueError("Expected the verified 4 MiB misc capture")
    if original[:32] not in (bytes(32), BOOTLOADER):
        raise ValueError("Another boot command is present; preserve it for review")
    return BOOTLOADER + original[32:]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    dest = args.output.resolve()
    if dest.exists() or dest == Path("/dev") or Path("/dev") in dest.parents:
        parser.error("Output must be a new ordinary file outside /dev")
    original = args.input.read_bytes()
    result = candidate(original)
    with dest.open("xb") as file:
        file.write(result)
    print(json.dumps(dict(bytes=len(result), changed_field_bytes=32,
                          bytes_after_field_unchanged=result[32:] == original[32:],
                          input_sha256=hashlib.sha256(original).hexdigest(),
                          output_sha256=hashlib.sha256(result).hexdigest(),
                          device_written=False), indent=2))


if __name__ == "__main__":
    main()
