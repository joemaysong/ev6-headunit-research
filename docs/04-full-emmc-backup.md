# Full eMMC acquisition, verification, and extraction

## What was captured

| Region | Raw bytes |
|---|---:|
| eMMC user area, `mmcblk0` | 63,652,757,504 |
| Additional boot region, `mmcblk0boot0` | 33,030,144 |
| Additional boot region, `mmcblk0boot1` | 33,030,144 |
| Total | 63,718,817,792 |

The private acquisition covered both GPT copies and all 27 ordinary partitions.
The user area was stored in 256 MiB raw chunks before compression, with a shorter
final chunk; two additional boot-region chunks brought the total to 240 gzip
files. Compressed storage was about 3.45 GB for this particular acquisition.
That number is not a promise that another head unit's data will compress as well.

Navigation-related regions accounted for about 47.2 GB, update staging about
8.1 GB, system about 2.1 GB, and other areas about 6.2 GB. Unused space still
exists in the raw address space and can explain a much larger raw size than the
compressed archive.

## Acquisition procedure

The device read each segment into a uniquely named gzip file on `/dev` tmpfs,
avoiding a persistent copy on a nearly full storage partition. The host pulled
that binary file to an external disk. For each segment:

1. Confirm the external destination is mounted and has adequate free space.
2. Read the declared block range; never issue a block-device output write.
3. Hash the compressed source file on the head unit.
4. Transfer using binary-safe `adb pull` and compare compressed MD5.
5. Reopen/decompress locally, validate gzip integrity, raw length, and raw SHA-256.
6. Record offset, length, compressed/raw digests, and relative file path.
7. Remove only that run's temporary file after verification.

The captured byte stream, not a later re-read of a changing filesystem, is what
the archive hashes describe. Temporary acquisition writes and regular Android
writes still occur; the backup procedure does not modify source block content.

The public [capture utility](../backup/capture.py) is a parameterized version of
this approach. It requires an explicit action flag, a selected USB serial,
verified geometry, root ADB, and a new private directory outside the repository:

```sh
python3 backup/capture.py --adb adb --serial "$EV6_ADB_SERIAL" \
  --output /private-external-disk/ev6-capture --capture
```

It is not a resume tool. Preserve partial output and investigate a failed
acquisition rather than mixing two live captures into one claimed snapshot.
The publication utility was checked with synthetic archives; it was not used
to perform another live full capture for this GitHub publication.

## Independent disk readback

After acquisition, all external files were reopened. Verification checked:

- Compressed SHA-256 and gzip integrity for each chunk.
- Raw chunk SHA-256, exact length, contiguous offsets, and full device digest.
- Primary and secondary GPT header CRCs and partition-entry CRCs.
- Agreement of partition names, offsets, and sizes in both GPT copies.
- Complete coverage and a computed hash for every ordinary partition.
- Both extra boot regions from the external copies.

The public verifier implements these checks without contacting a car:

```sh
python3 backup/archive.py verify /private-external-disk/ev6-capture
```

The resulting `verification.json` is private archive metadata. Do not copy the
real archive or its raw metadata into GitHub.

## Extracting a partition

```sh
python3 backup/archive.py extract /private-external-disk/ev6-capture \
  --partition system --output /private-work/system-extracted.img
```

Extraction opens a new ordinary output file, refuses `/dev` and existing
destinations, checks chunk digests and the verified partition hash, and leaves a
failed partial for inspection. It never flashes a partition. The final link is
created without replacing an existing file.

## Consistency and recovery limits

This was a running, multi-chunk acquisition. `data`, logs, databases, and OEM
state could change between chunks. Complete address coverage and valid hashes
do not create a single-instant, transaction-consistent snapshot.

RPMB is authenticated/counter storage and was not captured as a normal raw
region. MICOM, the instrument cluster, other ECUs, and volatile RAM were outside
scope. A successful stock boot writeback does not validate restoring all 63.72
GB. Whole-disk writeback has not been tested.

An external filesystem may not retain Android Unix ownership and modes for
ordinary copied files. Raw images and the original archive metadata preserve
different semantics from a file explorer copy. Keep the original verified
archive, manifests, and acquisition context privately.

The project also verified migration of 5,789 older backup files and archival of
54,780 workspace support files before removing duplicated local copies. Those
archives are not GitHub release assets. The external disk was not mounted during
final CarPlay validation; no claim is made that it was reread again that night.
