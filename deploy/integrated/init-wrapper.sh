#!/bin/bash
set -e
bash /home/template/init/init.sh.upstream "$@"
/usr/bin/env python /opt/dnf-admin/prepare_channels.py
/usr/bin/env python /opt/dnf-admin/online-mail/prepare.py || echo 'Online mail notifications could not be prepared; game startup continues'
