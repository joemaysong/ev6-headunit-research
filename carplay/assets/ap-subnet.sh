#!/system/bin/sh
# AP-only subnet isolation, through netd; root-private recovery stays on car.
STATE=${EV6_STATE:-/data/local/ev6-carplay/state}
SAVE=$STATE/ap-subnet.original
umask 077
ndc_ok() {
 response=$(ndc "$@" 2>&1)
 if echo "$response" | grep -q '^200 [0-9][0-9]* '; then
  echo "NETD:$1:$2:code=200" >> "$STATE/subnet-operations"
  return 0
 fi
 code=${response%% *}
 case "$code" in ''|*[!0-9]*) code=unknown;; esac
 echo "NETD:$1:$2:code=$code" >> "$STATE/subnet-operations"
 echo AP_SUBNET_NETD_FAILED
 return 1
}
valid_address() { case "$1" in ''|*[!0-9.]*) return 1;; *) return 0;; esac; }
restore() {
 [ -f "$SAVE" ] || return 0
 IFS=' ' read orig_addr orig_prefix pool_lo pool_hi < "$SAVE" || return 1
 for address in "$orig_addr" "$pool_lo" "$pool_hi"; do valid_address "$address" || return 1; done
 [ "$orig_prefix" = 24 ] || return 1
 ndc_ok tether stop || return 1
 ndc_ok interface setcfg wlan0 "$orig_addr" "$orig_prefix" up || return 1
 ndc_ok tether start "$pool_lo" "$pool_hi" || return 1
 set -- $(ndc interface getcfg wlan0 | grep '^213 ')
 [ "$1" = 213 ] && [ "$4" = "$orig_addr" ] && [ "$5" = "$orig_prefix" ] || return 1
 rm -f "$SAVE"
 echo AP_SUBNET_RESTORED
}
case "$1" in restore) restore; exit $?;; start) ;; *) exit 1;; esac
[ ! -e "$SAVE" ] || { echo AP_SUBNET_RECOVERY_REQUIRED; exit 1; }
set -- $(ndc interface getcfg wlan0 | grep '^213 ')
[ "$1" = 213 ] && [ "$5" = 24 ] || exit 1
orig_addr=$4; orig_prefix=$5
valid_address "$orig_addr" || exit 1
orig_base=${orig_addr%.*}
# Only one tether owner may be changed.
iface_count=0
while read code seq iface rest; do
 [ "$code" = 111 ] || continue
 [ "$iface" = wlan0 ] || exit 1
 iface_count=$((iface_count+1))
done <<EOF
$(ndc tether interface list)
EOF
[ "$iface_count" = 1 ] || exit 1
conflict=0
while read iface flag cidr rest; do
 [ "$flag" = UP ] || continue
 [ "$iface" = lo ] && continue
 [ "$iface" = wlan0 ] && continue
 case "$cidr" in 0.0.0.0/*) continue;; */24|*/32) ;; *) echo AP_SUBNET_UNSUPPORTED_NETWORK; exit 1;; esac
 addr=${cidr%/*}
 [ "${addr%.*}" = "$orig_base" ] && conflict=1
done <<EOF
$(netcfg)
EOF
[ "$conflict" = 1 ] || { echo AP_SUBNET_ALREADY_ISOLATED; exit 0; }
candidate=50
while [ "$candidate" -lt 100 ]; do
 used=0
 while read iface flag cidr rest; do
  [ "$flag" = UP ] || continue
  case "$cidr" in "192.168.$candidate."*) used=1;; esac
 done <<EOF
$(netcfg)
EOF
 [ "$used" = 0 ] && break
 candidate=$((candidate+1))
done
[ "$candidate" -lt 100 ] || exit 1
dns_pid=
while read row; do
 set -- $row
 [ "$#" -gt 1 ] || continue
 selected_pid=$2
 for cell do selected_name=$cell; done
 case "$selected_name" in dnsmasq|/system/bin/dnsmasq) dns_pid=$selected_pid; break;; esac
done <<EOF
$(ps)
EOF
case "$dns_pid" in ''|*[!0-9]*) exit 1;; esac
# NUL-separated argv is retained only in this root process, never logged.
cmdline=$(cat /proc/$dns_pid/cmdline)
case "$cmdline" in *--dhcp-range=*) ;; *) exit 1;; esac
pool=${cmdline#*--dhcp-range=}
case "$pool" in *--dhcp-range=*) exit 1;; esac
pool_lo=${pool%%,*}; pool=${pool#*,}; pool_hi=${pool%%,*}
valid_address "$pool_lo" && valid_address "$pool_hi" || exit 1
eth_before=$(cat /proc/net/route | grep '^eth0')
echo "$orig_addr $orig_prefix $pool_lo $pool_hi" > "$SAVE" || exit 1
ndc_ok tether stop || exit 1
ndc_ok interface setcfg wlan0 "192.168.$candidate.1" 24 up || exit 1
ndc_ok tether start "192.168.$candidate.2" "192.168.$candidate.254" || exit 1
set -- $(ndc interface getcfg wlan0 | grep '^213 ')
[ "$1" = 213 ] && [ "$4" = "192.168.$candidate.1" ] && [ "$5" = 24 ] || exit 1
eth_after=$(cat /proc/net/route | grep '^eth0')
[ "$eth_before" = "$eth_after" ] || exit 1
echo AP_SUBNET_ISOLATED_ETHERNET_UNCHANGED
