"""Explicit read-only eMMC capture; private images go outside this repository."""
import argparse
import gzip
import hashlib
import json
import re
import shutil
import subprocess
import time
import uuid
from pathlib import Path

PROFILE = (("mmcblk0boot0", 33030144), ("mmcblk0boot1", 33030144),
           ("mmcblk0", 63652757504))
REPOSITORY = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--capture", action="store_true")
    args = parser.parse_args()
    if not args.capture:
        parser.error("--capture is required; no device operation was performed")
    dest = args.output.resolve()
    if dest.exists() or REPOSITORY == dest or REPOSITORY in dest.parents:
        parser.error("Use a new private directory outside the repository")
    if shutil.disk_usage(dest.parent).free < 5 * 1024 ** 3:
        parser.error("At least 5 GiB of free space is required; compressed size is not guaranteed")
    adb = [args.adb, "-s", args.serial]

    def command(*arguments):
        result = subprocess.run(adb + list(arguments), capture_output=True, timeout=300)
        if result.returncode:
            raise RuntimeError("ADB operation failed; private partials retained")
        return result.stdout.decode("utf-8", errors="replace").replace("\r", "")

    def shell(text):
        output = command("shell", "{\nset -e\n" + text +
                         "\n}; _capture_rc=$?; echo; echo CAPTURE_RC=$_capture_rc")
        if not re.search(r"\nCAPTURE_RC=0\s*$", output):
            raise RuntimeError("Remote operation failed; private partials retained")
        return output

    if "uid=0(root)" not in shell("id"):
        parser.error("Verified root ADB is required")
    for device, expected in PROFILE:
        value = shell("cat /sys/block/" + device + "/size").splitlines()[0]
        if not value.isdigit() or int(value) * 512 != expected:
            parser.error("Block geometry differs from the tested profile")
    dest.mkdir(mode=0o700)
    run = uuid.uuid4().hex
    for device, expected in PROFILE:
        folder = dest / "current-emmc" / device
        folder.mkdir(parents=True, mode=0o700)
        full, records = hashlib.sha256(), []
        for index, offset in enumerate(range(0, expected, 256 * 1024 ** 2)):
            size = min(256 * 1024 ** 2, expected - offset)
            if shutil.disk_usage(folder).free < size + 1024 ** 3:
                raise RuntimeError("Insufficient space for the next chunk; existing private chunks retained")
            block = 1048576 if size % 1048576 == 0 else 512
            stage = "/dev/ev6-public-backup-" + run + "-" + str(index) + ".gz"
            partial = folder / (str(index).zfill(4) + ".img.gz.partial")
            final = folder / (str(index).zfill(4) + ".img.gz")
            shell("umask 077\ntest ! -e " + stage +
                  "\n/system/bin/dd if=/dev/block/" + device + " bs=" + str(block) +
                  " skip=" + str(offset // block) + " count=" + str(size // block) +
                  " 2>/dev/null | /system/bin/gzip -1 -c > " + stage)
            try:
                remote = shell("md5 " + stage)
                expected_md5 = re.search(r"\b[0-9a-f]{32}\b", remote)
                if expected_md5 is None:
                    raise RuntimeError("Compressed source hash unavailable")
                command("pull", stage, str(partial))
                compressed_md5, compressed_sha = hashlib.md5(), hashlib.sha256()
                with partial.open("rb") as source:
                    while data := source.read(4 * 1024 * 1024):
                        compressed_md5.update(data)
                        compressed_sha.update(data)
                if compressed_md5.hexdigest() != expected_md5[0]:
                    raise RuntimeError("Compressed source-to-disk mismatch")
                raw, count = hashlib.sha256(), 0
                with gzip.open(partial, "rb") as file:
                    while data := file.read(4 * 1024 * 1024):
                        count += len(data)
                        if count > size:
                            raise RuntimeError("Chunk exceeded declared raw length")
                        raw.update(data)
                        full.update(data)
                if count != size:
                    raise RuntimeError("Chunk raw length mismatch")
                partial.rename(final)
                records.append(dict(index=index, offset=offset, raw_bytes=count,
                                    raw_sha256=raw.hexdigest(),
                                    gzip_sha256=compressed_sha.hexdigest(),
                                    gzip_source_md5=expected_md5[0],
                                    path=str(final.relative_to(dest))))
                manifest = dict(device=device, expected_bytes=expected, chunks=records,
                                complete=offset + size == expected,
                                raw_sha256=full.hexdigest(), atomic_snapshot=False)
                (folder / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
                print(device, offset + size, "/", expected, flush=True)
            finally:
                shell("rm -f " + stage)
    print("Capture saved privately. Reopen and verify with backup/archive.py.")


if __name__ == "__main__":
    main()
