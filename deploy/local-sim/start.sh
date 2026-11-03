#!/bin/bash
# Local simulator: real Web and Supervisor with harmless dummy game processes.
set -euo pipefail
mkdir -p /data/run /data/conf.d /data/monitor_ip /data/log /home/neople/game/cfg /etc/supervisor/conf.d
if [[ -f /data/Script.pvf ]]; then cp /data/Script.pvf /home/neople/game/Script.pvf; fi
if [[ -f /data/publickey.pem ]]; then cp /data/publickey.pem /home/neople/game/publickey.pem; fi
[[ -e /data/monitor_ip/MONITOR_PUBLIC_IP ]] || printf '127.0.0.1\n' > /data/monitor_ip/MONITOR_PUBLIC_IP
[[ -e /data/run/start_game.sh ]] || cp /opt/dnf-sim/start_game.sh /data/run/start_game.sh
chmod +x /data/run/start_game.sh
for number in 01 06 07 $(seq -w 11 39) 52 53 54 55 56; do
  cp /opt/dnf-sim/game.sh "/home/neople/game/siroco$number"
done
cat > /etc/supervisor/supervisord.conf <<'CONF'
[unix_http_server]
file=/tmp/dnf-sim-supervisor.sock
[supervisord]
nodaemon=true
logfile=/data/log/supervisord.log
pidfile=/tmp/dnf-sim-supervisord.pid
[rpcinterface:supervisor]
supervisor.rpcinterface_factory=supervisor.rpcinterface:make_main_rpcinterface
[supervisorctl]
serverurl=unix:///tmp/dnf-sim-supervisor.sock
[include]
files=/etc/supervisor/conf.d/*.conf /data/conf.d/*.conf
CONF
cat > /etc/supervisor/conf.d/channel.conf <<'CONF'
[program:channel]
command=/bin/sleep 999999
autostart=true
autorestart=true
stdout_logfile=/data/log/channel.log
[program:game_siroco53]
command=/bin/bash /data/run/start_game.sh 53 5
directory=/home/neople/game
autostart=true
autorestart=true
stopasgroup=true
killasgroup=true
stdout_logfile=/data/log/game_siroco53.log
[program:game_siroco54]
command=/bin/bash /data/run/start_game.sh 54 5
directory=/home/neople/game
autostart=true
autorestart=true
stopasgroup=true
killasgroup=true
stdout_logfile=/data/log/game_siroco54.log
[group:dnf_channel]
programs=channel,game_siroco53,game_siroco54
priority=999
CONF
if [[ "${DNF_SIM_LAYOUT:-integrated}" = integrated ]]; then
  mkdir -p /data/channel-manager/channels /data/channel-manager/ips
  if [[ ! -e /data/channel-manager/initialized ]]; then
    for number in 53 54; do
      printf 'mode=INHERIT\ntarget=\nenabled=true\ntype=\n' > "/data/channel-manager/channels/$number.properties"
    done
    printf '1\n' > /data/channel-manager/initialized
  fi
  python3 /app/dnf-integration/prepare_channels.py
elif [[ "${DNF_SIM_LAYOUT}" != legacy ]]; then
  echo 'DNF_SIM_LAYOUT must be integrated or legacy' >&2
  exit 1
fi
cat > /etc/supervisor/conf.d/web.conf <<'CONF'
[program:dnf_admin]
command=/opt/java/openjdk/bin/java -Xms64m -Xmx1536m -jar /app/app.jar
directory=/app
autostart=true
autorestart=true
startsecs=2
stdout_logfile=/dev/stdout
stdout_logfile_maxbytes=0
redirect_stderr=true
CONF
export SERVER_PORT=9001 DNF_DATA_DIR=/data DNF_GAME_DIR=/home/neople/game
export DNF_SUPERVISORCTL=/usr/local/bin/supervisorctl DNF_CHANNELS_ENABLED=auto
export DNF_MEMBER_RESET_ENABLED=false
exec /usr/bin/supervisord -n -c /etc/supervisor/supervisord.conf
