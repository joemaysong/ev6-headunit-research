# V17 release — candidate92

Promoted by the owner on 2026-11-07; no additional vehicle installation is required.

## Observed acceptance

- Wireless CP and Mac AARE wireless AA: first pairing, manual stop and subsequent connection observed across the V17 candidate series.
- Bounded AA and CP recovery after unexpected Wi-Fi loss observed in prior candidates; later changes require the normal regression matrix.
- Candidate92 idle wired iPhone X attach: native Wired session started; unplug finalized normally.
- Candidate92 USB attached during wireless CP: active wireless session remained protected; explicit manual stop with the cable continuously attached led to a native Wired session in about 24.5 seconds.
- Candidate92 USB attached during manual-stop cleanup: no physical replug; native Wired session established about 13.4 seconds after attach.
- Both USB handovers included system-reported USB detach/attach events; their cause and latency remain optimization work.
- Final unplug: native finalization around one second; AP off, coordinator DONE, prior Wi-Fi preference restored.

## Remaining validation

Real wired AA is deferred for lack of a compatible device. Wireless-device switching while a USB device remains attached has source/fixture coverage but has not been physically accepted. Mac simulation does not certify behavior of every Android phone. Bluetooth-off CP failure can retain progress until the bounded demand timeout; owner deferred changes.

Launchpad source includes USB/TCP ADB controls. Host USB role must be restored before wired projection tests. AARE standby sources are published separately in joemaysong/aare-mac-emulator-bridge.

## Retention

The owner requested removal of Mac historical backup/rollback payloads after promotion. GitHub contains public source, not recovery payloads. Restoring an earlier installed binary will require the external pristine archive or a separately rebuilt compatible package. Signing material is excluded and retained privately.
