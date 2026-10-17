#!/system/bin/sh
# Restore only the CarPlay changes in this release. Keep original backups.
set -e
umask 077
BASE=/data/local/ev6-carplay
STATE=$BASE/state
ASSET=/system/etc/ev6-carplay
export CLASSPATH=/system/framework/com.broadcom.bt.jar:/system/framework/com.tcc.iap2.jar:$ASSET/controller.jar
[ "$(id -u)" = 0 ] || exit 1
touch "$STATE/stop"
rm -f "$BASE/enabled"
count=0
while [ -f "$STATE/controller.pid" ]; do
 read controller_pid < "$STATE/controller.pid"
 case "$controller_pid" in ''|*[!0-9]*) exit 2;; esac
 [ -d /proc/$controller_pid ] || break
 [ "$count" -lt 15 ] || exit 3
 sleep 1
 count=$((count+1))
done
app_process /system/bin RecoveryTool disconnect
if [ -f "$STATE/ap-subnet.original" ]; then
 EV6_STATE=$STATE sh "$ASSET/ap-subnet.sh" restore
fi
if [ -f "$STATE/saved-client-state" ]; then
 app_process /system/bin OemApSwitch stop "$STATE/saved-client-state" /data/data/org.ev6.wirelesscarplay/files/event-state
fi
app_process /system/bin RecoveryTool ap-original
app_process /system/bin RecoveryTool inactive
am force-stop com.daudio.app.dipo
am force-stop com.android.btsettings
am force-stop org.ev6.wirelesscarplay
zygote_pid=
while read uid process parent rest; do
 case "$rest" in *' zygote') zygote_pid=$process;; esac
done <<EOF
$(ps)
EOF
case "$zygote_pid" in ''|*[!0-9]*) exit 4;; esac
"$ASSET/detach-overlays" "$zygote_pid"
for check in \
 'libCarPlayAudio.so:fca4c75b1ae39a88b83912f083ce7dda' \
 'libCarPlay.so:3104b375dc8d2d9c02aca7bec9f4db72' \
 'HKMC_BtSettings.odex:87b220ddaacb926221e25cdb2db8775a' \
 'featurexml.cfg:debef8736658722186aa9a3ac7731f98'; do
 name=${check%%:*}; expected=${check#*:}
 set -- $(md5 "$BASE/original/$name")
 [ "$1" = "$expected" ] || exit 5
done
mount -o remount,rw /system
trap 'mount -o remount,ro /system' EXIT
for name in libCarPlayAudio.so libCarPlay.so HKMC_BtSettings.odex featurexml.cfg; do
 case "$name" in
  lib*) target=/system/lib/$name;;
  HKMC*) target=/system/app/$name;;
  feature*) target=/system/onebin/cvev_ir/$name;;
 esac
 cp "$BASE/original/$name" "$target.ev6-restore"
 chmod 644 "$target.ev6-restore"
 chown 0:0 "$target.ev6-restore"
 mv "$target.ev6-restore" "$target"
done
feature=/system/etc/permissions/com.hkmc.software.connectivity.carplay_wireless.xml
if [ -f "$feature" ]; then
 set -- $(md5 "$feature")
 [ "$1" = 95ff801a064526de56059dba10960b05 ] || exit 6
 rm "$feature"
fi
if [ -f /system/etc/install-recovery.sh ]; then
 set -- $(md5 /system/etc/install-recovery.sh)
 [ "$1" = 04bd5a3bb191a0c9009334cc00e00473 ] || exit 7
 rm /system/etc/install-recovery.sh
fi
sync
mount -o remount,ro /system
trap - EXIT
for name in phoneprojection_setting_enable_wireless phoneprojection_setting_enable_wireless_dr1 phoneprojection_setting_enable_wireless_dr2; do
 content delete --uri content://settings/system --where "name='$name'"
done
pm uninstall org.ev6.wirelesscarplay
pm uninstall org.ev6.carplaybutton
setprop sys.ev6.cp.busy ''
setprop sys.ev6.cp.until 0
app_process /system/bin WifiClientPolicy
native_pid=
while read uid process parent rest; do
 case "$rest" in *' com.daudio.app.dipo') native_pid=$process;; esac
done <<EOF
$(ps)
EOF
if [ -n "$native_pid" ]; then
 case "$native_pid" in *[!0-9]*) exit 8;; esac
 kill -15 "$native_pid"
 count=0
 while [ -d /proc/$native_pid ]; do
  [ "$count" -lt 10 ] || exit 9
  sleep 1
  count=$((count+1))
 done
fi
am startservice -n com.daudio.app.dipo/.service.DipoService
echo CARPLAY_ORIGINAL_BINARIES_RESTORED_BACKUPS_RETAINED
echo REBOOT_REQUIRED_TO_REFRESH_PACKAGE_FEATURE_CACHE
