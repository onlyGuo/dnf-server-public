#!/usr/bin/env python
# Enable the fixed-version notifier without replacing existing channel configuration.
from __future__ import print_function
import os
import shutil
import sys

START = '/data/run/start_game.sh'
ASSETS = os.path.dirname(os.path.abspath(__file__))
INSTALL = '/data/channel-manager/online-mail'
MARKER = '# DNF_ONLINE_MAIL_NOTIFY'
ANCHOR = 'LD_PRELOAD=/dp2/libhook.so ./df_game_r $channel_name start'

REPLACEMENT = '''# DNF_ONLINE_MAIL_NOTIFY
dnf_mail_lib=/data/channel-manager/online-mail/mail_notify.so
dnf_mail_hashes=/data/channel-manager/online-mail/supported-sha256.txt
dnf_mail_hash=$(sha256sum ./df_game_r 2>/dev/null | awk '{print $1}')
if [ -r "$dnf_mail_lib" ] && [ -r "$dnf_mail_hashes" ] && [ -n "$dnf_mail_hash" ] && grep -Fxq "$dnf_mail_hash" "$dnf_mail_hashes"; then
  DNF_MAIL_NOTIFY_SUPPORTED=1 DNF_MAIL_NOTIFY_DIR=/data/channel-manager/mail-notify LD_PRELOAD="$dnf_mail_lib:/dp2/libhook.so" ./df_game_r "$channel_name" start
else
  LD_PRELOAD=/dp2/libhook.so ./df_game_r "$channel_name" start
fi'''


def install_assets(source=ASSETS, destination=INSTALL):
    if not os.path.isdir(destination):
        os.makedirs(destination)
    for name in ('mail_notify.so', 'supported-sha256.txt'):
        src = os.path.join(source, name)
        dst = os.path.join(destination, name)
        if os.path.exists(dst):
            with open(src, 'rb') as left, open(dst, 'rb') as right:
                if left.read() == right.read():
                    continue
            backup = dst + '.before-update'
            if not os.path.exists(backup):
                shutil.copy2(dst, backup)
        temporary = dst + '.tmp'
        shutil.copy2(src, temporary)
        os.rename(temporary, dst)


def prepare(path=START):
    if not os.path.isfile(path):
        print('Online mail: game startup script not found; notifications remain unavailable')
        return False
    with open(path) as stream:
        original = stream.read()
    if MARKER in original:
        return True
    if original.count(ANCHOR) != 1:
        print('Online mail: unsupported custom startup script; notifications remain unavailable')
        return False
    backup = path + '.before-online-mail'
    if not os.path.exists(backup):
        with open(backup, 'w') as stream:
            stream.write(original)
        os.chmod(backup, os.stat(path).st_mode & 0o777)
    temporary = path + '.online-mail.tmp'
    with open(temporary, 'w') as stream:
        stream.write(original.replace(ANCHOR, REPLACEMENT))
    os.chmod(temporary, os.stat(path).st_mode & 0o777)
    os.rename(temporary, path)
    print('Online mail: channel startup prepared')
    return True


if __name__ == '__main__':
    install_assets()
    prepare(sys.argv[1] if len(sys.argv) > 1 else START)
