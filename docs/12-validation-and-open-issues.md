# Final validation scope

Current owner-selected final: **2026.10.18-manual-handoff-final**.

## Current observed evidence

The owner reported successful X wired -> 15 Pro wireless selection with X
remaining plugged in. Original stop/finalization events preceded preparation
of the next connection. Native session, backend publication and OEM selected
association agreed; observer uncertainty remained false. Latest screen/App
operability was not separately confirmed.

The deployed observer passed isolated compilation and 1,732 original ABI
reference checks with zero differences/unavailable entries, plus original
APK/adjacent-cache pairing. These are build compatibility checks, not complete
functional or switching acceptance. The cleanup checked 22 runtime files
unchanged and preserved the active session.

## Known limitation / open owner checks

- An inactive, unpaired USB-only phone may disappear from the original Device
  Connections list. The list expansion was stopped without deployment.
- Reverse selection of that missing USB device is incomplete.
- Same-phone wireless-first/USB-first coexistence is not accepted yet.
- Repeated switching, ignition/endurance and latest screen/App interaction
  remain owner checks.

See [owner checklist](16-owner-switching-tests.md). The owner will perform
further switching tests; no additional live experiment is part of closeout.

## Prior evidence

The prior OEM11 AP timing repair passed one bounded failure/recovery and
manual-stop case. That historical result is inherited implementation context,
not a rerun of every scenario on this combined final. Older validation files
retain their original dates/scopes and must not be read as new acceptance.

The public Apple device-selector guidance covers explicit selection across
plugged-in and wirelessly paired devices. It does not require a per-phone
manual transport switch. The current missing USB-only row remains a gap;
owner final designation does not waive it or claim full MFi certification.
Reference: https://developer.apple.com/videos/play/wwdc2017/717/
