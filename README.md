# EV6 Head Unit Research

English documentation and source-only tooling for one Kia EV6 head unit: from
Fastboot and private backups to verified root, the native Wi-Fi menu, and a
working wireless CarPlay integration.

**Tested platform:** `wp_daudioplus_cvev_ir`, Telechips TCC803x, Android 4.4.2
(API 19), firmware `CV_E.GEN.S5W_M.V001.001.211105`.
**Research period:** October 13-17, 2026, Asia/Taipei.

This is a firmware-specific research record, not a universal EV6 installer or
an Apple-certified receiver distribution. It retains the head unit's original
iAP2/MFi authentication, receiver, video decoder, and audio routing. No MFi key
or replacement authentication implementation is supplied.

## What was achieved

| Area | Verified result |
|---|---|
| Fastboot and recovery | Product identification, return to Android, stock boot write/readback, and a limited pre-armed Reset-to-Fastboot rescue path |
| Boot analysis and root | Original boot parsed and round-tripped; a ten-byte adbd patch retained UID/GID 0 and full capabilities when booted from the boot partition |
| Private full backup | eMMC user area plus boot0/boot1: 63,718,817,792 bytes, 240 gzip chunks, all 27 ordinary partitions and both GPT copies verified |
| Wi-Fi and native menu | Native Wi-Fi client and a real TCP ADB transport verified; original Setup Wi-Fi icon and click dispatch repaired |
| Wireless CarPlay setup | Genuine iOS confirmation observed during a clean first-pairing test; AP credentials exchanged by the original stack |
| Network stability | Overlapping AP/Ethernet subnets isolated through OEM netd; DHCP completion and stable interactive projection verified |
| Media audio | Wireless media AAC-LC at 48 kHz stereo, decoded to PCM; PCM drain completion fixed and the user confirmed a complete final syllable |
| Device Connections | Three per-device phone/music/CarPlay buttons, active/gray states, real connect/disconnect, and the original-style progress spinner |
| Autonomous connection | On-unit init controller, Bluetooth-triggered connection, software-reboot recovery, post-boot button cycle, and manual-pause behavior verified |
| Two-phone selection | Clean 15 Pro setup, standalone reconnect, X music-only HFP repair, and explicit switches in both directions verified |
| Switch speed | Owned AP retained across explicit switches; observed X-to-15 Pro 9.3 seconds and 15 Pro-to-X 2.8 seconds, versus a 30.2-second reverse-direction baseline |

The current source is **2026.10.17-final2**. The successful new-kernel reboot
case belongs to the earlier final1 controller. Actual ignition/QuickBoot,
a fresh reboot of this exact final2 controller, complete unexpected-loss
recovery, more phone/iOS combinations, Siri/call priority transitions, extended
load tests and a complete whole-device restore remain separate work. See the
[validation matrix](docs/12-validation-and-open-issues.md).

**Known reconnect limitation:** a later existing-pairing 15 Pro test stalled
before the selected phone joined the AP, despite native Bluetooth qualification.
The other remembered phone was associated; the controller did not connect to
the wrong phone. A user Bluetooth reconnect restored projection, and a subsequent
ordinary car-button reconnect passed in 11.836 seconds without a code change.
The intermittent cause is still unresolved. Successful cases must not be read as
a guarantee that every reconnect works. See the
[current investigation](docs/12-validation-and-open-issues.md#intermittent-15-pro-reconnect).

**Known disconnect crash:** a later manual disconnect crashed the receiver in
native clock finalization and left stale connected-state metadata after recovery.
The native disconnect fallback and recovery cleanup require further work. This
snapshot records the research and earlier passing tests; it is not a stable
production release. See the
[disconnect investigation](docs/12-validation-and-open-issues.md#manual-disconnect-crash-and-stale-icon).

## Read in order

1. [Platform, scope, and research chronology](docs/01-platform-and-timeline.md)
2. [Fastboot, recovery, and the limits of the rescue path](docs/02-fastboot-and-rescue.md)
3. [Private boot backup, image analysis, and root](docs/03-boot-backup-and-root.md)
4. [Full eMMC backup, verification, and extraction](docs/04-full-emmc-backup.md)
5. [Wi-Fi client, TCP ADB, native icon, and hidden-feature inventory](docs/05-wifi-and-native-menu.md)
6. [CarPlay architecture and missing components](docs/06-carplay-architecture.md)
7. [Pairing, native confirmation, and automatic connection](docs/07-pairing-and-autoconnect.md)
8. [DHCP, subnet isolation, Bonjour, and IPv6](docs/08-network-diagnosis.md)
9. [Audio capability negotiation and PCM drain](docs/09-audio-and-drain.md)
10. [Device-row buttons and progress indicators](docs/10-device-connections-ui.md)
11. [Local build and deployment boundaries](docs/11-build-and-deployment.md)
12. [Validation, resource measurements, and open issues](docs/12-validation-and-open-issues.md)
13. [Original backups, rollback, and cross-vehicle porting](docs/13-rollback-and-porting.md)
14. [Public technical references](docs/14-references.md)

## Repository layout

```text
boot/         Offline boot and misc-command generators; no flashing commands
backup/       Explicit private capture, disk verification, partition extraction
wifi/         Hash-locked native Setup Wi-Fi ODEX patch generator
carplay/      Final controller, backend/provider contracts, UI/native patches
profiles/     Firmware identity and expected original-file hashes
validation/   Curated numeric results and deployed-artifact hashes
tests/        Synthetic host regressions; no vehicle required
tools/        Publication checks
docs/         Detailed research and operating boundaries
```

Run the source-only checks without connecting a car:

```sh
make check
make test
```

Android/ARM builds additionally require owner-supplied OEM inputs and local
toolchains. Follow the [build guide](docs/11-build-and-deployment.md). There is
no ready-to-flash or one-click installation artifact in this repository.

## What is deliberately absent

No `.img`, partition chunks, kernel/ramdisk payloads, APK/ODEX/DEX/SO/JAR files,
private keys, certificates, signing stores, Wi-Fi credentials, pairing databases,
raw logs, packet captures, screenshots, personal device serials, or the original
workspace history are published. Hashes identify expected inputs and validated
local outputs; they do not replace the owner's private backups.

The publication was assembled from an explicit text-source allowlist and checked
again before upload. See [SECURITY.md](SECURITY.md) and
[third-party boundaries](THIRD_PARTY_NOTICES.md).

## Relationship to official documentation

Public Apple material informs the design, especially
[Developing Wireless CarPlay Systems, WWDC 2017](https://developer.apple.com/videos/play/wwdc2017/717/)
and [Developing CarPlay Systems, Part 1, WWDC 2016](https://developer.apple.com/videos/play/wwdc2016/722/).
The detailed receiver specification is available through
[MFi](https://mfi.apple.com/en/how-it-works.html). The notes distinguish public
guidance, this firmware's observed behavior, and local implementation choices.
Timeouts, retry limits, reverse-engineered offsets, and test observations are
not presented as Apple requirements.

Maintained under [joemaysong](https://github.com/joemaysong). The repository's
public visibility does not license or redistribute OEM firmware.
