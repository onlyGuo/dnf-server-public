#!/bin/bash
# Manage a real local Supervisor and harmless simulated DNF channel processes.
set -euo pipefail
project=$(cd "$(dirname "$0")/../.." && pwd)
root=${DNF_MOCK_ROOT:-"$project/dnf_mock"}
action=${1:-start}
ctl="$root/usr/local/bin/supervisorctl"
case "$action" in
  start)
    mkdir -p "$root/usr/lib/python" "$root/home/neople/game" "$root/data"
    web_data="$root/local-sim/web-data"
    old_web_data="$project/data/local-sim/web-data"
    dnf_source=${DNF_GAME_SOURCE:-"$project/../dnf"}
    if [[ ! -e "$root/data/Script.pvf" ]]; then
      if [[ -f "$root/home/neople/game/Script.pvf" ]]; then
        cp "$root/home/neople/game/Script.pvf" "$root/data/Script.pvf"
      elif [[ -f "$web_data/Script.pvf" ]]; then
        cp "$web_data/Script.pvf" "$root/data/Script.pvf"
      elif [[ -f "$old_web_data/Script.pvf" ]]; then
        cp "$old_web_data/Script.pvf" "$root/data/Script.pvf"
      elif [[ -f "$dnf_source/build/dnf_data/home/template/init/Script.tgz" ]]; then
        tar -xOzf "$dnf_source/build/dnf_data/home/template/init/Script.tgz" Script.pvf > "$root/data/Script.pvf.tmp"
        mv "$root/data/Script.pvf.tmp" "$root/data/Script.pvf"
      fi
    fi
    if [[ ! -e "$root/home/neople/game/Script.pvf" && -f "$root/data/Script.pvf" ]]; then
      cp "$root/data/Script.pvf" "$root/home/neople/game/Script.pvf"
    fi
    key_source="$project/data/private.key"
    if [[ ! -f "$key_source" && -f "$web_data/private.key" ]]; then key_source="$web_data/private.key"; fi
    if [[ ! -f "$key_source" && -f "$old_web_data/private.key" ]]; then key_source="$old_web_data/private.key"; fi
    if [[ ! -f "$key_source" && -f "$project/src/main/resources/private.key" ]]; then key_source="$project/src/main/resources/private.key"; fi
    if [[ ! -f "$key_source" ]]; then key_source="$dnf_source/build/dnf_data/home/template/init/privatekey.pem"; fi
    bash "$project/deploy/local-sim/prepare-keys.sh" "$root/data" "$key_source" "$root/home/neople/game"
    if [[ ! -f "$root/usr/lib/python/supervisor/supervisord.py" ]]; then
      python3 -m pip install --disable-pip-version-check --no-input --target "$root/usr/lib/python" 'supervisor==4.3.0'
    fi
    if [[ ! -x "$root/home/neople/game/df_game_r" ]]; then
      cc -O2 "$project/deploy/local-sim/ide/game.c" -o "$root/home/neople/game/df_game_r"
    fi
    python3 "$project/deploy/local-sim/ide/setup.py" "$root"
    if ! "$ctl" pid >/dev/null 2>&1; then
      PYTHONPATH="$root/usr/lib/python${PYTHONPATH:+:$PYTHONPATH}" python3 -m supervisor.supervisord -c "$root/etc/supervisord.conf"
    fi
    for i in {1..20}; do if "$ctl" pid >/dev/null 2>&1; then break; fi; sleep 1; done
    "$ctl" status || echo 'Some simulated channels are stopped.'
    ;;
  stop) "$ctl" shutdown ;;
  status) "$ctl" status ;;
  *) echo 'Use start, stop, or status.' >&2; exit 2 ;;
esac
