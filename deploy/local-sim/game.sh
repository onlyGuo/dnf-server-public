#!/bin/bash
# The simulator runs this script using a copy of bash named df_game_r, so the
# process has the same command-line shape as a real DNF channel process.
number=${0#siroco}
python3 /opt/dnf-sim/listen.py "$number" &
child=$!
trap 'kill "$child" 2>/dev/null || true; wait "$child" 2>/dev/null || true; exit 0' TERM INT
wait "$child"
