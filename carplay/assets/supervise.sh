#!/system/bin/sh
# The original init flash_recovery service invokes this as root at boot.
umask 077
BASE=/data/local/ev6-carplay
STATE=$BASE/state
mkdir -p "$STATE" || exit 1
chmod 700 "$BASE" "$STATE" || exit 1
boot_wait=0
while [ "$(getprop sys.boot_completed)" != 1 ]; do
 [ "$boot_wait" -lt 120 ] || exit 2
 sleep 1
 boot_wait=$((boot_wait+1))
done
attempt=0
while [ -f "$BASE/enabled" ] && [ ! -e "$STATE/stop" ]; do
 CLASSPATH=/system/framework/com.broadcom.bt.jar:/system/framework/com.tcc.iap2.jar:/system/etc/ev6-carplay/controller.jar \
  /system/bin/app_process /system/bin CarPlayController >/dev/null 2>&1
 [ ! -e "$STATE/stop" ] && [ -f "$BASE/enabled" ] || break
 attempt=$((attempt+1))
 [ "$attempt" -lt 3 ] || break
 # Process recovery backoff, not a Bluetooth/CarPlay reconnect interval.
 sleep 3
done
exit 0
