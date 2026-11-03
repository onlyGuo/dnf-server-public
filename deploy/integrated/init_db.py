#!/usr/bin/env python
# Generate only additive schema changes. Never execute DROP or seed INSERT from init.sql.
from __future__ import print_function
import os
import re
import subprocess
import sys

env = os.environ.copy()
env['MYSQL_PWD'] = env.get('DNF_DB_INIT_PASSWORD', env.get('DNF_DB_ROOT_PASSWORD', ''))
host = env.get('DNF_DB_INIT_HOST', '127.0.0.1')
port = env.get('DNF_DB_INIT_PORT', '3306')
user = env.get('DNF_DB_INIT_USER', 'root')
database = env.get('DNF_DB_INIT_NAME', 'dnf_service')
if not re.match(r'^[A-Za-z0-9_]+$', database) or not re.match(r'^[0-9]+$', port):
    sys.exit('Invalid database name or port')
cmd = ['mysql', '-N', '-B', '-u' + user, '-h' + host, '-P' + port, '--protocol=tcp']

def query(sql):
    output = subprocess.check_output(cmd + ['-e', sql], env=env)
    if not isinstance(output, str):
        output = output.decode('utf-8')
    return output.strip()

query('SELECT 1')
columns = [('admin', 'INT(10) NULL'), ('parent_uid', 'INT(10) NULL'), ('isRobot', 'TINYINT(3) DEFAULT 0')]
for name, definition in columns:
    sql = "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='d_taiwan' AND TABLE_NAME='accounts' AND COLUMN_NAME='%s'" % name
    if query(sql) == '0':
        print('ALTER TABLE `d_taiwan`.`accounts` ADD COLUMN `%s` %s;' % (name, definition))
with open(env.get('DNF_INIT_SQL', '/opt/dnf-admin/init.sql')) as f:
    source = f.read()
tables = re.findall(r'CREATE TABLE\s+`[^`]+`\s*\(.*?\)\s*ENGINE\s*=.*?;', source, re.I | re.S)
if not tables:
    sys.exit('No table definitions found in init.sql')
print('CREATE DATABASE IF NOT EXISTS `%s` DEFAULT CHARACTER SET utf8;' % database)
print('USE `%s`;' % database)
for table in tables:
    print(re.sub(r'^CREATE TABLE', 'CREATE TABLE IF NOT EXISTS', table, count=1, flags=re.I))
