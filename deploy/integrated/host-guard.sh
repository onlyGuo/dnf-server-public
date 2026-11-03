#!/bin/bash
# Run on the Linux Docker host, not inside the container. Restrict incoming
# sockets belonging to this one host-networked container, including dynamic UDP.
set -euo pipefail
container=${DNF_CONTAINER_NAME:-dnf}
lan_if=${DNF_HOST_GUARD_LAN_IF:-}
lan4=${DNF_HOST_GUARD_LAN_IPV4:-}
lan6=${DNF_HOST_GUARD_LAN_IPV6:-}
interval=${DNF_HOST_GUARD_INTERVAL:-2}
case "$container" in *[!a-zA-Z0-9_.-]*|'') echo 'Invalid container name' >&2; exit 2;; esac
table="dnf_host_guard_$(printf '%s' "$container" | sha256sum | cut -c1-12)"
[[ -z "$lan_if" || "$lan_if" =~ ^[a-zA-Z0-9_.:-]{1,15}$ ]] || { echo 'Invalid LAN interface' >&2; exit 2; }
[[ -z "$lan4" || "$lan4" =~ ^[0-9.]+/[0-9]+$ ]] || { echo 'Invalid LAN IPv4 CIDR' >&2; exit 2; }
[[ -z "$lan6" || "$lan6" =~ ^[a-fA-F0-9:]+/[0-9]+$ ]] || { echo 'Invalid LAN IPv6 CIDR' >&2; exit 2; }
[[ "$interval" =~ ^[1-9][0-9]*$ ]] || { echo 'Invalid poll interval' >&2; exit 2; }
if [[ -n "$lan4$lan6" && -z "$lan_if" ]]; then echo 'Set DNF_HOST_GUARD_LAN_IF with LAN CIDRs' >&2; exit 2; fi

install_guard() {
  local running pid group path level inode boot data current admin server tcp udp relay stun tmp marker
  running=$(docker inspect --format '{{.State.Running}}' "$container" 2>/dev/null) || return 0
  [[ "$running" == true ]] || return 0
  [[ "$(docker inspect --format '{{.HostConfig.NetworkMode}}' "$container")" == host ]] || return 0
  docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$container" | grep -qx 'DNF_HOST_NETWORK=true' || return 0
  pid=$(docker inspect --format '{{.State.Pid}}' "$container")
  [[ "$pid" =~ ^[1-9][0-9]*$ ]] || return 1
  group=$(awk -F: '$1==0 {print $3}' "/proc/$pid/cgroup")
  path=${group#/}
  [[ "$path" =~ ^[a-zA-Z0-9_./-]+$ && -d "/sys/fs/cgroup/$path" ]] || { echo 'Unsupported cgroup path' >&2; return 1; }
  level=$(awk -F/ '{print NF}' <<< "$path")
  inode=$(stat -c %i "/sys/fs/cgroup/$path")
  boot=$(cat /proc/sys/kernel/random/boot_id)
  data=$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/data"}}{{.Source}}{{end}}{{end}}' "$container")
  [[ -n "$data" && -d "$data" ]] || { echo '/data must be a host bind mount' >&2; return 1; }
  mkdir -p "$data/channel-manager"
  marker="$data/channel-manager/host-guard-ready"
  current=$(cat "$marker" 2>/dev/null || true)
  if [[ "$current" == "$boot:$inode" ]] && nft list table inet "$table" >/dev/null 2>&1; then return 0; fi
  rm -f "$marker"
  server=$(docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$container" | sed -n 's/^SERVER_GROUP=//p' | tail -1)
  admin=$(docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$container" | sed -n 's/^DNF_ADMIN_PORT=//p' | tail -1)
  server=${server:-3}; admin=${admin:-8003}
  [[ "$server" =~ ^[1-6]$ && "$admin" =~ ^[1-9][0-9]{0,4}$ && "$admin" -le 65535 ]] || { echo 'Invalid DNF group or admin port' >&2; return 1; }
  tcp=$((server * 10000)); udp=$((tcp + 1000)); relay=$((7000 + server * 100)); stun=$((2000 + server * 100 + 11))
  if (( admin == 2000 || admin == 3000 || admin == 7001 || admin == relay ||
        admin == tcp+1 || admin == tcp+6 || admin == tcp+7 ||
        (admin >= tcp+11 && admin <= tcp+39) || (admin >= tcp+52 && admin <= tcp+56) )); then
    echo 'Admin port conflicts with an exposed DNF port' >&2; return 1
  fi
  tmp=$(mktemp /tmp/dnf-host-guard.XXXXXX)
  if nft list table inet "$table" >/dev/null 2>&1; then printf 'delete table inet %s\n' "$table" >> "$tmp"; fi
  {
    printf 'table inet %s {\n chain inbound {\n  type filter hook input priority filter; policy accept;\n' "$table"
    printf '  iifname "lo" return\n  ct state established,related return\n'
    printf '  socket cgroupv2 level %s "%s" tcp dport 3000 iifname "tailscale0" return\n' "$level" "$path"
    printf '  socket cgroupv2 level %s "%s" tcp dport 3000 iifname "docker0" return\n' "$level" "$path"
    printf '  socket cgroupv2 level %s "%s" tcp dport 3000 iifname "br-*" return\n' "$level" "$path"
    if [[ -n "$lan4" ]]; then printf '  socket cgroupv2 level %s "%s" tcp dport 3000 iifname "%s" ip saddr %s return\n' "$level" "$path" "$lan_if" "$lan4"; fi
    if [[ -n "$lan_if" ]]; then printf '  socket cgroupv2 level %s "%s" tcp dport 3000 iifname "%s" ip6 saddr { fc00::/7, fe80::/10 } return\n' "$level" "$path" "$lan_if"; fi
    if [[ -n "$lan6" ]]; then printf '  socket cgroupv2 level %s "%s" tcp dport 3000 iifname "%s" ip6 saddr %s return\n' "$level" "$path" "$lan_if" "$lan6"; fi
    printf '  socket cgroupv2 level %s "%s" tcp dport { 2000, %s, 7001, %s, %s, %s, %s, %s-%s, %s-%s } return\n' \
      "$level" "$path" "$admin" "$relay" "$((tcp+1))" "$((tcp+6))" "$((tcp+7))" "$((tcp+11))" "$((tcp+39))" "$((tcp+52))" "$((tcp+56))"
    printf '  socket cgroupv2 level %s "%s" udp dport { 7001, %s, %s-%s, %s, %s, %s, %s-%s, %s-%s } return\n' \
      "$level" "$path" "$relay" "$stun" "$((stun+2))" "$((udp+1))" "$((udp+6))" "$((udp+7))" "$((udp+11))" "$((udp+39))" "$((udp+52))" "$((udp+56))"
    printf '  socket cgroupv2 level %s "%s" drop\n }\n}\n' "$level" "$path"
  } >> "$tmp"
  if ! nft -c -f "$tmp" || ! nft -f "$tmp"; then rm -f "$tmp"; return 1; fi
  rm -f "$tmp"
  # Ensure the container did not restart while the rules were being applied.
  if [[ "$(docker inspect --format '{{.State.Pid}}' "$container")" != "$pid" ]] || [[ "$(stat -c %i "/sys/fs/cgroup/$path" 2>/dev/null || true)" != "$inode" ]]; then return 1; fi
  printf '%s:%s\n' "$boot" "$inode" > "$marker.tmp"
  mv -f "$marker.tmp" "$marker"
  echo "Host firewall active for container $container (cgroup inode $inode)"
}

if [[ "${1:-}" == '--once' ]]; then install_guard; exit; fi
while true; do
  if ! install_guard; then echo 'Could not apply the DNF host firewall; startup remains blocked' >&2; fi
  sleep "$interval"
done
