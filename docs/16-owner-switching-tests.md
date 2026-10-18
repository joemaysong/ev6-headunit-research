# Owner switching acceptance matrix for V16

Use original vehicle UI manually. Keep USB ADB mode off on the port used for
phone USB CarPlay. Change one condition at a time. Record exact revision,
actual started transport, old finalization/new session identity and owner App
operability. A UI ACK or USB cable presence alone is insufficient.

| Case | Reference evidence | Still needed for a new vehicle |
|---|---|---|
| Wireless connect, stop and direct reconnect | V14 targeted normal case with owner operability confirmed | Repeat after the port's reboot |
| Wireless device A -> B -> A | V14 both directions; original finalization and new verified identity | Repeat with that vehicle's paired devices |
| Same phone wireless, then USB insertion/removal | V14 session remained actual Wireless; three stages owner-operable | Do not relabel this as true wired start |
| True wired start from fresh controller Added | V16 first native OK, verified Wired, normal unplug finalization | Require same ordering and actual Wired evidence |
| True wired start after prior wireless controller, fresh Updated | V16 targeted first native OK, verified Wired, normal unplug finalization | Repeat Updated separately; Added-only evidence is insufficient |
| True wired unplug -> explicit wireless | Earlier targeted functional passes, including same-phone sequence | Check new port without assuming the earlier initial INVALID_ARGUMENT is accepted |
| Rapid repeated taps / arbitrary simultaneous crossovers | Owner later reported free switching normal; no exhaustive matrix | Mark each tested permutation explicitly |
| Exact Bluetooth cancel positive path | Unexercised; normal work completion is not cancel acceptance | Do not force busy state or loosen work identity guards |
| Inactive USB-only row discovery/selection | Known OEM list limitation; catalog expansion was retired | Remains a separate compatibility question |
| Every Siri/call/priority/error/power interruption | Not comprehensively classified | Separate bounded acceptance cases |

Stop and preserve evidence when blocked. Do not clear busy flags, restart OEM
processes, re-pair phones or reboot until the failure has been understood, unless
the owner chooses recovery. Unknown evidence must stay unknown. Captures remain
private and finite; embedded runtime bridges remain installed.

This matrix reports the scope of preserved evidence. No new owner switching
trial was performed by the reproduction review.
