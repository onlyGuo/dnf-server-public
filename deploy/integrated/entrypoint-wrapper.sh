#!/bin/bash
set -e
if [ "${DNF_HOST_NETWORK:-false}" = true ]; then
  mkdir -p /data/channel-manager
  identity="$(cat /proc/sys/kernel/random/boot_id):$(stat -c %i /sys/fs/cgroup)"
  echo 'Waiting for the host-network firewall guard to protect this container...'
  until [ "$(cat /data/channel-manager/host-guard-ready 2>/dev/null || true)" = "$identity" ]; do sleep 2; done
  python /opt/dnf-admin/host_network.py
  export DNF_ADMIN_PORT="${DNF_ADMIN_PORT:-8003}"
fi
exec /bin/bash /docker-entrypoint.sh.upstream "$@"
