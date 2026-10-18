# V16 ownership and lifecycle architecture

## Component boundaries

| Owner | Responsibility | Boundary |
|---|---|---|
| OEM PhoneProjection/DiPO/native | Actual device association, iAP2/MFi authentication, requests/retries, session start/end, transport | Project never fabricates success or overrides transport |
| Root controller14 | Explicit selected UI intent, owned AP/subnet/reply firewall, outcome/pause bookkeeping | Native/backend/OEM association and original work receipts gate release |
| EV6ATTEMPT13 + SessionLedger | Separate request ID, endpoint, local context/epoch and actual started transport | Local epochs are not OEM/native session IDs; late callbacks cannot finalize successors |
| Bluetooth bridge14 | Root-only receipt of exact OEM machine/work generation | Cancellation remains in original Connecting Handler and machine monitor |
| Wired gate16 | Order the two original USB identification submissions | Same Handler, fresh Added OR Updated, USB/intent epoch, device, current controller object |
| OEM9 backend/provider | Delegate to the existing Iap2ServerApp and framework library | No replacement of OEM MFi/authentication code |

## Submission and session evidence

The observation protocol is **EV6ATTEMPT13**, 37 fields, parsed strictly by
`OemAttemptSnapshot`. An earlier EV6ATTEMPT4 fixture is incompatible. Queue empty,
UI ACK, return OK, native pointer, timeout and active AP are different facts.
Session details may precede initialized; Unknown request type can later yield a
real wired/wireless start. Same-endpoint requests retain independent returns
while sharing a pre-init context. A finalized old session does not settle a
successor request. Historic faults are distinct from current release blockers.

An explicit UI selection can adopt a real original active identity for STOP only
when native control ownership and matching OEM association are proved. Automatic
pairing cannot use that path. Actual finalization plus inactive native/backend/
OEM state remains required before successor begin or AP cleanup.

## Exact Bluetooth work and Wired readiness

Controller14 pins the Bluetooth receiver token, process generation, work epoch
and target during explicit selection. The in-process bridge rechecks them on the
same original Handler under the machine monitor, with no active profiles/call/
projection, before dispatching the original normal-cancel body. Dispatch receipt
is not completion; original non-busy settlement must follow. The positive cancel
branch is still unexercised on the reference vehicle.

Gate16 replays the original listener API only after a fresh original Wired Added
or Updated return for the current controller. USB detach, intent replacement,
controller removal and service replacement retire the pending ticket. Original
error/retry/timeout/active-session behavior remains the owner of the operation.

## Retained earlier compatibility changes

The DiPO baseline also contains a documented wireless initialization compatibility
edit in `initVariables`, replacing an old debug-log region with a wireless-support
check that updates `mIsIap2Disconnected`. `dipo-v16.json` includes this change and
the delegate-entry hook before its observation wrappers. Thus "OEM business bodies
preserved" applies to the renamed wrapper bodies subject to these explicit legacy
integration edits; it is not a claim that every original method is byte-identical.

Stock native control/disconnect, stock Nature Sound, the existing discovery/provider
and the state-based media PCM drain remain. No custom native teardown or direct
HTTP disconnect fallback is part of V16. Optional captures can be removed; embedded
observation/ledger/Bluetooth bridges remain runtime dependencies.
