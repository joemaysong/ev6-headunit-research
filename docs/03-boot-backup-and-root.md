# Private boot backup, offline image analysis, and root

## Preserve private originals

Before root changes, private copies were made of boot, boot_mirror, recovery,
recovery_mirror, DTB, system, eMMC boot0, and boot1. These eight critical images
totaled 2,318,401,536 bytes. Lengths and source MD5 were checked and local SHA-256
recorded. The full-disk capture was a later, separate stage.

Use binary-safe transfer for images. This API-19 head unit did not support modern
`adb exec-out`; the experiment used a private staging file and `adb pull`, then
verified its size and digest before deleting staging. Do not decode an image as
shell text. `boot.img` and all captures remain outside Git.

The original boot is 15,728,640 bytes, with SHA-256:

```text
dc4ec8bd8cef335515fda4a11b723b1155e169f4d90ddc79a499cb00c5bacc0b
```

## Image format and control experiments

The legacy `ANDROID!` header uses 2048-byte pages. Kernel size is 6,733,872 bytes;
gzip ramdisk size is 2,016,956 bytes; second-stage and header DT sizes are zero.
The aligned logical image ends at 8,755,200 bytes and the remainder is zero
padding. The original image ID was recalculated and a reconstruction using the
original compressed bytes matched the entire partition capture byte for byte.

The original kernel is preserved, including any appended DTB. A zero header DT
size does not justify replacing or dropping the kernel's own board data.

Two controls were essential:

- Completely stock `fastboot boot` also stalled at the logo, so the patched
  candidate's RAM-boot failure could not identify a bad root patch.
- Recompressing the entirely unchanged cpio archive, flashing the repack into
  the boot partition, and reading it back worked. The tested packing method was
  therefore compatible with partition boot.

The [offline generator](../boot/boot_image.py) supports `inspect`, `repack`, and
the hash-locked `root` mode. It preserves cpio headers, member order, padding,
kernel, init files, and USB configuration. It writes a new local file only.

```sh
python3 boot/boot_image.py inspect /private-backup/boot.img
python3 boot/boot_image.py repack /private-backup/boot.img --output /private-work/repacked.img
python3 boot/boot_image.py root /private-backup/boot.img --output /private-work/root-candidate.img
```

Compression-tool versions can affect compressed output hashes. A newly generated
file is not automatically the already-booted artifact; verify the uncompressed
scope, output hash, and rescue prerequisites before any separate device test.

## Why UID 0 was insufficient

Initially the OEM adbd ran as UID 2000 with capability bounding set `0xc0`.
`su` produced UID 0 but inherited that bounding set: only CAP_SETGID and
CAP_SETUID remained. CAP_SYS_ADMIN was absent, explaining denied bind/remount
operations without inventing a virtualization explanation.

`adb root` said it was restarting as root, yet the new daemon still dropped
privilege. Static analysis of this exact original adbd showed an unconditional
capability-drop loop and setgid/setuid to 2000 on its startup path. Merely editing
`ro.secure` would not remove those instructions. See the historical AOSP
[adbd implementation](https://android.googlesource.com/platform/system/core/+/android-4.4_r1/adb/adb.c)
for the relevant class of privilege handling; the actual patch was based on this
head unit's binary, not an assumption about every AOSP build.

## The minimal patch

| Thumb address | Change | Retained behavior |
|---|---|---|
| `0x91f6` | Replace the CAPBSET_DROP call with success/NOP | Capability iteration and surrounding control flow |
| `0x9240` | setgid argument 2000 to 0 | Original call and failure path |
| `0x924c` | setuid argument 2000 to 0 | Original call and failure path |

Three four-byte regions contain ten actually changed bytes. The original adbd
hash is checked before patching. No replacement adbd, init script, key, kernel,
or default.prop change is introduced. This is a specific privileged daemon
modification; it is not Magisk or a generic root package.

The same candidate that failed RAM boot worked from the boot partition:
direct shell UID/GID 0 and CapPrm/CapEff/CapBnd `0x3fffffffff`, including
CAP_SYS_ADMIN. Candidate boot and live adbd hashes matched. The first trial
restored stock and verified UID 2000/baseline capabilities. The owner later
retained the same root version for further work.

## Mount validation and current scope

Two independent text files were used to prove bind, unmount, original-content
restoration, a second bind, and disappearance of that bind after a full normal
reboot through Fastboot. Root remained; the test files and rescue flag were
cleaned up. This preceded app/ODEX changes and did not establish that every
application or namespace would see a future bind mount.

Later CarPlay work found separate ADB and Zygote/app mount namespaces. Hashing
an overlay only from ADB was insufficient. Final deployment removed the reviewed
temporary mounts and verified persistent files from both namespaces.
