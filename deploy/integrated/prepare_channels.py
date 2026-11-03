#!/usr/bin/env python
# Runs after the upstream init script and before supervisord. Compatible with CentOS 7's Python 2.
from __future__ import print_function
import glob
import os
import re
import sys

DATA = '/data'
ROOT = DATA + '/channel-manager'
CONF = '/etc/supervisor/conf.d/channel.conf'
START = DATA + '/run/start_game.sh'


def atomic(path, value):
    tmp = path + '.tmp'
    with open(tmp, 'w') as f:
        f.write(value)
    os.rename(tmp, path)


def props(path):
    result = {}
    with open(path) as f:
        for line in f:
            if '=' in line and not line.startswith('#'):
                k, v = line.strip().split('=', 1)
                result[k] = v
    return result


def program(number, active):
    seq = 3 if 11 <= int(number) <= 39 else 5
    name = 'channel_admin_' + number
    return ('[program:%s]\ncommand=/bin/bash -c "/data/run/start_game.sh %s %d"\n'
            'directory=/home/neople/game\nuser=root\nautostart=%s\nautorestart=true\n'
            'stopasgroup=true\nkillasgroup=true\nstdout_logfile=/data/log/%s.log\n'
            'redirect_stderr=true\nstdout_logfile_maxbytes=1MB\nstderr_logfile_maxbytes=1MB\ndepend=channel\n'
            % (name, number, seq, 'true' if active else 'false', name))


def prepare():
    for directory in (ROOT, ROOT + '/channels', ROOT + '/ips', DATA + '/conf.d'):
        if not os.path.isdir(directory):
            os.makedirs(directory)

    with open(CONF) as f:
        original = f.read()
    sections = re.findall(r'^\[(program|group):([^]]+)\]', original, re.M)
    if ('program', 'channel') not in sections or ('group', 'dnf_channel') not in sections:
        sys.exit('Unsupported channel.conf: central channel group is missing')
    if any(not ((kind == 'program' and (name == 'channel' or name.startswith('game_'))) or
               (kind == 'group' and name == 'dnf_channel')) for kind, name in sections):
        sys.exit('Unsupported custom channel.conf: refusing to remove custom programs')
    with open(START) as f:
        start = f.read()
    marker = '# DNF_CHANNEL_MANAGER_IP_OVERRIDE'
    if marker not in start:
        anchor = 'cp /tmp/$channel_name.cfg /home/neople/game/cfg/$channel_name.cfg'
        if start.count(anchor) != 1:
            sys.exit('Unsupported start_game.sh: cannot safely install per-channel IP override')
        injection = '''# DNF_CHANNEL_MANAGER_IP_OVERRIDE
    channel_override=$(cat /data/channel-manager/ips/$channel_no 2>/dev/null || true)
    if [[ "$channel_override" =~ ^[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+$ ]]; then
      sed -i "s/^ip[[:space:]]*=.*/ip = $channel_override/" /tmp/$channel_name.cfg
    fi
    '''
        if not os.path.exists(START + '.before-channel-manager'):
            atomic(START + '.before-channel-manager', start)
        atomic(START, start.replace(anchor, injection + anchor))
        os.chmod(START, 0o755)

    # Import only once. The generated upstream file is authoritative on first install;
    # later OPEN_CHANNEL changes cannot silently re-enable or delete managed channels.
    initialized = ROOT + '/initialized'
    if not os.path.exists(initialized):
        numbers = re.findall(r'start_game\.sh\s+(\d{1,2})\s+\d+', original)
        group = os.environ.get('SERVER_GROUP', '3')
        if not re.match(r'^[1-6]$', group):
            sys.exit('Invalid server group')
        for raw in numbers:
            number = int(raw)
            if not (number in (1, 6, 7) or 11 <= number <= 39 or 52 <= number <= 56):
                sys.exit('Unsupported channel in upstream configuration: %s' % raw)
            channel = '%02d' % number
            path = ROOT + '/channels/' + channel + '.properties'
            if not os.path.exists(path):
                # No override is installed until the operator chooses a type.
                atomic(path, 'mode=INHERIT\ntarget=\nenabled=true\ntype=\n')
        atomic(initialized, '1\n')

    # Keep the central channel service in its original group. Each game channel is a
    # separate Supervisor group, so modifying one never reloads the others.
    head = original.split('[program:game_', 1)[0].split('[group:dnf_channel]', 1)[0]
    if '[program:channel]' not in head:
        sys.exit('Unsupported channel.conf: central channel program is missing')
    atomic(CONF, head.rstrip() + '\n\n[group:dnf_channel]\nprograms=channel\npriority=999\n')
    for path in glob.glob(ROOT + '/channels/[0-9][0-9].properties'):
        number = os.path.basename(path)[:2]
        active = props(path).get('enabled', 'false').lower() == 'true'
        atomic(DATA + '/conf.d/channel-admin-' + number + '.conf', program(number, active))
    print('Channel Manager: independent Supervisor groups prepared')


if __name__ == "__main__":
    prepare()
