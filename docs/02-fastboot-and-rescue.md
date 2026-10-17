# Fastboot, stock recovery, and a limited rescue path

## Establish identity before changing anything

The original experiments first recorded Android boot completion, boot/system
hashes, partition geometry, and a complete private copy of the 4 MiB `misc`
partition. A USB serial must be selected explicitly; it is not embedded in this
public source.

Read-only identification examples:

```sh
adb -s "$EV6_ADB_SERIAL" shell id
adb -s "$EV6_ADB_SERIAL" shell getprop sys.boot_completed
adb -s "$EV6_ADB_SERIAL" shell ls -l /dev/block/platform/bdm/by-name
```

The tested transition was `adb reboot bootloader`. Fastboot returned product
`TCC803X_ANDROID_WIDEPE`, version `0.5`, and maximum download size `0x20000000`.
The reported system partition was `0x80000000` bytes/ext4. Some boot-size,
secure, and unlocked queries returned blank values. Blank is unknown, not proof
of an unlocked bootloader or disabled verification.

No OEM unlock, erase, format, bootloader write, or unrelated partition write was
needed for this research.

## The pre-armed rescue mechanism

The controlled boot experiments preserved the original `misc` bytes, then placed
the bootloader command in its 32-byte command field. The rest of the 4 MiB
partition had to remain identical. This allowed the owner's physical Reset to
return to Fastboot even when Android was not running normally.

The offline [misc generator](../boot/misc_command.py) demonstrates the exact
field scope. It creates a private local candidate; it does not write the vehicle.
The historical device procedure transferred only the reviewed command field
and read back the entire partition. The flag was kept during boot trials and
retired after successful return to Android. Never assume a flag cleared by an
earlier experiment is still armed.

The observed rescue sequence was:

1. Verify stock boot and private `misc` baseline; preserve an independently
   accessible stock image.
2. Pre-arm and read back the bootloader command, checking unchanged bytes.
3. Enter Fastboot and confirm the expected product.
4. Verify Fastboot re-entry with `fastboot reboot` and the owner's physical Reset.
5. Write the owner's own matching stock boot and return to Android.
6. Confirm the original screen, ADB, boot completion, and boot readback hash.
7. Restore only the original command field, preserving other legitimate metadata;
   compare the complete resulting `misc` capture with the expected baseline.

The one partition-flash command used in those controlled trials was
`fastboot flash boot <owner-supplied-matching-image>`. It is documented as a
historical operation, not run by the offline generators.

## Why transport success was insufficient

`fastboot continue` repeatedly reported a USB read error during the transition.
The return was accepted as successful only after independent Android evidence:
the owner saw the normal screen, USB ADB returned, `sys.boot_completed=1`, and
the expected boot bytes were read back. Conversely, `fastboot boot` returned
Sending/Booting OKAY for both a patched and a completely stock image, but both
remained at the logo. That path was not treated as safe just because it accepted
the command.

The old bootloader also emitted an AVB-footer/zero-partition-size warning. The
warning did not establish the verification policy. Actual write/readback and
boot results determined the conclusion for the tested image.

## Stock recovery

`adb reboot recovery` ran the stock recovery. Its log showed a recovery command,
failed USB mounting, a null update package, and an automatic restart after about
ten seconds. No interactive recovery shell or arbitrary restore path was
obtained. This was a recovery execution test, not validation of full rescue.

## What remains unverified

The rescue path depends on a functioning bootloader, readable `misc`, a previously
armed command, compatible stock input, power, and USB. No unarmed hardware-button
entry, GPIO/FWDN loader for this exact board, corrupt-bootloader rescue, full
vehicle power removal, whole-disk restore, or recovery of other ECUs was tested.

An empty `boot_mirror` was observed; its name did not make it a usable backup.
The recovery mirror matched the primary recovery in the private acquisition.
