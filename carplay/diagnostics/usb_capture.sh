#!/system/bin/sh
# Optional finite read-only capture; never installed by normal startup.
STAGE="$1"
case "$STAGE" in /data/local/tmp/ev6-*) ;; *) exit 2;; esac
case "$STAGE" in *[!a-zA-Z0-9_./-]*) exit 2;; esac
[ -d "$STAGE" ] || exit 2
export CLASSPATH=/system/framework/com.broadcom.bt.jar:/system/framework/com.tcc.iap2.jar:/system/etc/ev6-carplay/controller.jar
trap '' HUP
echo $$ > "$STAGE/pid"
echo CAPTURE_READY=1
i=0
while [ "$i" -lt 120 ] && [ ! -f "$STAGE/stop" ]; do
 echo SAMPLE=$i
 app_process /system/bin ControllerProbe 2>/dev/null
 app_process /system/bin OemAttemptProbe 2>/dev/null
 i=$((i+1))
 sleep 5
done
echo CAPTURE_ENDED=1
