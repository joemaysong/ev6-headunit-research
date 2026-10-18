# Private retention and complete V16 rollback scope

## Preserve the recovery inputs

Keep the first pristine original backup, full eMMC/boot backup and manifest,
external-storage pointer, exact private V16 assets, full accepted private smali
trees, and the existing backend/button signing keys **and certificates**. Keep
private signer backups with restricted access and separate recovery storage.
Do not replace those originals with patched output, recreate an update signer,
copy keys into a public staging folder, or delete them as temporary build files.
This source review preserves the owner's existing private signing material.

`profiles/v16-release.json` records the public hashes of the private originals
and frozen runtime assets. It contains no keys, credentials or OEM payloads.
`PUBLICATION.json` serves a different purpose: public text inventory only.

## Recovery choices

Exact V16 restoration uses the owner's private frozen runtime and manifest.
A source rebuild remains a candidate until scope, loading and operation are
verified. A different compiler may produce a different DEX/ODEX. The frozen
controller includes two unreferenced legacy BluetoothWork classes absent from
the retained current Java sources; the current referenced classes were compared
separately during review. Do not claim a rebuilt JAR is bit-identical to it.

The old public rollback script predated the Bluetooth/DiPO changes and was
incomplete. Its V16 replacement applies to the **exact reference V16 only**,
requires original UI stop and fresh idle receipts, verifies original inputs
before remounting, preserves the replaced system files, restores Bluetooth,
DiPO, BtSettings, audio/control and feature configuration plus existing mirrors,
and removes only the exact authored feature/startup files. It disables the
project packages while retaining their private data. It never uses force-stop,
PID killing, pairing deletion or an original/native disconnect fallback.

```sh
# On the matching vehicle, only after the reviewed idle/recovery checks:
sh /path/to/reviewed/restore-original.sh --restore-reference-v16
```

This source helper has received static review and shell syntax checks, **not a
new live rollback trial**. An unknown observer, broken bridge, different current
hash or historical overlay setup stops it before system replacement. For a port
or failed-load recovery, use the private original manifest and separately
reviewed recovery plan; do not weaken guards to get this helper to run.

## Required original state after system restoration

The owner performs a full reboot before using projection/phone/audio. Restoring
mapped files does not unload their old code. Verify pristine bytes in both
/system/app/HKMC_DiPO.odex and any retained asset mirror, Bluetooth.odex,
BtSettings.odex, native audio/control files, OEM APK pairing, ownership/modes,
process generations and /system read-only. Stock Nature Sound was unchanged.

Compare the three phoneprojection_setting_enable_wireless settings with the
vehicle's private pre-project baseline; restore each original value or absence.
Do not assume all cars originally lacked those settings. Restore AP configuration
and client preferences only from this vehicle's own baseline. Keep private state
until verified restored. Do not copy another owner's SSID/PSK or pairing data.
The separately installed Wi-Fi menu and root boot changes have separate original
backups and rollback procedures; CarPlay rollback does not restore the whole car.

After correct reboot and verification, disabled project packages may be retained
with their data for recovery or removed under an explicit cleanup plan. Retain
local signing keys and first backups either way. A complete stock restoration
claim needs settings, packages, startup, feature/cache and real UI verification,
not just a successful file-copy command.

## Another vehicle

Follow [the porting workflow](17-reproduction-and-porting.md). Resolve its own
bootloader/rescue, ABI/native offsets, Binder/Handler contracts, feature/UI
resources and AP behavior. The reference profile is evidence for one firmware;
it is not a universal vehicle installer.
