#!/bin/bash
# Linux Docker-host integration test. All databases and processes are disposable.
set -euo pipefail
image=${1:?image tag required}
suffix=$$
network="dnfqa-net-$suffix"
db="dnfqa-db-$suffix"
app="dnfqa-app-$suffix"
work=$(mktemp -d /tmp/dnf-api-qa.XXXXXX)
cleanup() {
  docker rm -fv "$app" "$db" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
  rm -rf "$work"
}
trap cleanup EXIT
mkdir -p "$work/data/monitor_ip" "$work/data/qa-status" "$work/data/run"
printf '203.0.113.5\n' > "$work/data/monitor_ip/MONITOR_PUBLIC_IP"
printf '#!/bin/bash\n# DNF_CHANNEL_MANAGER_IP_OVERRIDE\n' > "$work/data/run/start_game.sh"
cat > "$work/data/game-stub.c" <<'STUB'
#include <unistd.h>
int main(void) { for (;;) pause(); return 0; }
STUB
cat > "$work/supervisorctl" <<'SUPERVISOR'
#!/bin/bash
set -eu
cmd=$1; name=${2:-}
state="/data/qa-status/$name"
if [ "$cmd" = status ]; then
  if [ -z "$name" ]; then echo 'dnf_channel:channel RUNNING'; exit 0; fi
  if [ -f "$state" ]; then printf '%s %s\n' "$name" "$(cat "$state")"; exit 0; fi
  printf '%s: ERROR (no such process)\n' "$name"; exit 3
fi
printf '%s %s\n' "$cmd" "$name" >> /data/qa-operations.log
case "$cmd" in
  add) number=${name##*_}; if grep -q 'autostart=true' "/data/conf.d/channel-admin-$number.conf"; then echo RUNNING > "$state"; else echo STOPPED > "$state"; fi ;;
  start|restart) echo RUNNING > "$state" ;;
  stop) echo STOPPED > "$state" ;;
esac
SUPERVISOR
chmod 755 "$work/supervisorctl"
resource_mount=()
java_options=(-Xms64m -Xmx256m)
publish=()
if [[ -n "${DNF_QA_RESOURCE_DIR:-}" ]]; then
  [[ -d "$DNF_QA_RESOURCE_DIR" ]] || { echo 'QA resource directory does not exist' >&2; exit 1; }
  resource_mount=(-v "$DNF_QA_RESOURCE_DIR:/opt/dnf-admin/data:ro")
  java_options=(-Xms128m -Xmx1536m)
else
  mkdir -p "$work/keys"
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$work/keys/private.key" 2>/dev/null
  resource_mount=(-v "$work/keys:/opt/dnf-admin/data:ro")
fi
if [[ -n "${DNF_QA_EXPOSE_PORT:-}" ]]; then
  [[ "$DNF_QA_EXPOSE_PORT" =~ ^[1-9][0-9]{0,4}$ && "$DNF_QA_EXPOSE_PORT" -le 65535 ]] || { echo 'Invalid QA port' >&2; exit 1; }
  docker network create "$network" >/dev/null
  publish=(-p "127.0.0.1:$DNF_QA_EXPOSE_PORT:9001")
else
  docker network create --internal "$network" >/dev/null
fi
docker run -d --name "$db" --network "$network" --network-alias qa-db \
  -e MYSQL_ROOT_PASSWORD=dnf-local-qa -e MYSQL_ROOT_HOST=% mysql:8.0 \
  --default-authentication-plugin=mysql_native_password --innodb-buffer-pool-size=64M --max-connections=40 --sql-mode= >/dev/null
for i in {1..120}; do
  if docker exec -e MYSQL_PWD=dnf-local-qa "$db" mysqladmin -uroot -h127.0.0.1 ping --silent >/dev/null 2>&1; then break; fi
  if ! docker inspect --format '{{.State.Running}}' "$db" | grep -qx true; then docker logs --tail 50 "$db"; exit 1; fi
  sleep 1
done
docker exec -i -e MYSQL_PWD=dnf-local-qa "$db" mysql -uroot -h127.0.0.1 <<'SQL'
CREATE DATABASE dnf_service;
CREATE DATABASE d_taiwan;
CREATE DATABASE taiwan_cain;
CREATE TABLE d_taiwan.accounts (UID int NOT NULL AUTO_INCREMENT PRIMARY KEY, accountname varchar(255) NOT NULL, password varchar(255) NOT NULL, qq varchar(255), dzuid int DEFAULT NULL, billing int DEFAULT 0, VIP varchar(255) NOT NULL, admin int DEFAULT NULL, parent_uid int DEFAULT NULL, isRobot tinyint DEFAULT 0);
INSERT INTO d_taiwan.accounts(accountname,password,VIP,admin,parent_uid) VALUES ('qa-admin',MD5('qa-only-password'),'',1,0),('qa-gm',MD5('qa-only-password'),'',0,0),('qa-player',MD5('qa-only-password'),'',0,1);
CREATE TABLE d_taiwan.limit_create_character (id int);
CREATE TABLE taiwan_cain.game_channel (gc_no int PRIMARY KEY, gc_type tinyint NOT NULL DEFAULT 0);
INSERT INTO taiwan_cain.game_channel VALUES (3053,0),(3054,0);
SQL
# Run the application only, with a disposable DB and a fake Supervisor.
docker run -d --name "$app" --network "$network" \
  "${publish[@]}" \
  -v "$work/data:/data" -v "$work/supervisorctl:/usr/local/bin/supervisorctl:ro" \
  "${resource_mount[@]}" \
  -w /opt/dnf-admin -e DNF_INTEGRATED=true -e DNF_CHANNELS_ENABLED=true \
  -e DNF_DATA_DIR=/data -e SERVER_GROUP=3 -e SERVER_GROUP_DB=cain \
  -e DNF_CHANNEL_DDNS_INTERVAL_MS=2000 \
  -e 'SPRING_DATASOURCE_URL=jdbc:mysql://qa-db:3306/dnf_service?useUnicode=true&characterEncoding=utf8&autoReconnect=true&useSSL=false&serverTimezone=UTC' \
  -e SPRING_DATASOURCE_USERNAME=root -e SPRING_DATASOURCE_PASSWORD=dnf-local-qa \
  --entrypoint /opt/dnf-admin/jre/bin/java "$image" "${java_options[@]}" -jar /opt/dnf-admin/app.jar >/dev/null
for i in {1..120}; do
  if docker exec "$app" curl -fsS http://127.0.0.1:9001/login >/dev/null 2>&1; then break; fi
  if ! docker inspect --format '{{.State.Running}}' "$app" | grep -qx true; then docker logs --tail 80 "$app"; exit 1; fi
  sleep 1
done
if ! docker exec "$app" curl -fsS http://127.0.0.1:9001/login >/dev/null; then docker logs --tail 80 "$app"; exit 1; fi
docker exec "$app" gcc -O2 /data/game-stub.c -o /data/df_game_r
docker exec "$app" mkdir -p /home/neople/game
docker exec "$app" cp /data/df_game_r /home/neople/game/df_game_r
docker exec -i "$app" python - <<'PY'
from __future__ import print_function
import json, re, subprocess, time, urllib, urllib2
base='http://127.0.0.1:9001'
work=''

def request(method,path,body=None,token=None):
    headers={}
    if body is not None: headers['Content-Type']='application/json'; body=json.dumps(body).encode()
    if token: headers['Authorization']='Bearer '+token
    req=urllib2.Request(base+path,data=body,headers=headers)
    req.get_method=lambda: method
    try:
        response=urllib2.urlopen(req,timeout=60)
        return response.code,response.read().decode('utf-8')
    except urllib2.HTTPError as error: return error.code,error.read().decode('utf-8')

def login(name):
    status, body=request('POST','/api/v1/login',{'account':name,'password':'qa-only-password'})
    if status != 200: raise AssertionError('login failed: %s %s' % (status,body[:300]))
    return json.loads(body) if body.startswith('"') else body.strip()

def save(token,number,enabled,mode,target,kind=None):
    status,body=request('PUT','/api/v1/admin/channels/%s'%number,{'enabled':enabled,'mode':mode,'target':target,'channelType':kind},token)
    if status != 200: raise AssertionError('save %s failed: %s %s' % (number,status,body[:400]))
    return json.loads(body)

page=urllib2.urlopen(base+'/login',timeout=30).read().decode('utf-8')
match=re.search(r'<script[^>]+src="([^"]+\.js)"',page)
assert match, 'SPA entry script not found'
asset=urllib2.urlopen(base+match.group(1),timeout=30)
assert 'javascript' in asset.headers.get('Content-Type','').lower(), 'SPA script is not served as JavaScript'
assert not asset.read(128).lstrip().startswith('<!doctype'), 'SPA script returned HTML'
assert request('GET','/api/v1/admin/channels')[0] == 401
for name in ('qa-admin', 'qa-player'):
    status,body=request('POST','/api/v1/client/login',{'accountname':name,'password':'qa-only-password'})
    assert status == 200 and len(json.loads(body).get('token','')) > 64, 'client login failed for %s: %s %s' % (name,status,body[:300])
    query=urllib.urlencode({'accountname':name,'password':'qa-only-password'})
    status,body=request('GET','/client/login?'+query)
    assert status == 200 and len(body) > 64 and 'message' not in body, 'legacy client login failed for %s: %s %s' % (name,status,body[:300])
admin=login('qa-admin'); gm=login('qa-gm')
assert request('GET','/api/v1/admin/channels',token=gm)[0] == 403
status,body=request('GET','/api/v1/admin/channels',token=admin)
assert status==200 and json.loads(body)['available'] is True
created=save(admin,53,True,'STATIC','198.51.100.4',8)
assert created['actualType']==8
assert created['listening'] is False, 'a Supervisor process without a game socket must not be marked ready'
save(admin,54,True,'STATIC','198.51.100.5',None)
log=work+'/data/qa-operations.log'
open(log,'w').close()
save(admin,53,True,'STATIC','198.51.100.8',8)
assert open(log).read().splitlines()==['restart channel_admin_53']
open(log,'w').close()
game53=subprocess.Popen(['/data/df_game_r','siroco53','start'])
game54=subprocess.Popen(['/data/df_game_r','siroco54','start'])
time.sleep(0.3)
save(admin,53,False,'STATIC','198.51.100.8',8)
assert open(log).read().splitlines()==['stop channel_admin_53']
for _ in range(30):
    if game53.poll() is not None: break
    time.sleep(0.1)
else: raise AssertionError('detached channel 53 game process was not stopped')
assert game54.poll() is None, 'another channel game process was stopped'
game54.terminate(); game54.wait()
save(admin,53,True,'DDNS','localhost',8)
assert open(work+'/data/channel-manager/ips/53').read().strip()=='127.0.0.1'
open(log,'w').close()
with open(work+'/data/channel-manager/ips/53','w') as f: f.write('127.0.0.2\n')
for _ in range(30):
    if open(work+'/data/channel-manager/ips/53').read().strip()=='127.0.0.1': break
    time.sleep(1)
else: raise AssertionError('DDNS change not applied')
assert open(log).read().splitlines()==['restart channel_admin_53']
print('PASS: admin and player client login, SPA, permissions, channel operations and isolated DDNS restart')
PY
if [[ -n "${DNF_QA_HOLD_SECONDS:-}" ]]; then
  [[ "$DNF_QA_HOLD_SECONDS" =~ ^[1-9][0-9]{0,2}$ ]] || { echo 'Invalid QA hold duration' >&2; exit 1; }
  echo "QA application remains available for $DNF_QA_HOLD_SECONDS seconds"
  sleep "$DNF_QA_HOLD_SECONDS"
fi
