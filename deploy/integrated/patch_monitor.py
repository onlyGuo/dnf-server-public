#!/usr/bin/env python
# Preserve IPv4 DDNS parsing used by the existing DNF deployment.
from __future__ import print_function
import os
import pipes
import shutil
import sys

OLD = '''  nslookup_output=$(nslookup -debug $DDNS_DOMAIN 2>/dev/null || true)
  ddns_ip=$(echo "$nslookup_output" | awk '/^Address: / { print $2 }')'''
NEW = '''  nslookup_output=$(nslookup -type=A "$DDNS_DOMAIN" 2>/dev/null || true)
  ddns_ip=$(echo "$nslookup_output" | awk '/^Name:/{found=1} found && /^Address:/{print $2; exit}' | sed 's/#.*//')'''
UPSTREAM_QUERY = '  nslookup_output=$(nslookup -type=A $DDNS_DOMAIN 2>/dev/null || true)'
SAFE_QUERY, SAFE_PARSE = NEW.split('\n')


def patch(path):
    with open(path) as stream:
        text = stream.read()
    if NEW in text:
        return
    if text.count(SAFE_QUERY) == 1 and text.count(SAFE_PARSE) == 1:
        return
    if text.count(UPSTREAM_QUERY) == 1 and text.count(SAFE_PARSE) == 1:
        changed = text.replace(UPSTREAM_QUERY, SAFE_QUERY, 1)
    elif text.count(OLD) == 1:
        changed = text.replace(OLD, NEW, 1)
    else:
        raise ValueError('Unsupported DDNS monitor script: IPv4 parsing cannot be safely patched')
    tmp = path + '.dnf-admin.tmp'
    with open(tmp, 'w') as stream:
        stream.write(changed)
    os.chmod(tmp, os.stat(path).st_mode & 0o777)
    os.rename(tmp, path)
    print('DDNS monitor: preserve IPv4 answer parsing')


def install_default_wrapper(path, data='/data'):
    with open(path) as stream:
        original = stream.read()
    if '# DNF_WEB_DEFAULT_ADDRESS_WRAPPER' in original:
        if not os.path.isfile(path + '.before-web-default'):
            raise ValueError('DNF address monitor is missing its original backup')
        return
    if 'MONITOR_PUBLIC_IP' not in original or 'DDNS_ENABLE' not in original:
        raise ValueError('Unsupported DNF address monitor')
    backup = path + '.before-web-default'
    if os.path.exists(backup):
        with open(backup) as stream:
            if stream.read() != original:
                raise ValueError('DNF address monitor differs from its existing backup')
    else:
        shutil.copy2(path, backup)
    config = pipes.quote(os.path.join(data, 'channel-manager/default-address.properties'))
    last = pipes.quote(os.path.join(data, 'channel-manager/default-address-ip'))
    ip = pipes.quote(os.path.join(data, 'monitor_ip/MONITOR_PUBLIC_IP'))
    content = '''#!/bin/bash
# DNF_WEB_DEFAULT_ADDRESS_WRAPPER
set -e
if [ -f %(config)s ]; then
  address=$(cat %(last)s 2>/dev/null || true)
  if [[ ! "$address" =~ ^[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+$ ]]; then echo 'Managed default address unavailable' >&2; exit 1; fi
  printf '%%s\\n' "$address" > %(ip)s.tmp
  mv -f %(ip)s.tmp %(ip)s
  while [ -f %(config)s ]; do sleep 5; done
fi
exec %(backup)s "$@"
''' % {'config': config, 'last': last, 'ip': ip, 'backup': pipes.quote(backup)}
    tmp = path + '.dnf-admin.tmp'
    with open(tmp, 'w') as stream:
        stream.write(content)
    os.chmod(tmp, os.stat(path).st_mode & 0o777)
    os.rename(tmp, path)
    print('DNF default-address monitor adapter installed')


if __name__ == '__main__':
    try:
        patch('/home/template/init/monitor_ip/monitor_ip.sh')
        install_default_wrapper('/home/template/init/monitor_ip/monitor_ip.sh')
    except (IOError, ValueError) as exc:
        sys.exit(str(exc))
