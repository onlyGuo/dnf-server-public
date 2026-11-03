#!/bin/bash
# Run on the DNF host after deployment; only temporary network objects are created.
set -euo pipefail
suffix=$$
ns="dnfa${suffix}"
hostif="ah${suffix}"
peerif="ap${suffix}"
cleanup() { ip netns del "$ns" 2>/dev/null || true; ip link del "$hostif" 2>/dev/null || true; }
trap cleanup EXIT
ip netns add "$ns"
ip link add "$hostif" type veth peer name "$peerif"
ip link set "$peerif" netns "$ns"
ip addr add 198.18.242.1/29 dev "$hostif"
ip link set "$hostif" up
ip -n "$ns" link set lo up
ip -n "$ns" addr add 198.18.242.2/29 dev "$peerif"
ip -n "$ns" link set "$peerif" up
ip netns exec "$ns" python3 - <<'PY'
import http.client, json, socket

def check(port, expected):
    s=socket.socket(); s.settimeout(1.2)
    try:
        s.connect(('198.18.242.1',port)); actual=True
    except OSError:
        actual=False
    finally: s.close()
    if actual != expected: raise SystemExit('port %s expected reachable=%s, got %s' % (port,expected,actual))
    print('port %s %s as expected' % (port,'reachable' if actual else 'blocked'))

check(30056,True)
check(8003,True)
check(3000,False)
check(7000,False)

conn=http.client.HTTPConnection('198.18.242.1',8003,timeout=5)
conn.request('GET','/api/v1/admin/channels')
response=conn.getresponse(); response.read()
if response.status != 401: raise SystemExit('unauthenticated admin request was not rejected: %s' % response.status)
conn.close()
conn=http.client.HTTPConnection('198.18.242.1',8003,timeout=5)
body=json.dumps({'accountname':'__network_probe__','password':'invalid'}).encode()
conn.request('POST','/api/v1/client/login',body,{'Content-Type':'application/json'})
response=conn.getresponse(); content=response.read().decode('utf-8')
if response.status in (401,403) or '用户名或密码错误' not in content:
    raise SystemExit('player login did not reach account validation: status=%s' % response.status)
conn.close()
print('PASS: web/login and game are reachable; unauthenticated admin, database and internal bridge are blocked')
PY
