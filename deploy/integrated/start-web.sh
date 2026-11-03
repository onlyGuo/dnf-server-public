#!/bin/bash
set -e
cd /opt/dnf-admin
mkdir -p /data/dnf-admin
ln -sfn /data/dnf-admin data
if [ -d /data/ImagePacks2 ] && [ ! -e data/ImagePacks2 ]; then
  ln -s /data/ImagePacks2 data/ImagePacks2
fi
# Existing Web and game keys are preserved; the key manager updates them together.
export DNF_DATA_DIR=/data
export SERVER_PORT=${DNF_ADMIN_PORT:-9001}
export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:mysql://127.0.0.1:3306/dnf_service?useUnicode=true&characterEncoding=utf8&autoReconnect=true}"
export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-root}"
export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-$DNF_DB_ROOT_PASSWORD}"
# DNF initializes MySQL asynchronously. Retry without modifying existing rows.
for i in $(seq 1 90); do
  if MYSQL_PWD="$DNF_DB_ROOT_PASSWORD" mysql -N -B -uroot -h127.0.0.1 -P3306 --protocol=tcp -e 'SELECT 1' >/dev/null 2>&1; then break; fi
  sleep 2
done
export MYSQL_PWD="$DNF_DB_ROOT_PASSWORD"
python /opt/dnf-admin/init_db.py > /tmp/dnf-admin-init.sql
mysql -uroot -h127.0.0.1 -P3306 --protocol=tcp < /tmp/dnf-admin-init.sql
rm -f /tmp/dnf-admin-init.sql
unset MYSQL_PWD
# The existing PVF/NPK catalogue can need much more than 256 MiB. Keep the
# JVM's normal sizing unless an operator explicitly supplies a limit.
if [ -n "${DNF_ADMIN_JAVA_OPTS:-}" ]; then
  read -r -a java_options <<< "$DNF_ADMIN_JAVA_OPTS"
  exec /opt/dnf-admin/jre/bin/java "${java_options[@]}" -jar /opt/dnf-admin/app.jar
fi
exec /opt/dnf-admin/jre/bin/java -jar /opt/dnf-admin/app.jar
