# V17.0.1 maintenance release - candidate102

Promoted by the owner on 2026-11-18. Candidate102 is already installed; this
publication does not redeploy the vehicle or remove retained recovery material.
The original V17 candidate92 history remains in [the original release notes](20-v17-release.md).

## Changes since the original V17 baseline

- Real-phone wired AA admission, native stop confirmation, ownership release and
  stale USB bookkeeping were revised in candidates93-95.
- AA fullscreen and a 60fps capability request were introduced in candidate96
  for wired and wireless AA. Capability advertisement is not a measured sustained
  rendering rate. CP display configuration is outside this change.
- Candidates97-101 revised stale AA waiting state, failed-start cleanup, native
  stop evidence and same-Bluetooth-session restart. A dark icon alone is not
  evidence that native cleanup completed.
- Candidate102 fixes an incorrect instance-field read of the static WiFi
  transport field in the AA idle restart gate. This final change is one
  instruction in one GPM method; all other methods and 1480 other classes were
  preserved by independent DEX/roundtrip checks. Twenty other deployment targets
  are unchanged from candidate101.
- Exact request ownership, explicit device switching, active-session exclusion,
  manual-stop suppression of automatic reconnect, bounded same-device recovery,
  on-demand AP and restoration of the prior WiFi preference remain required.

## Observed candidate102 acceptance

| Scenario | Result |
|---|---|
| Real vivo wireless AA manual connect | CONNECTED in about 9.6 seconds |
| AA single-click manual stop | Native completion, free ownership, DONE, AP off |
| AA reconnect without toggling Bluetooth, reboot or record deletion | New WiFi transport; CONNECTED in about 9.8 seconds |
| Second AA single-click stop | DONE, ownership free, AP off, prior WiFi preference restored |
| iPhone first pairing and automatic wireless CP | CONNECTED in about 21.2 seconds after the owner's hotspot-related intervention |
| CP manual stop and subsequent button reconnect | Full cleanup, then CONNECTED in about 20.1 seconds |
| Second CP manual stop | Native/backend inactive, ownership free, DONE, AP off, prior WiFi preference restored |

The first CP pairing attempt did not join the vehicle AP. The owner reported
another phone using the iPhone's personal hotspot; a later pairing succeeded
after intervention. This is consistent with the observed join failure but is
not a controlled single-variable proof of cause. Device identifiers, raw logs,
network credentials and pairing data are intentionally not published.

## Validation limits and open work

Real wired AA projection and repeated insertion were observed in the earlier
candidate95 series; fullscreen, music and touch were observed in candidate96.
Those are historical results, not new candidate102 wired acceptance.
Candidate102 still needs wired CP and cross-device exclusion runtime regression,
and the full wired/wireless switching matrix. Audio-quality complaints and AA
voice availability are separate unresolved items; this release does not claim
those are fixed. Actual sustained 60fps and codec selection need measurement.
Earlier bounded recovery tests are not automatically counted as candidate102
regressions. Promotion is owner-selected with these limits, not full certification.

## Publication and reconstruction

[The current profile](../profiles/v17-release.json) identifies the installed
private package hash. [The integration snapshot](../v17/README.md) includes the
authored state-management changes and a hash-locked candidate101-to102 overlay.
The snapshot is not a complete stock-to-V17 installer. Firmware-specific OEM
inputs, native ODEX dependencies, hook integration and retained signing identity
are still required. Never substitute V16 build instructions for V17 deployment.

The source-only repository contains no OEM binaries, full decompiled OEM trees,
signing keys, credentials, raw device captures or recovery payloads. Existing
Launchpad and separate AARE publication remain unchanged in this revision.

The [sanitized validation summary](../validation/v17-maintenance102.json) records
the publication-time rebuild: its DEX hash matches the installed candidate102
input DEX, with the same one-method scope proof.
