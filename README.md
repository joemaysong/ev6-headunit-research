# EV6 Head Unit Research — V16 final

Owner-selected final: **2026.10.18-v16-final**, October18,2026 (Asia/Taipei).

## Current working combination

- V13 separates device identity, requests, local session generations and negotiated transport. Late callbacks do not complete successor requests.
- V14 controller and in-process Bluetooth exact-work bridge preserve original cancellation and guard receiver/work identity. Positive exact-cancel dispatch remains unexercised; it is not claimed accepted.
- V16 USB identification requests wait for a fresh original Wired **Added or Updated** callback, matching USB/intent generation, device identity and current OEM controller object. Original request API, error/retry handling and active-session behavior are preserved.
- Existing PCM drain, original iAP2/MFi and PhoneProjection ownership remain.

Targeted Added and Updated wired starts both returned first native OK and established an identity-verified wired session, with no INVALID_ARGUMENT, Phone is not responding or OEM USB reset. Unplug finalized the exact sessions. The owner later reported normal free switching. This is scoped evidence, not exhaustive switching or Apple/MFi certification.

## Build, recovery and diagnostics

See [build guide](docs/11-build-and-deployment.md), [validation](docs/12-validation-and-open-issues.md), [recovery](docs/13-rollback-and-porting.md), [diagnostic reconstruction](docs/15-diagnostics-and-retention.md) and [owner checklist](docs/16-owner-switching-tests.md).

Only project-authored English source and text are published. No OEM decompilation, firmware, IMG, APK/ODEX/DEX/SO/JAR, signing key/certificate, credentials, pairing databases, raw captures or private identifiers are distributed. Exact final assets and original backups remain private. Embedded observation/work bridges are runtime dependencies; standalone test recorders are optional and disposable.
