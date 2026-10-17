#!/system/bin/sh
# Added only when the original path is absent. No recovery flashing.
if [ -f /data/local/ev6-carplay/enabled ]; then
 exec /system/bin/sh /system/etc/ev6-carplay/supervise.sh
fi
exit 0
