#!/usr/bin/env python
# Host network: keep MySQL and Supervisor on loopback, and preserve the old
# externally reachable 3000/2000 ports through narrowly scoped proxies.
from __future__ import print_function
import os
import re
import sys


def change_section(text, section, option, value):
    pattern = r'(?m)^\[' + re.escape(section) + r'\][^\n]*\n(?:(?!^\[)[^\n]*(?:\n|$))*'
    match = re.search(pattern, text)
    if not match:
        raise ValueError('Missing config section: ' + section)
    block = match.group(0)
    key = re.compile(r'(?m)^\s*' + re.escape(option) + r'\s*=.*(?:\n|$)')
    block = key.sub('', block)
    first, rest = block.split('\n', 1)
    replacement = first + '\n' + option + '=' + value + '\n' + rest
    return text[:match.start()] + replacement + text[match.end():]


def update(path, transform):
    with open(path) as stream:
        previous = stream.read()
    changed = transform(previous)
    if changed != previous:
        temporary = path + '.dnf-admin.tmp'
        with open(temporary, 'w') as stream:
            stream.write(changed)
        os.chmod(temporary, os.stat(path).st_mode & 0o777)
        os.rename(temporary, path)


def bind_proxy(text, port):
    old = 'TCP-LISTEN:%s,fork,reuseaddr' % port
    new = 'TCP-LISTEN:%s,bind=127.0.0.1,fork,reuseaddr' % port
    if new in text:
        return text
    if text.count(old) != 1:
        raise ValueError('Unsupported DNF MySQL proxy configuration: ' + port)
    return text.replace(old, new)


def configure(mysql_conf, supervisor_conf, main_proxy, group_proxy, supervisor_dir):
    # Validate all inputs before writing anything, to avoid a partially configured startup.
    paths = [mysql_conf, supervisor_conf, main_proxy, group_proxy]
    with open(mysql_conf) as f:
        mysql = change_section(f.read(), 'mysqld', 'bind-address', '127.0.0.1')
    with open(supervisor_conf) as f:
        supervisor = change_section(f.read(), 'inet_http_server', 'port', '127.0.0.1:180')
    with open(main_proxy) as f:
        main = bind_proxy(f.read(), '3307')
    with open(group_proxy) as f:
        group = bind_proxy(f.read(), '3306')
    for path, value in zip(paths, [mysql, supervisor, main, group]):
        update(path, lambda old, value=value: value)
    path = os.path.join(supervisor_dir, 'dnf-admin-host-aliases.conf')
    content = '''[program:dnf_admin_mysql_alias]
command=/usr/local/bin/socat TCP-LISTEN:3000,fork,reuseaddr TCP:127.0.0.1:3306
user=root
autostart=true
autorestart=true
stdout_logfile=/data/log/dnf_admin_mysql_alias.log
redirect_stderr=true

[program:dnf_admin_supervisor_alias]
command=/usr/local/bin/socat TCP-LISTEN:2000,fork,reuseaddr TCP:127.0.0.1:180
user=root
autostart=true
autorestart=true
stdout_logfile=/data/log/dnf_admin_supervisor_alias.log
redirect_stderr=true
'''
    with open(path, 'w') as f:
        f.write(content)
    print('Host network: loopback database/Supervisor and compatibility ports configured')


if __name__ == '__main__':
    try:
        configure('/etc/my.cnf', '/etc/supervisord.conf',
                  '/home/template/init/mysql_proxy/start_main_mysql_proxy.sh',
                  '/home/template/init/mysql_proxy/start_mysql_proxy.sh',
                  '/etc/supervisor/conf.d')
    except (IOError, ValueError) as exc:
        sys.exit('Cannot safely configure host network: %s' % exc)
