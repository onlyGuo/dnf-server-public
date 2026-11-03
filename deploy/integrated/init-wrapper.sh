#!/bin/bash
set -e
bash /home/template/init/init.sh.upstream "$@"
/usr/bin/env python /opt/dnf-admin/prepare_channels.py
