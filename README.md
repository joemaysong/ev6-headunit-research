# EV6 Head Unit Research — final

The owner-selected final is **2026.10.17-oem11-apfix**, frozen on October17,2026
(Asia/Taipei). This exact firmware integration retains the original iAP2/MFi,
CarPlay control/disconnect, video and audio-resource paths. Project components
supply wireless management, the device-row UI and required network integration.

## Final changes

- Original PhoneProjection connection/control ownership; no direct HTTP sender.
- Scoped IPv4 tracked TCP reply allowance and owned AP subnet/firewall handling.
- OEM9 backend invocation acknowledgment and callback ownership retained.
- PCM state-based drain for short media endings; stock Nature Sound retained.
- OEM11 waits for original returned work/queued retries/timeouts before AP
  teardown; genuine native/backend/original association are independently checked.
- Original retained reconnect metadata is not mistaken for pending current work.

## Observed final acceptance

One bounded failure/recovery test dropped12 IPv4 SYNs. Initial and original retry
returned ERROR. AP remained enabled while original work was pending, including
after the project deadline. Original work settled before AP/client cleanup and
before later preparation. A genuine selected session recovered. The user then
manually stopped: session/association/queues cleared, AP off and Wi-Fi enabled.
Screen/App operability was not separately confirmed in this final window.

The user selected this as final and deferred speculative feature expansion.
This is not full MFi certification or acceptance of every ignition/USB/endurance
scenario. No further investigation is implied by an old exploratory checklist.

## Repository

- [Final architecture](docs/06-carplay-architecture.md)
- [Build and private inputs](docs/11-build-and-deployment.md)
- [Validation scope](docs/12-validation-and-open-issues.md)
- [Original backups and recovery](docs/13-rollback-and-porting.md)
- [Recreate diagnostics](docs/15-diagnostics-and-retention.md)
- [Public technical references](docs/14-references.md)

Only English text/project source is published. No firmware, APK/ODEX/DEX/SO/JAR,
signing keys, Wi-Fi credentials, pairing databases, raw diagnostic data, private
identifiers or original proprietary decompilation is distributed. The original
full backup and exact final runtime remain private. See [SECURITY.md](SECURITY.md).
