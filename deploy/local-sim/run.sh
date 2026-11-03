#!/bin/bash
# Local simulation only. The database is supplied by a local environment file.
set -euo pipefail
project=$(cd "$(dirname "$0")/../.." && pwd)
mode=${1:-integrated}
sim_root="$project/dnf_mock/local-sim"
legacy_sim="$project/data/local-sim"
mkdir -p "$sim_root"
if [[ -d "$legacy_sim" && ! -e "$sim_root/.migrated-web-data" ]]; then
  cp -pRn "$legacy_sim/." "$sim_root/"
  : > "$sim_root/.migrated-web-data"
fi
env_file=${DNF_LOCAL_ENV_FILE:-"$project/dnf_mock/local-test.env"}
if [[ -z "${DNF_LOCAL_ENV_FILE:-}" && ! -f "$env_file" && -f "$project/data/local-test.env" ]]; then
  install -m 600 "$project/data/local-test.env" "$env_file"
fi
web_data="$sim_root/web-data"
port=${DNF_LOCAL_WEB_PORT:-19001}
if [[ ! -f "$env_file" ]]; then
  echo "Create $env_file with the SPRING_DATASOURCE_* environment variables first." >&2
  exit 1
fi
if command -v docker >/dev/null 2>&1; then engine=docker
elif command -v container >/dev/null 2>&1; then engine=container
else echo 'Install Docker or Apple container to run the simulator.' >&2; exit 1
fi
case "$mode" in integrated|legacy|web-only) ;; *) echo 'Use integrated, legacy, or web-only.' >&2; exit 1;; esac
mkdir -p "$web_data"
dnf_source=${DNF_GAME_SOURCE:-"$project/../dnf"}
if [[ "$mode" != web-only ]]; then
  game_data="$sim_root/$mode-data"
  mkdir -p "$game_data"
  if [[ ! -e "$game_data/Script.pvf" ]]; then
    if [[ -f "$web_data/Script.pvf" ]]; then
      cp "$web_data/Script.pvf" "$game_data/Script.pvf"
    elif [[ -f "$dnf_source/build/dnf_data/home/template/init/Script.tgz" ]]; then
      tar -xOzf "$dnf_source/build/dnf_data/home/template/init/Script.tgz" Script.pvf > "$game_data/Script.pvf.tmp"
      mv "$game_data/Script.pvf.tmp" "$game_data/Script.pvf"
    fi
  fi
  key_source="$web_data/private.key"
  if [[ ! -f "$key_source" && -f "$project/data/private.key" ]]; then key_source="$project/data/private.key"; fi
  if [[ ! -f "$key_source" && -f "$project/src/main/resources/private.key" ]]; then key_source="$project/src/main/resources/private.key"; fi
  if [[ ! -f "$key_source" ]]; then key_source="$dnf_source/build/dnf_data/home/template/init/privatekey.pem"; fi
  bash "$project/deploy/local-sim/prepare-keys.sh" "$game_data" "$key_source"
fi
cd "$project"
"$engine" build -t dnf-web:local -f Dockerfile .
if [[ "$mode" = web-only ]]; then
  name=dnf-local-web-only
  image=dnf-web:local
  container_port=80
else
  name="dnf-local-sim-$mode"
  "$engine" build -t dnf-local-simulator:local -f deploy/local-sim/Dockerfile .
  image=dnf-local-simulator:local
  container_port=9001
fi
mounts=(-v "$web_data:/app/data")
npk_dir=${DNF_LOCAL_NPK_DIR:-"$project/data/ImagePacks2"}
if [[ -d "$npk_dir" && ! -f "$web_data/.npk-initialized" ]]; then
  mkdir -p "$web_data/ImagePacks2"
  if [[ -n "$(find "$web_data/ImagePacks2" -type f -iname '*.npk' -print -quit)" ]]; then
    : > "$web_data/.npk-initialized"
  elif [[ -n "$(find "$npk_dir" -type f -iname '*.npk' -print -quit)" ]]; then
    # Seed a private copy so an upload cannot overwrite the original client files.
    cp -Rn "$npk_dir/." "$web_data/ImagePacks2/"
    : > "$web_data/.npk-initialized"
  fi
fi
if [[ "$mode" != web-only ]]; then
  mounts+=(-v "$game_data:/data")
fi
"$engine" run -d --name "$name" -m 3G --env-file "$env_file" \
  -e DNF_DB_AUTO_INIT=false -e DNF_MEMBER_RESET_ENABLED=false -e "DNF_SIM_LAYOUT=$mode" \
  -p "127.0.0.1:$port:$container_port" \
  "${mounts[@]}" "$image"
printf 'Local %s is starting: http://127.0.0.1:%s/login\n' "$mode" "$port"
printf 'Stop it with: %s stop %s\n' "$engine" "$name"
printf 'Start it again with: %s start %s\n' "$engine" "$name"
