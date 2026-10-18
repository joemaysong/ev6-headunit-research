#!/system/bin/sh
# Projection-AP IPv4 replies for connections initiated by this head unit.
# Keep OEM hotspot restrictions. Own chain/jump only; never flush OEM chains.
STATE=${EV6_STATE:-/data/local/ev6-carplay/state}
OWNER=$STATE/ap-firewall.owner
CHAIN=ev6_cp_reply
umask 077

rule() {
 iptables "$@" "$CHAIN" -i wlan0 -p tcp -m conntrack --ctstate ESTABLISHED --ctdir REPLY -j ACCEPT
}
owned() { [ -f "$OWNER" ] && [ "$(cat "$OWNER")" = ev6-cp-ipv4-reply-v1 ]; }
validate() {
 # A chain with unexpected rules is preserved for review rather than flushed.
 rules=$(iptables -S "$CHAIN") || return 1
 count=0
 while IFS= read -r row; do
  case "$row" in "-A $CHAIN "*) count=$((count+1));; esac
 done <<EOF
$rules
EOF
 if [ "$count" = 0 ] && [ "$1" = allow-empty ]; then return 0; fi
 [ "$count" = 1 ] && rule -C
}
restore() {
 if ! owned; then
  if iptables -S "$CHAIN" >/dev/null 2>&1; then return 1; fi
  [ ! -e "$OWNER" ] || return 1
  echo AP_FIREWALL_ABSENT
  return 0
 fi
 if iptables -S "$CHAIN" >/dev/null 2>&1; then
  validate allow-empty || return 1
  attempts=0
  while iptables -C fw_wlan_INPUT -j "$CHAIN" >/dev/null 2>&1; do
   [ "$attempts" -lt 8 ] || return 1
   iptables -D fw_wlan_INPUT -j "$CHAIN" || return 1
   attempts=$((attempts+1))
  done
  iptables -F "$CHAIN" && iptables -X "$CHAIN" || return 1
 fi
 rm -f "$OWNER" || return 1
 echo AP_FIREWALL_RESTORED
}
start() {
 [ -d "$STATE" ] && [ -f "$STATE/ap-owner" ] && [ -f "$STATE/saved-client-state" ] || return 1
 iptables -S fw_wlan_INPUT >/dev/null 2>&1 || return 1
 if iptables -S "$CHAIN" >/dev/null 2>&1; then
  owned && validate || return 1
 else
  [ ! -e "$OWNER" ] || owned || return 1
  iptables -N "$CHAIN" || return 1
  echo ev6-cp-ipv4-reply-v1 > "$OWNER" || { iptables -X "$CHAIN"; return 1; }
  if ! rule -A; then
   iptables -X "$CHAIN" && rm -f "$OWNER"
   return 1
  fi
 fi
 if ! iptables -C fw_wlan_INPUT -j "$CHAIN" >/dev/null 2>&1; then
  iptables -I fw_wlan_INPUT 1 -j "$CHAIN" || { restore; return 1; }
 fi
 validate && iptables -C fw_wlan_INPUT -j "$CHAIN" || return 1
 echo AP_FIREWALL_READY_REPLY_ONLY
}
case "$1" in start) start;; restore) restore;; *) exit 2;; esac
