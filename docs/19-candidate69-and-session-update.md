# EV6 session update: candidate 69 and breathing lights

Status date: 2026-11-02 (Asia/Taipei). The public reconstruction reference remains
V16. This update supersedes the current-status interpretation of the historical
[candidate 61 report](18-leafia-integration-progress.md); that report is retained
as dated evidence. Candidate 67 remains the installed vehicle experiment at the
recorded checkpoint. Candidate 69 is packaged privately and **not deployed**.

## Projection admission and reconnect

The candidate 68/69 coordinator admits automatic starts and same-device reconnects
only when idle. An unsolicited automatic request cannot take ownership from a live
projection. Explicit switching still waits for the predecessor to finish. A brief
successful connection now retains reconnect eligibility even if the next poll
already observes disconnection. Delayed disconnect events from a retired session
cannot create a new reconnect generation. Retries remain bounded to three attempts
within 120 seconds; manual disconnect, explicit switching or maintenance retires
that eligibility. Host checks do not establish real multi-device behavior.

## Android Auto fixes packaged in candidate 69

- Video success captures the immutable receiver/request identity and cancels only
  that request's 45-second timeout. A stale receiver cannot cancel its successor.
- ByeBye and stop callbacks retain their original receiver/transport identity.
  Formal cancellation supports an exact current receiver that moved from handshake
  into connected or disconnecting state. The pending-only endpoint stays limited.
- Native destroy, normal handler return, resource absence and drained lease
  evidence remain necessary before release. UI state 1 alone is not completion.
- The companion AARE experiment now builds an optional identity helper: one-use,
  nonce/expiry-bound actual Mac adapter identity, sent after AA channels are ready.
  It checks the installed APK hash before opening radios. That identity APK was
  **not installed** at this checkpoint because another emulator session was active.

Private verification reported receiver-stop 105, lease 155, Java identity 17 and
Python supervisor 28 checks; inherited suites include coordinator 92, reconnect
75, runtime 165, ByeBye 28 and radio 275 checks. These are separate suite counts,
not one deduplicated acceptance total. DEX round-trip/source checks and isolated
ODEX compilation passed; vehicle baseline comparison matched and temporary
compilation files were removed. This publication did not rerun those private
suites. No OEM image, DEX/ODEX/APK, signing identity or private cohort is published.

## What remains unverified

An earlier vehicle experiment reached AA video setup. Emulator encoder callbacks
continued after the receiver later reported status 1; this proves encoder output,
not successful vehicle decoding, fresh rendering or delivered touch input. The
45-second timeout and missing AA device identity are concrete findings, but neither
alone establishes the cause of the visible freeze. A later Wi-Fi break aligned
with the tool's 300-second stop and is not proof of the earlier freeze's cause.

Before accepting candidate 69, separately verify CarPlay connect/manual-stop;
AA connection beyond 45 seconds; identity response and receiver device identity;
frame freshness and input dispatch; manual, EOF and TLS-failure cleanup; AP-off and
normal Wi-Fi restoration; USB conflict and cross-device regression. Preserve the
existing maintenance transaction and originals. No deployment is authorized by
reading this page.

## Breathing lights 1.1.2

The vehicle build was deployed on this date after retaining its previous APK,
settings and original-light restore data and matching old/new signing certificates.
It preserves the 8-second clockwise default, configurable 2-20 second period,
clockwise/counterclockwise/random modes, autoboot option and stop-on-OEM-light-menu
behavior. No microphone or audio analysis is used.

The previous boot received BOOT_COMPLETED, but failed at approximately 28 seconds
with Restore readback mismatch while recovering a prior baseline. RGB/brightness
matched in the retained report; the old diagnostic record did not expose every
field at each attempt, so a precise mismatching field was not established.

Version 1.1.2 requires two consecutive fresh matching samples, including fade,
allows asynchronous readback to settle, logs expected and actual fields, and retries
startup within the existing two-minute boot window. It never treats a mismatch as
success or discards the original restore obligation. The check compares the
**pre-effect backup**, not the last color of the previous animation. OEM menu
manual handoff retains priority. Existing per-role logs rotate at about 1 MiB,
keeping one previous file for each role.

The same BOOT service action successfully restored the retained baseline and ran
at least seven sequential color steps without a new failure. Installed APK bytes
matched the built artifact. A physical reboot after the fix has not been observed;
manual-menu detection code was preserved but not newly exercised in this run.

[Public breathing-light source and build route](../ambient/music-vibe/README.md)
are included. Its diagnostic strings are English for publication, so it is not a
byte-identical copy of the Traditional Chinese vehicle APK. The public build was
compiled separately; its new artifact is not an additional vehicle deployment.
