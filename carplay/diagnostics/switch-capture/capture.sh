#!/system/bin/sh
# Temporary passive sampler: no OEM controls, network changes, or startup hooks.
STAGE=/data/local/tmp/ev6-bt-lifecycle-capture-20261018
[ "$1" = "$STAGE" ] && [ -d "$STAGE" ] || exit 2
umask 077
trap '' HUP
export CLASSPATH=/system/framework/com.broadcom.bt.jar:/system/framework/com.tcc.iap2.jar:/system/etc/ev6-carplay/controller.jar
read started rest < /proc/uptime
started=${started%%.*}
echo $$ > "$STAGE/pid"
echo CAPTURE_READY=1
echo START_UPTIME=$started
# Standalone event timeline: file notifications + fast USB poll + OEM logcat.
# No package/system asset replacement and no boot hook.
CLASSPATH="$STAGE/timeline.jar" app_process /system/bin TimelineRecorder "$STAGE" >"$STAGE/timeline.stderr" 2>&1 </dev/null &
logger=$!
echo "$logger" > "$STAGE/timeline.pid"
finish() {
 touch "$STAGE/stop"
 wait "$logger" 2>/dev/null
 echo CAPTURE_ENDED=1
 touch "$STAGE/done"
}
trap 'finish; exit 0' TERM INT
i=0
while [ ! -f "$STAGE/stop" ]; do
 read now rest < /proc/uptime
 now=${now%%.*}
 [ $((now-started)) -lt 1200 ] || break
 echo SAMPLE=$i UPTIME=$now
 echo USB_STATE=$(getprop sys.usb.state)
 echo USB_CONFIG=$(getprop sys.usb.config)
 for node in /sys/class/android_usb/android0/state /sys/class/switch/usb_connected/state; do
  [ ! -r "$node" ] || { echo NODE=$node; cat "$node"; }
 done
 for node in /sys/bus/usb/devices/*/idVendor; do
  [ -r "$node" ] || continue
  parent=${node%/*}
  echo USB_NODE=${parent##*/}
  cat "$node"
  [ ! -r "$parent/idProduct" ] || cat "$parent/idProduct"
 done
 app_process /system/bin ControllerProbe 2>/dev/null
 app_process /system/bin OemAttemptProbe 2>/dev/null
 CLASSPATH="$CLASSPATH:$STAGE/timeline.jar" app_process /system/bin BluetoothWorkProbe 2>/dev/null
 # Only bounded state/status events; original controller remains untouched.
 [ ! -r /data/local/ev6-carplay/state/status ] || { echo CURRENT_STATUS; cat /data/local/ev6-carplay/state/status; }
 i=$((i+1))
 sleep 5
done
finish
