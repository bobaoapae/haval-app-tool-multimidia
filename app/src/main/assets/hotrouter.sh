#!/system/bin/sh

# hotrouter.sh
#
# Automatic router daemon for the multimedia hotspot.
#
# Goal:
# - Prefer external WLAN (wlan0), e.g. Starlink, for hotspot clients.
# - Fall back to OEM 4G route (vlan13) when WLAN is unavailable.
#
# Usage:
#   sh hotrouter.sh start   # run the routing loop (default)
#   sh hotrouter.sh stop    # kill the daemon and tear down all rules

BASE="/data/local/tmp"
NAME="hotrouter"
LOG="$BASE/$NAME.log"
PIDFILE="$BASE/$NAME.pid"
STATEFILE="$BASE/$NAME.state"
HOTSPOT_IF="wlan2"
WLAN_IF="wlan0"
WLAN_TABLE="wlan0"
RULE_PRIO="17999"
CHECK_HOSTS="8.8.8.8 1.1.1.1"
INTERVAL_SEC=5
MAX_LOG_LINES=400
HEARTBEAT_EVERY=24

log() {
  echo "$(date '+%Y-%m-%d %H:%M:%S') [$1] $2" >> "$LOG"
}

write_state() {
  echo "$1|$(date +%s)" > "$STATEFILE"
}

trim_log() {
  [ -f "$LOG" ] || return
  lines="$(wc -l < "$LOG" 2>/dev/null)"
  [ "$lines" -gt "$MAX_LOG_LINES" ] || return
  tail -n "$MAX_LOG_LINES" "$LOG" > "$LOG.tmp" && mv "$LOG.tmp" "$LOG"
}

kill_old_hotrouters() {
  self="$$"
  # Kill the recorded daemon pid first. Toybox `ps` does not print script args, so a
  # `ps | grep hotrouter.sh` sweep never matches the setsid'd daemon — the pidfile and a
  # /proc/<pid>/cmdline scan are the only reliable ways to find it.
  if [ -f "$PIDFILE" ]; then
    oldpid="$(cat "$PIDFILE" 2>/dev/null)"
    if [ -n "$oldpid" ] && [ "$oldpid" != "$self" ]; then
      kill -9 "$oldpid" 2>/dev/null
    fi
  fi
  for p in /proc/[0-9]*; do
    pid="${p#/proc/}"
    [ "$pid" = "$self" ] && continue
    # Read via cat so a process exiting mid-scan (cmdline gone) is swallowed by 2>/dev/null
    # instead of leaking a shell "can't open" error to stderr.
    cmd="$(cat "$p/cmdline" 2>/dev/null | tr '\0' ' ')"
    case "$cmd" in
      *hotrouter.sh*start*) kill -9 "$pid" 2>/dev/null ;;
    esac
  done
  rm -f "$PIDFILE"
}

cleanup_duplicate_rules() {
  while ip rule | grep -q "iif $HOTSPOT_IF lookup $WLAN_TABLE"; do
    ip rule del from all iif "$HOTSPOT_IF" lookup "$WLAN_TABLE" priority "$RULE_PRIO" 2>/dev/null || break
  done
}

ensure_rule_once() {
  cleanup_duplicate_rules
  ip rule add from all iif "$HOTSPOT_IF" lookup "$WLAN_TABLE" priority "$RULE_PRIO" 2>/dev/null
}

chain_exists() {
  iptables -nL "$1" >/dev/null 2>&1
}

nat_chain_exists() {
  iptables -t nat -nL "$1" >/dev/null 2>&1
}

ensure_iptables() {
  nat_chain_exists tetherctrl_nat_POSTROUTING || {
    log ERROR "Missing NAT chain tetherctrl_nat_POSTROUTING"
    return 1
  }

  chain_exists tetherctrl_FORWARD || {
    log ERROR "Missing chain tetherctrl_FORWARD"
    return 1
  }

  chain_exists tetherctrl_counters || {
    log ERROR "Missing chain tetherctrl_counters"
    return 1
  }

  iptables -t nat -C tetherctrl_nat_POSTROUTING -o "$WLAN_IF" -j MASQUERADE 2>/dev/null || \
  iptables -t nat -I tetherctrl_nat_POSTROUTING 1 -o "$WLAN_IF" -j MASQUERADE

  iptables -C tetherctrl_FORWARD -i "$WLAN_IF" -o "$HOTSPOT_IF" -m state --state RELATED,ESTABLISHED -g tetherctrl_counters 2>/dev/null || \
  iptables -I tetherctrl_FORWARD 1 -i "$WLAN_IF" -o "$HOTSPOT_IF" -m state --state RELATED,ESTABLISHED -g tetherctrl_counters

  iptables -C tetherctrl_FORWARD -i "$HOTSPOT_IF" -o "$WLAN_IF" -m state --state INVALID -j DROP 2>/dev/null || \
  iptables -I tetherctrl_FORWARD 2 -i "$HOTSPOT_IF" -o "$WLAN_IF" -m state --state INVALID -j DROP

  iptables -C tetherctrl_FORWARD -i "$HOTSPOT_IF" -o "$WLAN_IF" -g tetherctrl_counters 2>/dev/null || \
  iptables -I tetherctrl_FORWARD 3 -i "$HOTSPOT_IF" -o "$WLAN_IF" -g tetherctrl_counters

  iptables -C tetherctrl_counters -i "$HOTSPOT_IF" -o "$WLAN_IF" -j RETURN 2>/dev/null || \
  iptables -I tetherctrl_counters 1 -i "$HOTSPOT_IF" -o "$WLAN_IF" -j RETURN

  iptables -C tetherctrl_counters -i "$WLAN_IF" -o "$HOTSPOT_IF" -j RETURN 2>/dev/null || \
  iptables -I tetherctrl_counters 2 -i "$WLAN_IF" -o "$HOTSPOT_IF" -j RETURN

  return 0
}

teardown_iptables() {
  while iptables -t nat -C tetherctrl_nat_POSTROUTING -o "$WLAN_IF" -j MASQUERADE 2>/dev/null; do
    iptables -t nat -D tetherctrl_nat_POSTROUTING -o "$WLAN_IF" -j MASQUERADE 2>/dev/null || break
  done

  while iptables -C tetherctrl_FORWARD -i "$WLAN_IF" -o "$HOTSPOT_IF" -m state --state RELATED,ESTABLISHED -g tetherctrl_counters 2>/dev/null; do
    iptables -D tetherctrl_FORWARD -i "$WLAN_IF" -o "$HOTSPOT_IF" -m state --state RELATED,ESTABLISHED -g tetherctrl_counters 2>/dev/null || break
  done

  while iptables -C tetherctrl_FORWARD -i "$HOTSPOT_IF" -o "$WLAN_IF" -m state --state INVALID -j DROP 2>/dev/null; do
    iptables -D tetherctrl_FORWARD -i "$HOTSPOT_IF" -o "$WLAN_IF" -m state --state INVALID -j DROP 2>/dev/null || break
  done

  while iptables -C tetherctrl_FORWARD -i "$HOTSPOT_IF" -o "$WLAN_IF" -g tetherctrl_counters 2>/dev/null; do
    iptables -D tetherctrl_FORWARD -i "$HOTSPOT_IF" -o "$WLAN_IF" -g tetherctrl_counters 2>/dev/null || break
  done

  while iptables -C tetherctrl_counters -i "$HOTSPOT_IF" -o "$WLAN_IF" -j RETURN 2>/dev/null; do
    iptables -D tetherctrl_counters -i "$HOTSPOT_IF" -o "$WLAN_IF" -j RETURN 2>/dev/null || break
  done

  while iptables -C tetherctrl_counters -i "$WLAN_IF" -o "$HOTSPOT_IF" -j RETURN 2>/dev/null; do
    iptables -D tetherctrl_counters -i "$WLAN_IF" -o "$HOTSPOT_IF" -j RETURN 2>/dev/null || break
  done
}

starlink_has_ping() {
  ip link show "$HOTSPOT_IF" >/dev/null 2>&1 || return 1
  ip link show "$WLAN_IF" >/dev/null 2>&1 || return 1
  ip route show table "$WLAN_TABLE" | grep -q "^default" || return 1

  for host in $CHECK_HOSTS; do
    ping -I "$WLAN_IF" -c 1 -W 2 "$host" >/dev/null 2>&1 && return 0
  done

  return 1
}

route_to_starlink() {
  echo 1 > /proc/sys/net/ipv4/ip_forward
  ensure_rule_once
  ensure_iptables || return 1
  ip route flush cache
  return 0
}

route_to_4g() {
  cleanup_duplicate_rules
  ip route flush cache
}

do_stop() {
  # kill_old_hotrouters kills the daemon via pidfile + /proc cmdline scan (toybox ps
  # does not show script args, so a ps-based sweep can't find the setsid'd daemon).
  kill_old_hotrouters
  cleanup_duplicate_rules
  teardown_iptables
  ip route flush cache
  write_state "OFF"
  log INFO "Service stopped + teardown done"
}

CMD="${1:-start}"

case "$CMD" in
  stop)
    do_stop
    exit 0
    ;;
  start)
    ;;
  *)
    echo "usage: $0 {start|stop}"
    exit 1
    ;;
esac

kill_old_hotrouters

echo $$ > "$PIDFILE"

trap 'rm -f "$PIDFILE"; write_state "OFF"; log INFO "Service stopped"; exit 0' INT TERM EXIT

echo 1 > /proc/sys/net/ipv4/ip_forward

last_mode="initial"
tick=0

log INFO "Service force-started"
log INFO "Old hotrouter processes killed"
log INFO "Hotspot=$HOTSPOT_IF | Starlink=$WLAN_IF | Table=$WLAN_TABLE | Ping=$CHECK_HOSTS"

while true; do
  trim_log

  if starlink_has_ping; then
    if route_to_starlink; then
      mode="starlink"
    else
      # Could not apply the WLAN route (e.g. tetherctrl chains missing). Clean up
      # any half-applied diversion rule so the hotspot genuinely falls back to 4G,
      # keeping the reported "4G" state honest instead of leaving traffic broken.
      route_to_4g
      mode="broken"
      log ERROR "Starlink has ping, but route_to_starlink failed; fell back to 4G"
    fi
  else
    route_to_4g
    mode="4g"
  fi

  case "$mode" in
    starlink) write_state "WLAN" ;;
    *)        write_state "4G" ;;
  esac

  if [ "$mode" != "$last_mode" ]; then
    case "$mode" in
      starlink)
        log INFO "Route transition: $last_mode -> STARLINK. Hotspot forced through $WLAN_IF."
        ;;
      4g)
        log WARN "Route transition: $last_mode -> 4G. Starlink ping unavailable."
        ;;
      broken)
        log ERROR "Route transition: $last_mode -> BROKEN. Starlink ping OK but firewall/routing failed."
        ;;
    esac
    last_mode="$mode"
  fi

  tick=$((tick + 1))

  if [ "$tick" -ge "$HEARTBEAT_EVERY" ]; then
    log INFO "Heartbeat: current_mode=$mode"
    tick=0
  fi

  sleep "$INTERVAL_SEC"
done
