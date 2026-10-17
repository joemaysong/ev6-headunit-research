# Platform, scope, and chronology

## The actual target

The research vehicle uses a Telechips TCC803x ARM32 head unit running Android
4.4.2/API 19 and kernel `3.18.24-tcc`. The Android product is
`wp_daudioplus_cvev_ir`; Fastboot identifies `TCC803X_ANDROID_WIDEPE`. The
firmware string is `CV_E.GEN.S5W_M.V001.001.211105`.

The head unit already contains wired CarPlay, iAP2/MFi components, a receiver,
a video decoder, Bluetooth, and a Wi-Fi stack. Wireless CarPlay support is
incomplete at the service-management and integration layers. A missing menu
entry is not proof of absent hardware; a present icon is not proof of a working
service either.

All addresses and native offsets in the code are tied to verified original
file hashes in [the profile](../profiles/cvev-ir-211105.json). A different market,
software version, board, or product requires a new profile and new analysis.

## Chronology and corrected conclusions

| Date | Experiment or finding | Result and boundary |
|---|---|---|
| October 13-14 | Readable system collection and critical partition copies | Private originals preserved; initial `su` was UID 0 with insufficient capabilities |
| October 14 | `adb root`, Fastboot and stock recovery probes | ADB's success message did not retain root; stock recovery ran but provided no interactive rescue shell |
| October 14 | Pre-armed Reset/Fastboot and stock boot writeback | Limited rescue path verified with a valid bootloader, misc state, and USB connection |
| October 14 | Temporary RAM boot | Both patched and completely stock `fastboot boot` stalled at the logo; acceptance was not a boot-success test |
| October 14 | Unmodified cpio repack control | Partition-flashed repack booted; original boot/system/misc restoration verified |
| October 14 | Minimal adbd root candidate | Partition boot retained full root; initial trial restored stock, later the owner retained the same root version |
| October 14 | Independent text bind mount | Bind/unmount succeeded; normal reboot removed the temporary mount and retained root |
| October 14 | Full raw backup | User area and boot regions covered and read back; still a live multi-chunk acquisition |
| October 14 | Wi-Fi and Setup menu | Native client and real TCP ADB succeeded; icon visibility and click handling both repaired |
| October 15 | Wireless setup and iPhone confirmation | Clean pairing produced the genuine iOS prompt and then an interactive CarPlay screen |
| October 15-16 | Early projection exits | Packet directions and AP-only tests pointed toward DHCP; CPU load did not support an overload conclusion |
| October 16 | Subnet isolation | DHCP completed and interactive projection remained stable during the controlled trials |
| October 16 | Audio diagnosis | Existing AAC decoder worked; wireless media capability negotiation was missing/incomplete |
| October 16 | Tail truncation | Latency-table change alone did not fix it; pending PCM and close behavior motivated drain handling |
| October 16-17 | Final audio and UI | 48 kHz media, state-based drain, correct device-row selection, real button cycle, and spinner verified |
| October 17 | Autonomous integration | On-unit init startup, AP ownership, boot nonce retirement, and native/mDNS timing races fixed and tested |
| October 17 | Final publication | Source and English notes separated from private images, keys, identifiers, and raw evidence |

## How to read claims

"Observed" means an event, return value, readback, or user result was recorded.
"Inferred" means multiple observations support an explanation but no direct
measurement proves every internal step. "Verified" always has an explicit
scope: successful stock boot writeback is not a full disk recovery, and a clear
short sound is not a Siri/call test.

Earlier failures remain part of the record. In particular, a 600-second
prototype cleanup once ended a successful session; that was the test harness,
not an Apple session limit. A `ScreenTimeout` field logged as `false` is not a
timeout. Several session-start log lines can describe one session, not several
connections. Historical Bluetooth target-index mistakes were corrected rather
than hidden by suppressing all connection errors.

## Publication boundary

The original workspace contained private backups, extracted vendor applications,
runtime logs, and signing material. This repository is a separate curated
directory, with no inherited workspace Git history. The source retains original
algorithms; local host paths and device selection are parameterized. New host
utilities added for publication have synthetic regression coverage and were not
used to recapture or reflash the vehicle during publication.
