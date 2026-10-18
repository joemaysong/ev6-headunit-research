#!/system/bin/sh
# Exact-reference V16 system rollback. Owner stops through OEM UI first.
# No key, original backup, pairing database, or user credential is deleted.
set -eu
umask 077
BASE=/data/local/ev6-carplay
STATE=$BASE/state
ASSET=/system/etc/ev6-carplay
STAGE=$BASE/rollback-v16-staging
export CLASSPATH=/system/framework/com.broadcom.bt.jar:/system/framework/com.tcc.iap2.jar:$ASSET/controller.jar
[ "$(id -u)" = 0 ] || exit 1
[ "${1:-}" = --restore-reference-v16 ] || { echo 'Read docs/13-rollback-and-porting.md; explicit reference-V16 operation required'; exit 2; }
# This helper applies only to the frozen reference runtime, never an arbitrary port.
checksum() { set -- $(md5 "$1"); echo "$1"; }
[ "$(checksum /system/app/Bluetooth.odex)" = 91c0cfa3e53adff73956d18f4b8ab247 ] || exit 3
[ "$(checksum /system/app/HKMC_DiPO.odex)" = 1cc886f9deb0720eac8f67a00fa944fc ] || exit 3
[ "$(checksum /system/etc/ev6-carplay/controller.jar)" = e14d3a134b1abf47175507d2daeaf522 ] || exit 3
[ "$(checksum /system/lib/libCarPlayAndroid.so)" = ea0ca708efa1379d48e8bbffe3183f52 ] || exit 3
[ "$(checksum /system/lib/libCarPlayCore.so)" = acc35d6178084d8c4524ff14fb6eecbc ] || exit 3
[ "$(checksum /system/lib/libCarPlayAudio.so)" = f7a37c4594e23c40c5fa72f811f884e8 ] || exit 3
[ "$(checksum /system/lib/libCarPlay.so)" = 3104b375dc8d2d9c02aca7bec9f4db72 ] || exit 3
[ "$(checksum /system/app/HKMC_NatureSound.odex)" = 2d191cd5bfdc1b439450a95e69f079c0 ] || exit 3
backend_idle() {
 [ "$(cat /data/data/org.ev6.wirelesscarplay/files/session.state)" = 0 ] &&
 [ "$(cat /data/data/org.ev6.wirelesscarplay/files/backend.ready)" = 1 ]
}
backend_idle || exit 4
controller=$(app_process /system/bin ControllerProbe)
observer=$(app_process /system/bin OemAttemptProbe)
expect_controller() { echo "$controller" | grep -F -x "$1" >/dev/null; }
expect_observer() { echo "$observer" | grep -F -x "$1" >/dev/null; }
for receipt in NATIVE_READY=true NATIVE_ACTIVE=false BACKEND_SESSION_PUBLISHED=false OEM_CONNECTED_ADDRESS_PRESENT=false WIFI_AP_STATE=11 PROJECTION_AP_OWNER=false SAVED_CLIENT_PREFERENCE=false ANY_HFP_CONNECTED=false BLUETOOTH_WORK_BRIDGE_READY=true BLUETOOTH_WORK_BUSY=false BLUETOOTH_PROFILES_EMPTY=true UI_PROGRESS_ACTIVE=false; do expect_controller "$receipt" || exit 4; done
for receipt in OBSERVER_PROTOCOL=EV6ATTEMPT13 OBSERVER_UNKNOWN=false ORIGINAL_DISPATCHES=0 ORIGINAL_CONNECT_CALLS=0 ORIGINAL_QUEUED_MASK=0 TRANSPORT_OPEN_MASK=0 CURRENT_TRANSPORT_BLOCKER=0 PENDING_REQUEST_MASK=0 TRANSPORTS_SETTLED=true; do expect_observer "$receipt" || exit 4; done
[ ! -e "$STATE/ap-firewall.owner" ] || exit 4
[ ! -e "$STATE/ap-subnet.original" ] || exit 4
[ ! -e "$STAGE" ] || exit 5
# A historical bind/overlay setup needs its own namespace review first.
for info in /proc/[0-9]*/mountinfo; do
 [ ! -r "$info" ] || ! grep -E ' /system/(app/(HKMC_DiPO|HKMC_BtSettings|Bluetooth)\.odex|lib/libCarPlay(Audio)?\.so|etc/ev6-carplay(/[^ ]*)?|onebin/cvev_ir/featurexml\.cfg) ' "$info" >/dev/null || exit 6
done
# Validate every original BEFORE changing enabled state or remounting /system.
for receipt in 'Bluetooth.odex:26e81f710277a132e78acc66d2ebce8c' 'HKMC_DiPO.odex:b90a47c5419761bc5114e412fc8cf9ec' 'HKMC_BtSettings.odex:87b220ddaacb926221e25cdb2db8775a' 'HKMC_NatureSound.odex:2d191cd5bfdc1b439450a95e69f079c0' 'libCarPlayAudio.so:fca4c75b1ae39a88b83912f083ce7dda' 'libCarPlay.so:3104b375dc8d2d9c02aca7bec9f4db72' 'featurexml.cfg:debef8736658722186aa9a3ac7731f98'; do
 name=${receipt%%:*}; expected=${receipt#*:}
 [ "$(checksum "$BASE/original/$name")" = "$expected" ] || exit 7
done
feature=/system/etc/permissions/com.hkmc.software.connectivity.carplay_wireless.xml
[ ! -e "$feature" ] || [ "$(checksum "$feature")" = 95ff801a064526de56059dba10960b05 ] || exit 7
[ ! -e /system/etc/install-recovery.sh ] || [ "$(checksum /system/etc/install-recovery.sh)" = 04bd5a3bb191a0c9009334cc00e00473 ] || exit 7
mkdir "$STAGE"; chmod 700 "$STAGE"
# Stop only our worker through its normal stop marker, with no PID signaling.
touch "$STATE/stop"
rm -f "$BASE/enabled"
count=0
while [ -f "$STATE/controller.pid" ]; do
 read controller_pid < "$STATE/controller.pid"
 case "$controller_pid" in ''|*[!0-9]*) exit 8;; esac
 [ -d /proc/$controller_pid ] || break
 [ "$count" -lt 20 ] || exit 8
 sleep 1; count=$((count+1))
done
# Recheck real idle after the worker has exited; no disconnect/force-stop fallback.
backend_idle || exit 9
controller=$(app_process /system/bin ControllerProbe)
observer=$(app_process /system/bin OemAttemptProbe)
for receipt in NATIVE_READY=true NATIVE_ACTIVE=false BACKEND_SESSION_PUBLISHED=false OEM_CONNECTED_ADDRESS_PRESENT=false WIFI_AP_STATE=11 BLUETOOTH_WORK_BUSY=false BLUETOOTH_PROFILES_EMPTY=true; do expect_controller "$receipt" || exit 9; done
for receipt in OBSERVER_UNKNOWN=false TRANSPORT_OPEN_MASK=0 CURRENT_TRANSPORT_BLOCKER=0 PENDING_REQUEST_MASK=0 TRANSPORTS_SETTLED=true; do expect_observer "$receipt" || exit 9; done
# Preserve current system files privately before replacement. Keep this stage.
for name in Bluetooth.odex HKMC_DiPO.odex HKMC_BtSettings.odex libCarPlayAudio.so libCarPlay.so featurexml.cfg; do
 case "$name" in lib*) target=/system/lib/$name;; feature*) target=/system/onebin/cvev_ir/$name;; *) target=/system/app/$name;; esac
 cp -p "$target" "$STAGE/$name"
 [ "$(checksum "$target")" = "$(checksum "$STAGE/$name")" ] || exit 10
done
mount -o remount,rw /system
trap 'mount -o remount,ro /system' EXIT HUP INT TERM
for name in Bluetooth.odex HKMC_DiPO.odex HKMC_BtSettings.odex libCarPlayAudio.so libCarPlay.so featurexml.cfg; do
 case "$name" in lib*) target=/system/lib/$name;; feature*) target=/system/onebin/cvev_ir/$name;; *) target=/system/app/$name;; esac
 cp "$BASE/original/$name" "$target.ev6-restore"
 chown 0:0 "$target.ev6-restore"; chmod 644 "$target.ev6-restore"
 mv "$target.ev6-restore" "$target"
 [ "$(checksum "$target")" = "$(checksum "$BASE/original/$name")" ] || exit 11
 # Refresh the private asset mirror too; it is not executed while disabled.
 if [ -f "$ASSET/$name" ]; then
  cp "$BASE/original/$name" "$ASSET/$name.ev6-restore"
  chown 0:0 "$ASSET/$name.ev6-restore"; chmod 644 "$ASSET/$name.ev6-restore"
  mv "$ASSET/$name.ev6-restore" "$ASSET/$name"
 fi
done
[ ! -e "$feature" ] || rm "$feature"
[ ! -e /system/etc/install-recovery.sh ] || rm /system/etc/install-recovery.sh
sync
mount -o remount,ro /system
trap - EXIT HUP INT TERM
# Disable authored packages, preserving their private data and retained signing identity.
pm disable org.ev6.wirelesscarplay
pm disable org.ev6.carplaybutton
echo V16_SCOPED_SYSTEM_FILES_RESTORED_OWNER_FULL_REBOOT_REQUIRED
# Mapped Bluetooth/DiPO code remains old until a full reboot. Do not resume tests.
# Per-vehicle settings/AP preferences require baseline comparison in the runbook.
