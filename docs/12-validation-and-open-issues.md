# V16 validation and limits

- V14 normal wireless connect/stop/reconnect and both-phone wireless switching passed targeted checks.
- V16 fresh Wired Added and Wired Updated paths each submitted only after the corresponding original controller receipt; first native result OK, actual wired session identity verified, unplug finalized normally.
- V15 missing Updated callback caused Phone is not responding; V16 explicitly corrects that gap. The earlier Added-only acceptance did not cover Updated.
- Owner reports the subsequent free switching round had no observed problem. It does not prove every rapid click, plug ordering or recovery branch.
- Bluetooth exact-cancel positive branch remains unexercised. An offline trial observed normal original work completion, not a needed cancel being incorrectly blocked. Do not broaden guards solely to force a test.
- No full Apple/MFi certification or native error internals are claimed. No new tests are run during final closeout.
