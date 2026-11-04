#!/bin/bash
set -e
if [ "${DNF_HOST_NETWORK:-false}" = true ]; then
  python /opt/dnf-admin/host_network.py
  export DNF_ADMIN_PORT="${DNF_ADMIN_PORT:-8003}"
fi
exec /bin/bash /docker-entrypoint.sh.upstream "$@"
