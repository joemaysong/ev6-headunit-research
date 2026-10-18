# Reproduction and porting workflow for another Codex workspace

## What can be reproduced

This repository preserves the authored implementation and the method for the
reference firmware `CV_E.GEN.S5W_M.V001.001.211105`, product
`wp_daudioplus_cvev_ir`, ARM32/Android4.4.2/API19/Dalvik ODEX36. Its owner supplies
matching proprietary originals. Exact recovery uses private frozen binaries.
Source reconstruction produces reviewed candidates; target loading and operation
are separate checks. A different head unit needs new contracts and acceptance.

Ask Codex to: read AGENTS.md, inventory supplied private inputs, compare this
reference profile, reproduce each component offline, document every mismatch,
prepare a scoped deployment/recovery plan, and report what still requires target
verification. Do not treat a build success as a universal vehicle installation.

## 1. Establish the target and independent recovery

Record the new target's model/market, product/firmware, Android API/VM, CPU/kernel,
partition geometry and file hashes in its own private inventory. Confirm root
capabilities and an independently accessible recovery/stock boot path before
changing system files. Follow docs/02-04 for the reference research and its limits;
RAM boot acceptance is not successful boot, and a live raw backup is not a tested
whole-disk restoration. Do not reuse another car's boot image or misc partition.

Preserve first originals, current accepted state, signing identities, permissions,
package paths, feature/settings values, AP/client configuration and external backup
pointer. Keep credentials and phone identifiers private. Public profiles contain
hashes/contracts only. Use an explicit EV6_ADB_SERIAL for any later authorized
vehicle operation; no utility should guess the target from an old IP or name.

## 2. Required private inputs

| Input | Purpose |
|---|---|
| All `originals` listed in profiles/v16-release.json | Hash-locked reference reconstruction and recovery |
| Original Bluetooth.apk paired with Bluetooth.odex | Preserve original resources/package/native dependencies |
| Matching framework ODEX/JAR dependencies from the target | Correct Dalvik field/vtable/inline resolution and runtime |
| Stock Iap2ServerApp.apk, com.tcc.iap2.jar, com.broadcom.bt.jar | Original runtime authentication/service APIs; remain on target |
| Private AP/client/settings baseline and package/permission inventory | Correct per-owner deployment/rollback |
| Existing backend and button certificate/key, installed package copies | Update-signature continuity; never silently replace identity |
| Exact private V16 assets and complete accepted private smali, when available | Bit-exact recovery and comparison; not public source prerequisites |

The original DiPO and Bluetooth declare 16 framework ODEX dependencies:
core, conscrypt, okhttp, core-junit, bouncycastle, ext, framework, framework2,
telephony-common, voip-common, mms-common, android.policy, services, apache-xml,
webviewchromium and automotive. Keep their matching JARs too if present. The
helper reads the actual dependency table and compares each local ODEX DEX-header
signature; preserve full framework SHA-256 in your private input manifest.

```sh
python3 carplay/tools/check_inputs.py \
  --originals "$EV6_PRIVATE/original" --framework "$EV6_PRIVATE/framework"
```

A missing/different input is an explicit blocker. A different firmware must get a
new profile and reviewed integration changes. Do not overwrite reference hashes
or lower the checks merely to produce an output.

## 3. Obtain a correct pristine Dalvik deodex baseline

Use smali/baksmali/dexlib2 **2.5.2 with dependencies**, the target's own framework,
and `carplay/tools/Ev6DalvikDeodex.java`. It explicitly selects API19,
`ClassPath.NOT_ART` and the original ODEX-version inline resolver. The failed
historical default ART-layout output must not be assembled or deployed.

```sh
mkdir "$EV6_PRIVATE/deodex-tools"
"$JAVAC" -cp "$SMALI_CLASSPATH" -d "$EV6_PRIVATE/deodex-tools" carplay/tools/Ev6DalvikDeodex.java
"$JAVA" -cp "$EV6_PRIVATE/deodex-tools:$SMALI_CLASSPATH" Ev6DalvikDeodex \
  "$EV6_PRIVATE/original/HKMC_DiPO.odex" "$EV6_PRIVATE/framework" "$EV6_PRIVATE/pristine-dipo-smali"
"$JAVA" -cp "$EV6_PRIVATE/deodex-tools:$SMALI_CLASSPATH" Ev6DalvikDeodex \
  "$EV6_PRIVATE/original/Bluetooth.odex" "$EV6_PRIVATE/framework" "$EV6_PRIVATE/pristine-bluetooth-smali"
```

Require no AnalysisException, unresolved quick/inline opcodes, tool-generated
verification-error throws or missing classes. The reference pristine inventories
are 302 DiPO and 718 Bluetooth OEM classes. Some null-receiver quick `close()`
sites were not correctly inferred automatically by the original tooling. The
public `carplay/patches/dalvik-close-resolutions.json` records the exact original
method/site and resolved call contracts, including those cases. Compare the raw
original instruction, its original vtable slot, debug type and matching framework;
repair only the tool's unresolved substitution at that same site, preserving the
original exceptional control flow. Never replace an original throw or infer a
call from a similar name. The complete canonical class hashes in the component
recipes must then match; instruction order, registers, descriptors and exceptions
are retained, while debug positions/label spellings are normalized.

This review checked hook reconstruction against pristine instructions recovered
from the original raw ODEX plus the accepted preserved resolution contracts. It
did not freshly pull all framework dependencies or claim a new end-to-end
vehicle deodex/cache trial. A future workspace must complete its own pristine
input/deodex check; a mismatch stays blocked with the exact class/method recorded.

## 4. Rebuild all authored components

Use every relevant command in docs/11. The hooks retain original method bodies
in renamed private methods, with exception-aware authored wrappers. DiPO's recipe
also includes the retained wireless initialization compatibility edit and delegate
entry hook. The Wired gate includes both Added and Updated; omitting Updated
reproduces the V15 gap. The Bluetooth recipe adds only three boundary hooks and
five processMessage wrappers; cancellation stays in the original machine.

Controller/provider/backend/button build without proprietary APK templates. The
backend dynamically loads the on-target OEM iAP2 service. Preserve its package,
frameworks, native libraries and behavior. Keep backend/button keys separate and
verify each update signer against that package's installed APK.

The BtSettings patch preserves its original APK and resources. Audio preserves
stock control/authentication/native libraries and patches only the verified audio
library. The reference audio/BtSettings/feature outputs have exact final hash
checks. Other outputs need instruction/ABI comparison, not a byte-identical
promise. New native station-reader source implements the fixed root-only bounded
PTY query and is a reconstructed helper needing live validation.

## 5. Runtime file and ownership plan

| Artifact | Reference destination / required handling |
|---|---|
| controller.jar | /system/etc/ev6-carplay/controller.jar, root:root 0644 |
| HKMC_DiPO.odex | /system/app/HKMC_DiPO.odex AND retained /system/etc/ev6-carplay/HKMC_DiPO.odex mirror; matching original stripped APK |
| Bluetooth.odex | /system/app/Bluetooth.odex; matching original Bluetooth.apk; full reboot |
| HKMC_BtSettings.odex | /system/app/HKMC_BtSettings.odex plus retained asset mirror; original APK/resources unchanged |
| libCarPlayAudio.so | /system/lib/libCarPlayAudio.so plus retained asset mirror, root:root 0644 |
| libCarPlay.so | Stock original control/disconnect library; do not substitute old teardown candidates |
| featurexml.cfg | /system/onebin/cvev_ir/featurexml.cfg; remove only the matching CarPlay-wireless exclusion |
| wireless feature XML | /system/etc/permissions/com.hkmc.software.connectivity.carplay_wireless.xml |
| AP subnet/reply scripts and supervisor | /system/etc/ev6-carplay; executable scripts root:root 0755; preserve OEM chains |
| wl-stations | /system/etc/ev6-carplay/wl-stations, executable; associated-station method must work under Java ProcessBuilder |
| install-recovery.sh | /system/etc/install-recovery.sh only if original path was absent and init contract is verified |
| backend.apk / button.apk | Install with their retained identities; root-private APK mirrors for recovery; verify resolved pm paths/hashes |
| operational state / originals | /data/local/ev6-carplay, root-private; preserve originals and current recovery manifest |
| backend/button files directories | Resolve actual installed package UID; do not hard-code another car's app UID |

System ODEX/native/config files use root:root 0644 unless the target baseline
requires otherwise. Keep original timestamps/modes/ownership where meaningful.
No raw APK/ODEX/DEX/native binary belongs on GitHub.

Before enabling startup, preserve original AP configuration on the vehicle,
provision only the required wireless settings/feature grants, install the APKs with their own retained signers (`adb -s "$EV6_ADB_SERIAL" install -r` on each local private APK), then use the reviewed explicit service component
`org.ev6.wirelesscarplay/org.ev6.research.ProbeService` to initialize the backend.
Preserve app-owned private files directories. If the button files directory must
be provisioned before its first valid device command, resolve the actual package
UID from package-manager output, create only `/data/data/org.ev6.carplaybutton/files`,
assign that package's own UID/GID and mode0700, and verify it. The controller runs
as full root and needs no world-readable package data. It creates controller-ready
as an existence marker; CpCommand writes its own AtomicFile command. Review that
files contract before enabling. Do not make a guessed
UID or world-writable application directory the deployment recipe. Preserve the
three original phoneprojection_setting_enable_wireless values/absence for rollback.
The OEM init flash_recovery->install-recovery.sh path is reference-specific.

Disable phones' Bluetooth/Wi-Fi and remove phone USB as agreed with the owner
when establishing installation idle. Verify native/backend/OEM association/AP/
Bluetooth profiles/work, preserve any live session, stop only through original UI,
and stage/hash all scoped replacements before remount. Keep current-pair recovery.
The owner performs a full reboot after writes. Verify /system read-only and
loaded process generation, controller14, EV6ATTEMPT13, gate16 logs and Bluetooth
bridge readiness. Check every mirror and pm-installed APK, not just a disk copy.

## 6. Acceptance and failure handling

Start from a clean baseline. Use docs/16's matrix, independently verify native/
backend/OEM actual identity and transport, then obtain owner screen/App feedback.
Classify Added and Updated separately. Keep exact cancellation unaccepted until
its actual guarded positive branch has been exercised. Include audio short-tail,
Siri/call/priority and boot/reconnect cases as separate observations.

Optional switch capture is finite and private; see docs/15. A command ACK is not
successful connection. A pending query or outer timeout does not settle work.
When blocked, preserve the state and stop repeated taps so the first fault remains
understandable. No unconditional flag clearing, phone re-pairing, AP reset, receiver
restart or targetless Bluetooth cancel may substitute for the original lifecycle.
For rollback use docs/13 and the target's original files/settings, then verify
actual reboot/loading/operation. Keep keys and first originals throughout.

## 7. Contracts that change on another vehicle

| Area | Re-establish before adapting |
|---|---|
| Boot/root/recovery | Actual boot format, partition/misc scope, root capabilities, independently working rescue |
| VM/architecture | Dalvik versus ART, API, ARM32/other ABI, ODEX dependency/vtable and PCM ioctl layouts |
| Native state | All hard-coded addresses in NativeSession, mDNS probes/control and native transport integration, against exact library hashes |
| OEM services | PhoneProjection and Bluetooth Binder descriptors, method signatures, service UID, Handler ownership, message enums and return semantics |
| DiPO lifecycle | Endpoint/request/initialization/details/start/finalization ordering, original object identity, actual negotiated transport |
| USB | Attach/detach actions, Added/Updated callbacks, controller inventory and generations |
| Network | AP/client APIs, DHCP subnet, netd responses, interface/iptables chains and real associated-station query |
| UI/packages | Actual device-row identity, resource IDs, signing/grants, installed UID/files contracts and startup hooks |
| Audio | Original capability construction, codec availability, PCM struct/ioctl and state-based drain behavior |

Keep the same ownership/lifecycle reasoning and scoped evidence, while deriving
new offsets/resources/hooks from that vehicle's own originals. If it lacks the
required OEM wireless receiver/MFi/iAP2/PhoneProjection hardware/software, this
repository does not supply those components. Document the concrete gap.
