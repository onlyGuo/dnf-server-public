#!/bin/sh
# Preserve existing signing keys when preparing the standalone Web data directory.
set -eu
directory=${1:?key directory required}
mkdir -p "$directory"
private="$directory/private.key"
public="$directory/publickey.pem"
if [ -f "$private" ] && [ -f "$public" ]; then
    echo 'Existing Web keys found.'
elif [ -f "$private" ]; then
    temporary=$(mktemp "$directory/.publickey.XXXXXX")
    if openssl pkey -in "$private" -pubout -out "$temporary" 2>/dev/null || \
       { printf '%s\n' '-----BEGIN PRIVATE KEY-----'; awk '{sub(/\r$/, ""); print}' "$private"; printf '%s\n' '-----END PRIVATE KEY-----'; } | openssl pkey -pubout -out "$temporary" 2>/dev/null; then
        chmod 644 "$temporary"
        mv "$temporary" "$public"
        echo 'Derived the missing public key from the existing private key.'
    else
        rm -f "$temporary"
        echo 'WARNING: could not derive a public key; the existing private key was preserved.' >&2
    fi
elif [ -f "$public" ]; then
    echo 'WARNING: install the matching private key; the existing public key was preserved.' >&2
else
    umask 077
    temporary_private=$(mktemp "$directory/.privatekey.XXXXXX")
    temporary_public=$(mktemp "$directory/.publickey.XXXXXX")
    if openssl genpkey -algorithm RSA -out "$temporary_private" -pkeyopt rsa_keygen_bits:2048 2>/dev/null && \
       openssl pkey -in "$temporary_private" -pubout -out "$temporary_public" 2>/dev/null; then
        chmod 600 "$temporary_private"
        chmod 644 "$temporary_public"
        mv "$temporary_private" "$private"
        mv "$temporary_public" "$public"
        echo 'Created new Web keys.'
    else
        rm -f "$temporary_private" "$temporary_public"
        echo 'Could not generate Web keys.' >&2
        exit 1
    fi
fi
