# EV6 Head Unit Research — final

The owner-selected final is **2026.10.18-manual-handoff-final**, frozen on October 18, 2026
(Asia/Taipei). It preserves the currently installed working combination.
The owner will perform further switching tests. Final designation does not
mark unresolved scenarios passed.

## Current changes

- Original iAP2/MFi and PhoneProjection control/disconnect ownership retained.
- Prior AP cleanup timing, scoped network integration and PCM drain retained.
- Original wired preflight continuations are observed separately from delegated
  player work; only same-target Wired=0 rejected preflights qualify.
- Explicit native-UI selection can stop an observed original active session
  through disconnectPP, wait for genuine finalization, then connect the next
  device. Automatic connections still preserve an active session.
- Guarded manual iAP2 recovery preserves healthy/in-progress original links.

## Observed behavior and limits

The owner reported successful X wired -> 15 Pro wireless selection while X
remained plugged in. Controller events show original stop/finalization before
new setup; selected native, backend and OEM association agreed and observer
uncertainty stayed false. Latest screen/App operability was not separately
confirmed.

**Known limitation:** the original Device Connections list can omit an inactive
USB-only phone that is not Bluetooth paired. USB list expansion was stopped
without deployment. Reverse selection and same-phone USB/wireless behavior
remain open owner tests. No full Apple/MFi certification is claimed.

## Documentation

- [Architecture](docs/06-carplay-architecture.md)
- [Build and private inputs](docs/11-build-and-deployment.md)
- [Validation scope](docs/12-validation-and-open-issues.md)
- [Original backups and recovery](docs/13-rollback-and-porting.md)
- [Recreate diagnostics](docs/15-diagnostics-and-retention.md)
- [Owner switching checklist](docs/16-owner-switching-tests.md)
- [Public technical references](docs/14-references.md)

Only English project text/source is published. No IMG, firmware, APK/ODEX/DEX/SO/
JAR, signing key/certificate, Wi-Fi credential, pairing database, raw diagnostic
data, private device identifier or proprietary OEM decompilation is distributed.
Exact final assets and original recovery inputs stay private. See [SECURITY.md](SECURITY.md).
