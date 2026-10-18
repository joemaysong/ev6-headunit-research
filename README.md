# EV6 Head Unit Research - V16 final

Owner-selected reference: **2026.10.18-v16-final**, October 18, 2026 (Asia/Taipei).

V16 combines the Wired Added/Updated gate16 with controller/Bluetooth revision14,
EV6ATTEMPT13 session reconciliation, and the retained OEM9 backend/audio/UI.
Original iAP2/MFi and PhoneProjection keep authentication and session ownership.
Targeted Added and Updated starts each returned first native OK, established an
identity-verified wired session and finalized after unplug. The owner later
reported free switching normal. Exact Bluetooth cancellation remains unexercised;
this is scoped acceptance, with no full Apple/MFi certification claim.

## Reproduce with another Codex workspace

Give Codex this repository and the owner's private originals, and ask it to read
[AGENTS.md](AGENTS.md) and follow [the reproduction workflow](docs/17-reproduction-and-porting.md).
The [component map](carplay/README.md), [build commands](docs/11-build-and-deployment.md),
[rollback plan](docs/13-rollback-and-porting.md), [current inventory](profiles/v16-release.json)
and [review evidence](validation/v16-reproduction-review.json) specify the inputs,
transformations, outputs, checks and target-specific boundaries.

Matching firmware can reuse the hash-locked reconstruction recipes. Another
vehicle/firmware needs its own profile, native/Binder/Handler contracts and
controlled acceptance. A working reference implementation does not establish
compatibility with arbitrary vehicles. The workflow identifies those differences
before system writes.

## Publication and private retention

Publish project-authored English source/text only. Keep OEM firmware and smali,
IMG/APK/ODEX/DEX/SO/JAR, exact restoration assets, private signing keys/certificates,
credentials, pairing data, raw captures and identifiers privately. Preserve
existing signing identities for updates; builders never silently create keys.
See [retention](docs/13-rollback-and-porting.md) and [publication checks](tools/check_publication.py).

[Architecture](docs/06-carplay-architecture.md), [acceptance limits](docs/12-validation-and-open-issues.md),
[optional diagnostics](docs/15-diagnostics-and-retention.md) and
[owner switching matrix](docs/16-owner-switching-tests.md) describe current behavior.
[Historical evidence](validation/README.md) is version-scoped separately.
