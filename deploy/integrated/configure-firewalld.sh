#!/bin/bash
# Open the DNF host-network ports in firewalld while preserving pre-existing rules.
# The DNF host guard must already be active to restrict the database port.
set -euo pipefail
group=${DNF_SERVER_GROUP:-3}
admin=${DNF_ADMIN_PORT:-8003}
container=${DNF_CONTAINER_NAME:-dnf}
zone=${DNF_FIREWALL_ZONE:-$(firewall-cmd --get-default-zone)}
record=${1:?pass a directory in which to record only newly added rules}
[[ "$group" =~ ^[1-6]$ && "$admin" =~ ^[1-9][0-9]{0,4}$ && "$admin" -le 65535 ]] || { echo 'Invalid group or admin port' >&2; exit 2; }
[[ "$zone" =~ ^[a-zA-Z0-9_-]+$ ]] || { echo 'Invalid firewalld zone' >&2; exit 2; }
systemctl is-active --quiet dnf-host-guard.service || { echo 'Start dnf-host-guard before opening ports' >&2; exit 1; }
[[ "$container" =~ ^[a-zA-Z0-9_.-]+$ ]] || { echo 'Invalid container name' >&2; exit 2; }
[[ "$(docker inspect --format '{{.HostConfig.NetworkMode}}' "$container")" == host ]] || { echo 'Container is not using host networking' >&2; exit 1; }
pid=$(docker inspect --format '{{.State.Pid}}' "$container")
[[ "$pid" =~ ^[1-9][0-9]*$ ]] || { echo 'Container is not running' >&2; exit 1; }
cgroup=$(awk -F: '$1==0 {print $3}' "/proc/$pid/cgroup")
data=$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/data"}}{{.Source}}{{end}}{{end}}' "$container")
identity="$(cat /proc/sys/kernel/random/boot_id):$(stat -c %i "/sys/fs/cgroup$cgroup")"
[[ -n "$data" && "$(cat "$data/channel-manager/host-guard-ready" 2>/dev/null || true)" == "$identity" ]] || { echo 'Host guard is not ready for this container' >&2; exit 1; }
table="dnf_host_guard_$(printf '%s' "$container" | sha256sum | cut -c1-12)"
nft list table inet "$table" >/dev/null || { echo 'Host guard firewall table is missing' >&2; exit 1; }
mkdir -p "$record"
runtime="$record/firewalld-runtime-added"
permanent="$record/firewalld-permanent-added"
test ! -e "$runtime" && test ! -e "$permanent" || { echo 'Rule record already exists' >&2; exit 1; }
: > "$runtime"
: > "$permanent"
chmod 600 "$runtime" "$permanent"
tcp=$((group*10000)); udp=$((tcp+1000)); relay=$((7000+group*100)); stun=$((2000+group*100+11))
ports=("2000/tcp" "3000/tcp" "$admin/tcp" "7001/tcp" "7001/udp" "$relay/tcp" "$relay/udp" "$stun-$((stun+2))/udp"
       "$((tcp+1))/tcp" "$((tcp+6))/tcp" "$((tcp+7))/tcp" "$((tcp+11))-$((tcp+39))/tcp" "$((tcp+52))-$((tcp+56))/tcp"
       "$((udp+1))/udp" "$((udp+6))/udp" "$((udp+7))/udp" "$((udp+11))-$((udp+39))/udp" "$((udp+52))-$((udp+56))/udp")
rollback() {
  local spec
  while IFS= read -r spec; do [[ -z "$spec" ]] || firewall-cmd --zone="$zone" --remove-port="$spec" >/dev/null 2>&1 || true; done < "$runtime"
  while IFS= read -r spec; do [[ -z "$spec" ]] || firewall-cmd --permanent --zone="$zone" --remove-port="$spec" >/dev/null 2>&1 || true; done < "$permanent"
}
trap 'rollback' ERR
for spec in "${ports[@]}"; do
  if firewall-cmd --zone="$zone" --query-port="$spec" >/dev/null 2>&1; then continue; else code=$?; fi
  if [[ "$code" -ne 1 ]]; then echo "Cannot query firewalld runtime rules" >&2; false; fi
  printf '%s\n' "$spec" >> "$runtime"
  firewall-cmd --zone="$zone" --add-port="$spec" >/dev/null
done
for spec in "${ports[@]}"; do
  if firewall-cmd --permanent --zone="$zone" --query-port="$spec" >/dev/null 2>&1; then continue; else code=$?; fi
  if [[ "$code" -ne 1 ]]; then echo "Cannot query firewalld permanent rules" >&2; false; fi
  printf '%s\n' "$spec" >> "$permanent"
  firewall-cmd --permanent --zone="$zone" --add-port="$spec" >/dev/null
done
trap - ERR
printf 'Firewalld zone %s updated without a reload; new rules recorded in %s\n' "$zone" "$record"
