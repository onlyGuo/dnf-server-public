#!/bin/bash
# Exercise the DNF image's actual Supervisor without starting a game or database.
set -euo pipefail
image=${1:?image tag required}
name="dnfqa-supervisor-$$"
work=$(mktemp -d /tmp/dnf-supervisor-qa.XXXXXX)
cleanup() { docker rm -fv "$name" >/dev/null 2>&1 || true; rm -rf "$work"; }
trap cleanup EXIT
mkdir -p "$work/conf.d"
cat > "$work/supervisord.conf" <<'CONF'
[unix_http_server]
file=/qa/supervisor.sock
[supervisord]
nodaemon=true
logfile=/qa/supervisord.log
pidfile=/qa/supervisord.pid
[rpcinterface:supervisor]
supervisor.rpcinterface_factory=supervisor.rpcinterface:make_main_rpcinterface
[supervisorctl]
serverurl=unix:///qa/supervisor.sock
[include]
files=/qa/conf.d/*.conf
CONF
cat > "$work/conf.d/channel.conf" <<'CONF'
[program:channel]
command=/bin/sleep 300
autostart=true
autorestart=true
[group:dnf_channel]
programs=channel
CONF
docker run -d --name "$name" --network none -v "$work:/qa" --entrypoint /usr/local/bin/supervisord "$image" -n -c /qa/supervisord.conf >/dev/null
for i in {1..30}; do
  if docker exec "$name" supervisorctl -c /qa/supervisord.conf status dnf_channel:channel 2>/dev/null | grep -q RUNNING; then break; fi
  sleep 1
done
central=$(docker exec "$name" supervisorctl -c /qa/supervisord.conf pid dnf_channel:channel)
[[ "$central" =~ ^[1-9][0-9]*$ ]]
cat > "$work/conf.d/channel-admin-53.conf" <<'CONF'
[program:channel_admin_53]
command=/bin/sleep 300
autostart=true
autorestart=true
stopasgroup=true
killasgroup=true
depend=channel
CONF
docker exec "$name" supervisorctl -c /qa/supervisord.conf reread >/dev/null
docker exec "$name" supervisorctl -c /qa/supervisord.conf add channel_admin_53 >/dev/null
for i in {1..20}; do
  if docker exec "$name" supervisorctl -c /qa/supervisord.conf status channel_admin_53 2>/dev/null | grep -q RUNNING; then break; fi
  sleep 1
done
docker exec "$name" supervisorctl -c /qa/supervisord.conf status channel_admin_53 | grep -q RUNNING
docker exec "$name" supervisorctl -c /qa/supervisord.conf stop channel_admin_53 >/dev/null
stopped=$(docker exec "$name" supervisorctl -c /qa/supervisord.conf status channel_admin_53 2>/dev/null || true)
[[ "$stopped" == *STOPPED* ]]
[[ "$central" == "$(docker exec "$name" supervisorctl -c /qa/supervisord.conf pid dnf_channel:channel)" ]]
printf 'PASS: Supervisor added and stopped an independent channel without restarting the central channel\n'
