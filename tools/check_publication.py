"""Check the explicit public text inventory and, when present, local Git history."""
import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "PUBLICATION.json"
SUFFIXES = {".md", ".py", ".java", ".smali", ".S", ".c", ".ld", ".sh", ".json", ".xml", ".txt"}
EXACT = {".gitignore", "Makefile"}
PRIVATE_PARTS = {"original", "signing", "private", "backups", "vendor", "local", "build", "evidence", "previous-validated-final1"}
FORBIDDEN = (
    (re.compile(r"/" + r"Users/[^/\s]+/|/" + r"home/[^/\s]+/"), "private host path"),
    (re.compile(r"-----BEGIN (?:[A-Z0-9 ]*PRIVATE KEY|CERTIFICATE)-----"), "key/certificate payload"),
    (re.compile(r"\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,}|AKIA[A-Z0-9]{16})\b"), "credential pattern"),
    (re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]"), "non-English CJK text"),
    (re.compile(r"\b[0-9]{18,24}\b"), "possible literal device serial"),
    (re.compile(r"\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b"), "literal device UUID"),
)
SYNTHETIC_MACS = {"00:11:22:33:44:55", "AA:BB:CC:DD:EE:FF", "11:22:33:44:55:66"}


def inspect(path, payload):
    relative = Path(path)
    if relative.is_absolute() or ".." in relative.parts or any(p in PRIVATE_PARTS for p in relative.parts):
        raise ValueError("Excluded path: " + path)
    if relative.suffix not in SUFFIXES and path not in EXACT:
        raise ValueError("Not an allowed text source: " + path)
    if len(payload) > 1024 * 1024 or b"\0" in payload:
        raise ValueError("Binary/oversized content: " + path)
    if payload.startswith((b"\x7fELF", b"PK\x03\x04", b"ANDROID!", b"dex\n", b"dey\n")):
        raise ValueError("Binary magic: " + path)
    text = payload.decode("utf-8")
    for pattern, label in FORBIDDEN:
        if pattern.search(text):
            raise ValueError(label + ": " + path)
    for value in re.findall(r"\b(?:[0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}\b", text):
        if value.upper() not in SYNTHETIC_MACS:
            raise ValueError("Non-fixture literal MAC: " + path)
    return text


def inventory():
    found = {}
    for path in sorted(ROOT.rglob("*")):
        relative = path.relative_to(ROOT)
        if relative.parts[0] == ".git" or "__pycache__" in relative.parts:
            continue
        if path.is_symlink():
            raise ValueError("Symlink is not a publication source: " + str(relative))
        if path.is_file() and path != MANIFEST:
            inspect(relative.as_posix(), path.read_bytes())
            found[relative.as_posix()] = hashlib.sha256(path.read_bytes()).hexdigest()
    return found


def markdown_links(found):
    for relative in found:
        if not relative.endswith(".md"):
            continue
        path = ROOT / relative
        text = path.read_text()
        for target in re.findall(r"\]\(([^)\s]+)\)", text):
            if re.match(r"[a-z][a-z0-9+.-]*:", target, re.I) or target.startswith("#"):
                continue
            dest = (path.parent / unquote(target.split("#")[0])).resolve()
            if ROOT not in dest.parents or not dest.is_file():
                raise ValueError("Broken/outside local Markdown link: " + relative)


def history():
    if not (ROOT / ".git").exists():
        return 0
    def git(*args, data=None):
        return subprocess.run(["git", "-C", str(ROOT), *args], input=data,
                              stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True).stdout
    if Path(git("rev-parse", "--show-toplevel").decode().strip()).resolve() != ROOT:
        raise ValueError("Refusing to inspect unrelated parent Git history")
    objects = git("rev-list", "--objects", "--all").decode().splitlines()
    seen, total = set(), 0
    for row in objects:
        fields = row.split(" ", 1)
        if len(fields) != 2:
            continue
        sha, path = fields
        if sha in seen or git("cat-file", "-t", sha).strip() != b"blob":
            continue
        inspect(path, git("cat-file", "-p", sha))
        seen.add(sha)
        total += 1
    return total


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write-manifest", action="store_true")
    args = parser.parse_args()
    found = inventory()
    markdown_links(found)
    if args.write_manifest:
        MANIFEST.write_text(json.dumps(dict(format=1, text_only=True, files=found), indent=2) + "\n")
    expected = json.loads(MANIFEST.read_text())
    if expected["files"] != found:
        raise ValueError("Public source inventory/hash differs from reviewed PUBLICATION.json")
    inspect("PUBLICATION.json", MANIFEST.read_bytes())
    count = history()
    print(json.dumps(dict(files=len(found) + 1, source_only=True, english_checked=True,
                          local_markdown_links_valid=True, history_blobs_checked=count)))


if __name__ == "__main__":
    main()
