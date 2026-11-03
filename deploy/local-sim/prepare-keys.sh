#!/bin/bash
# Seed the simulator from the existing key without overwriting an established key.
set -euo pipefail
game_data=${1:?game data directory required}
source_key=${2:-}
game_dir=${3:-}
mkdir -p "$game_data"
umask 077
if [[ ! -e "$game_data/privatekey.pem" ]]; then
  if [[ -z "$source_key" || ! -f "$source_key" ]]; then
    echo 'No existing RSA private key found for the local simulator.' >&2
    exit 1
  fi
  temporary=$(mktemp "$game_data/.privatekey.XXXXXX")
  trap 'rm -f "$temporary"' EXIT
  if openssl pkey -passin pass: -in "$source_key" -out "$temporary" 2>/dev/null || \
     { printf '%s\n' '-----BEGIN PRIVATE KEY-----'; cat "$source_key"; printf '\n%s\n' '-----END PRIVATE KEY-----'; } | \
       openssl pkey -passin pass: -out "$temporary" 2>/dev/null; then
    chmod 600 "$temporary"
    mv "$temporary" "$game_data/privatekey.pem"
  else
    echo 'The existing simulator private key could not be read.' >&2
    exit 1
  fi
  trap - EXIT
fi
if [[ ! -e "$game_data/publickey.pem" ]]; then
  temporary=$(mktemp "$game_data/.publickey.XXXXXX")
  trap 'rm -f "$temporary"' EXIT
  openssl pkey -passin pass: -in "$game_data/privatekey.pem" -pubout -out "$temporary" 2>/dev/null
  chmod 644 "$temporary"
  mv "$temporary" "$game_data/publickey.pem"
  trap - EXIT
fi
if [[ -n "$game_dir" && ! -e "$game_dir/publickey.pem" ]]; then
  mkdir -p "$game_dir"
  cp "$game_data/publickey.pem" "$game_dir/publickey.pem"
  chmod 644 "$game_dir/publickey.pem"
fi
