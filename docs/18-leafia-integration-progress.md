# Leafia-inspired integration: experimental candidate 61

Status date: 2026-11-01 (Asia/Taipei).

The reproducible public reference remains V16. Candidate 61 is a private,
vehicle-specific experimental build, not a new public firmware release or a
replacement for the V16 acceptance record. This page summarizes observed progress
without distributing OEM binaries, private runtime state or deployment tools.

## Intended behavior

- Start the shared AP on an admitted wireless CarPlay or Android Auto request;
  restore the saved normal Wi-Fi preference after both projections have ended.
- Preserve an active projection against unsolicited USB or other wireless requests.
  An explicit user switch must finish the old projection before starting the new one.
- A manual stop cancels automatic reconnection eligibility. Unexpected disconnects
  may use bounded, same-device retries; those paths still require separate acceptance.
- Keep three fixed Bluetooth-row button positions. Remember a confirmed projection
  type, retain its dim reconnect button after disconnect, and show progress promptly.
- Disable normal Wi-Fi interaction while projection owns the radio, without prompts.
- Instrument-cluster turn-by-turn is excluded from this implementation scope.

These are requirements, not a statement that every path is fully validated.

## What has been observed

| Area | Evidence and limit |
|---|---|
| Wireless CarPlay | Candidate 60 completed two observed connection/disconnection cycles with AP shutdown and normal Wi-Fi restoration. Candidate 61 has not repeated that complete acceptance sequence. |
| Candidate 61 deployment | Installed files were checked and the new boot/idle AP-off state was verified. The experimental candidate remains installed with private recovery material retained. |
| Android Auto button | An obsolete UI wait from a retired process generation initially blocked clicks. Exact-record recovery restored submission of a new AA request. |
| On-demand AA AP | The new request advanced through Bluetooth handoff, started the AP and obtained Wi-Fi connection information over WPP. |
| GPM crash fix | Restoring a missing project helper method allowed execution beyond the previous transport-construction crash point. Final-DEX direct/static linkage checks were added privately. |
| Mac transport | RFCOMM version exchange, start request and Wi-Fi information response were observed. Wi-Fi association was not verified; no TCP pair, AAP/TLS acceptance, video, audio or touch was established. |
| Mac cleanup | The tool reported Wi-Fi restoration, completed native cleanup and removal of its temporary sockets. The internet default route remained on USB during the observed attempt. This is not successful simultaneous car association. |
| Timeout display | The owner confirmed that the AA spinner stopped and the button became dim after timeout. Native cleanup did not complete. |

## Unresolved failures

1. **Accepted AA work does not fully drain after association failure.** The current
   request remains in DRAIN, still holds the AA reservation and leaves AP enabled.
   The OEM state indicates preparation, not a successful projection. A dark button,
   an ACK, a timeout or a dead RFCOMM socket is not native completion.
2. **Failure-code compatibility.** The Mac bridge reports Wi-Fi unavailable using
   status -11. The inspected OEM enum does not recognize that value; its log reads
   the packet as unsolicited. The exact contribution to cleanup remains to be
   tested. Do not map an unknown failure to success or guess a replacement code.
3. **Association diagnosis.** The original tool report collapsed the failure into
   cancellation. Sanitized error categories and numeric system error codes were
   added to the Mac tool and tested offline; the diagnostic build has not repeated
   the real association attempt yet.
4. **Recovery has multiple consumers.** Native ownership, network restoration and
   the Bluetooth menu's persistent wait must each be reconciled. Restoring only the
   first two left an obsolete UI wait behind in this experiment.
5. Newer-phone Siri distortion, full AA media/touch, USB conflict protection,
   multi-device switching and extended stability retain separate validation gaps.

## Recovery design lessons

The earlier crashed AA process left an owned reservation. A controlled maintenance
transaction pinned the old identity, blocked new admission, confirmed retirement
of the relevant processes, saved preimages and transferred ownership under the
existing file lock directly to maintenance. There was no intermediate FREE lease.
Network shutdown used existing OEM operations and retained the old journals.

A second issue was the distinction between an interrupted AP startup and a completed
AP startup whose ownership had already passed to AA. Treating both as the immediate
predecessor of maintenance rejected a valid later reservation sequence. Recovery
must validate that complete chain instead of decrementing sequence or deleting it.

This historical retirement proof cannot be reused for the current live AA process.
The next implementation must bind cancellation to the exact accepted request and
transport, run native stopping off the main thread, and observe real completion
before releasing ownership or restoring Wi-Fi. No forced flag clearing is a substitute.

## Next acceptance gate

1. Finish exact pre-connection AA cancellation and completion evidence.
2. Verify AP closure, saved Wi-Fi restoration and UI wait retirement together.
3. Diagnose actual Mac association failure and implement verified legacy failure reporting.
4. Retry WPP -> association -> TCP -> AAP/TLS, then verify visible video and interaction.
5. Repeat CarPlay and cross-device/USB regression tests before declaring a release.

The companion [AARE Mac bridge](https://github.com/joemaysong/aare-mac-emulator-bridge)
contains portable tool sources. This repository contains only the sanitized status
of candidate 61; it does not provide its private vehicle deployment package.
