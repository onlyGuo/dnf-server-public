#!/usr/bin/env python3
"""Create the user's local DNF directory layout without changing existing data."""
from pathlib import Path
import shlex
import sys

root = Path(sys.argv[1]).resolve()
data = root / 'data'
game = root / 'home/neople/game'
for path in (data / 'run', data / 'conf.d', data / 'log', data / 'monitor_ip',
             data / 'channel-manager/channels', data / 'channel-manager/ips',
             game / 'cfg', root / 'run', root / 'etc/conf.d', root / 'usr/local/bin'):
    path.mkdir(parents=True, exist_ok=True)

def create(path, content, mode=0o644):
    if not path.exists():
        path.write_text(content)
        path.chmod(mode)

def conf_path(path):
    return str(path).replace('%', '%%').replace('\n', '')

create(data / '.dnf-local-simulator', 'Local IDE simulation; game processes are not real.\n')
create(data / 'monitor_ip/MONITOR_PUBLIC_IP', '127.0.0.1\n')
create(data / 'channel-manager/initialized', '1\n')
game_script = '''#!/bin/bash
set -eu
channel_no=$1
channel_name="siroco$channel_no"
ip=$(cat {monitor})
# DNF_CHANNEL_MANAGER_IP_OVERRIDE
override=$(cat {ipdir}/"$channel_no" 2>/dev/null || true)
if [[ "$override" =~ ^[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+$ ]]; then ip=$override; fi
printf 'ip = %s\\n' "$ip" > {cfg}/"$channel_name.cfg"
cd {game}
exec ./df_game_r "$channel_name" start
'''.format(monitor=shlex.quote(str(data / 'monitor_ip/MONITOR_PUBLIC_IP')),
           ipdir=shlex.quote(str(data / 'channel-manager/ips')),
           cfg=shlex.quote(str(game / 'cfg')), game=shlex.quote(str(game)))
create(data / 'run/start_game.sh', game_script, 0o755)
for number in (53, 54):
    name = 'channel_admin_%02d' % number
    create(data / ('channel-manager/channels/%02d.properties' % number),
           'mode=INHERIT\ntarget=\nenabled=true\ntype=\n')
    create(data / ('channel-manager/ips/%02d' % number), '127.0.0.1\n')
    create(data / ('conf.d/channel-admin-%02d.conf' % number),
           '[program:{name}]\ncommand=/bin/bash {start} {number:02d} 5\n'
           'directory={game}\nautostart=true\nautorestart=true\nstopasgroup=true\nkillasgroup=true\n'
           'stdout_logfile={log}\nredirect_stderr=true\nstdout_logfile_maxbytes=1MB\n'.format(
               name=name, start=shlex.quote(conf_path(data / 'run/start_game.sh')),
               number=number, game=conf_path(game), log=conf_path(data / ('log/' + name + '.log'))))

create(root / 'etc/conf.d/channel.conf',
       '[program:channel]\ncommand=/bin/sleep 86400\nautostart=true\nautorestart=true\n'
       'stdout_logfile={log}\n[group:dnf_channel]\nprograms=channel\npriority=999\n'.format(
           log=conf_path(data / 'log/channel.log')))
create(root / 'etc/supervisord.conf',
       '[unix_http_server]\nfile={socket}\nchmod=0700\n'
       '[supervisord]\nlogfile={log}\npidfile={pid}\nchildlogdir={child}\n'
       '[rpcinterface:supervisor]\nsupervisor.rpcinterface_factory=supervisor.rpcinterface:make_main_rpcinterface\n'
       '[supervisorctl]\nserverurl=unix://{socket}\n[include]\nfiles={system} {dynamic}\n'.format(
           socket=conf_path(root / 'run/supervisor.sock'), log=conf_path(data / 'log/supervisord.log'),
           pid=conf_path(root / 'run/supervisord.pid'), child=conf_path(data / 'log'),
           system=conf_path(root / 'etc/conf.d/*.conf'), dynamic=conf_path(data / 'conf.d/*.conf')))
ctl = '''#!/bin/sh
root=$(CDPATH= cd -- "$(dirname "$0")/../../.." && pwd)
PYTHONPATH="$root/usr/lib/python${PYTHONPATH:+:$PYTHONPATH}"; export PYTHONPATH
exec python3 -m supervisor.supervisorctl -c "$root/etc/supervisord.conf" "$@"
'''
create(root / 'usr/local/bin/supervisorctl', ctl, 0o755)
print('Local IDE DNF directories prepared:', root)
