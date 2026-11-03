#!/bin/bash
set -eu
channel_no=$1
channel_name="siroco$channel_no"
printf 'ip = %s\n' "$(cat /data/monitor_ip/MONITOR_PUBLIC_IP)" > "/tmp/$channel_name.cfg"
cp /tmp/$channel_name.cfg /home/neople/game/cfg/$channel_name.cfg
cd /home/neople/game
exec ./df_game_r "$channel_name" start
