# Recreate optional diagnostics

Final requires the embedded EV6ATTEMPT13 observation bridge, session ledger and root-only Bluetooth exact-work bridge. They provide functional guard receipts and must remain installed. They do not require a persistent full capture worker. Deleting these runtime channels would break the final controller.

## Finite vehicle recorder

Source is carplay/diagnostics/switch-capture. Set JAVA, ECJ_JAR, ANDROID_JAR and R8_JAR as in docs/11. CONTROLLER_SOURCE defaults to carplay/src. Build in a private fresh directory; choose the authorized target explicitly:

```sh
python3 carplay/diagnostics/switch-capture/build_timeline.py "$EV6_PRIVATE/timeline-build"
export TIMELINE_JAR="$EV6_PRIVATE/timeline-build/timeline.jar"
export ADB=/path/to/adb
export EV6_ADB_SERIAL=OWNER_SELECTED_VEHICLE_SERIAL
python3 carplay/diagnostics/switch-capture/manage.py start
```

The serial above is a placeholder. Use status, collect and remove with the same explicit target. The script refuses to guess from connected devices. It records USB properties/sysfs/enumeration, observed UI commands and ACK/outcome, original callbacks, request/session/actual transport and fixed Bluetooth work flags. Actual taps that emit no command are not guaranteed visible.

Recording is local, bounded to 20 minutes with rotation; it continues when ADB is unplugged. collect stops and exports private files; remove deletes only that recorder stage and its own Dalvik cache. Never add it to boot hooks. Keep captures private and remove them after analysis. Do not remove operational known-peer, pause, session, ownership or saved-client-state files.

## Read-only probes

Run ControllerProbe/OemAttemptProbe with the exact final controller JAR and original framework classpath, in a separate root app_process. Use fixed counters/booleans; preserve UNKNOWN. Do not fake completion from an outer timeout or clear busy flags to pass acceptance.

## Phone diagnostics

Use a finite USB syslog relay, selecting a unique ProductType. Keep identifiers private. Existing iPhone X jailbreak SSH may be reached through an ephemeral usbmux forward to the existing phone port; read credentials interactively, never store them in this repository. Do not remove jailbreak or system SSH as project cleanup. A relay does not imply an installed phone recorder. Stop only the specific host relay/proxy and remove its own output/forward.

## Final closeout

Preserve initial original inputs, exact final assets, existing signing identity and complete private rebuild inputs. Remove standalone recorder/build stages, temporary probes/caches and obsolete project candidate copies only after hashes and ownership are verified. Embedded work bridges remain required. Do not delete phone pairing, Wi-Fi credentials, user apps, normal analytics or OEM logs globally.
