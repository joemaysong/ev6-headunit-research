# Instructions for reproducing or porting V16

Read README.md, docs/11-build-and-deployment.md, docs/13-rollback-and-porting.md,
docs/17-reproduction-and-porting.md, profiles/v16-release.json and
validation/v16-reproduction-review.json before changing a target.

The reference combination is Wired gate16 + controller14 + Bluetooth bridge14 +
EV6ATTEMPT13 + retained OEM9 backend/audio/UI. A V16 label does not mean each
component exposes revision16. Preserve the current working vehicle until the
owner requests deployment. This repository review/build workflow is offline.

Keep all owner originals, complete private smali trees, exact accepted runtime
assets, existing backend/button signing keys and certificates, and the external
initial-backup pointer. Never replace an original backup with a modified file.
Never regenerate an existing package's signing identity. Do not print secrets,
copy them into a public staging area, upload captures, or delete private keys.

Use a new private workspace outside this repository for OEM inputs and outputs.
Run tools/check_publication.py before publication. PUBLICATION.json inventories
public files only; it is not a runtime deployment manifest. Every referenced
public runtime helper must have source and a build route. Keep historical
validation distinct from current checks and actual owner acceptance.

For the matching reference profile, use the documented local build commands and
hash/semantic contracts. An input/contract mismatch stops that component. Never
remove a check or relabel a different firmware to make the reference patch run.
For another firmware, inventory its own hardware/boot/ABI/AP/Binder/native
contracts and create a new profile and new integration recipe. Preserve the
lifecycle invariants, not this vehicle's offsets, credentials or device IDs.

Original iAP2/MFi/PhoneProjection/native code owns authentication, retries,
sessions, finalization and transport choice. Root controller owns its own AP,
firewall and selected UI intent. ACK is not outcome. Timeout is not completion.
Missing evidence remains UNKNOWN. Do not clear busy/session flags, restart OEM
processes, delete pairing, force USB resets or broaden cancellation to pass a test.
Bluetooth cancellation must stay inside the exact OEM Handler/work generation.
Wired identification must match fresh original Added/Updated, USB/intent
lifetime, device identity and current controller object.

Before a requested deployment: obtain pristine recovery, verify inactive real
native/backend/OEM association/AP/Bluetooth work, stop via original UI, make a
reviewed file/ownership/permission plan and preserve the current working pair.
The owner performs full reboot. After reboot verify every installed copy, loaded
process generation, protocol/revisions and /system read-only. Stop on uncertainty.
Optional recorders are bounded and removable; embedded bridges are runtime
dependencies. Do not install a recorder as a boot service.
