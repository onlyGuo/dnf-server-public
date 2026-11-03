#!/bin/bash
# Linux/root integration test. Creates an isolated network namespace and a
# temporary cgroup; the real host firewall and DNF container are untouched.
set -euo pipefail
project=${1:?pass the project source directory}
work=$(mktemp -d /tmp/dnf-guard-qa.XXXXXX)
suffix=$$
ns="dnfq${suffix}"
hostif="dh${suffix}"
peerif="dp${suffix}"
cgroup="/sys/fs/cgroup/dnf-guard-qa-${suffix}"
launcher=
cleanup() {
  if [[ -n "$launcher" ]]; then kill "$launcher" 2>/dev/null || true; wait "$launcher" 2>/dev/null || true; fi
  ip netns del "$ns" 2>/dev/null || true
  ip link del "$hostif" 2>/dev/null || true
  rmdir "$cgroup" 2>/dev/null || true
  rm -rf "$work"
}
trap cleanup EXIT
mkdir "$cgroup"
ip netns add "$ns"
ip link add "$hostif" type veth peer name "$peerif"
ip link set "$peerif" netns "$ns"
ip addr add 198.18.241.1/29 dev "$hostif"
ip addr add 198.18.241.3/29 dev "$hostif"
ip link set "$hostif" up
ip -n "$ns" link set lo up
ip -n "$ns" addr add 198.18.241.2/29 dev "$peerif"
ip -n "$ns" link set "$peerif" up
mkdir "$work/mock" "$work/data"
cat > "$work/server.py" <<'PY'
import os, select, socket
sockets = []
for port in (8003, 3000, 30011, 4000):
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind(('0.0.0.0', port)); s.listen(20); sockets.append(s)
for port in (31011, 40403, 49000):
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.bind(('0.0.0.0', port)); sockets.append(s)
open(os.environ['GUARD_TEST_WORK'] + '/ready', 'w').close()
while True:
    ready, _, _ = select.select(sockets, [], [], 5)
    for s in ready:
        if s.type == socket.SOCK_STREAM:
            c, _ = s.accept(); c.sendall(b'ok'); c.close()
        else:
            message, peer = s.recvfrom(1024); s.sendto(b'ok', peer)
PY
export GUARD_TEST_WORK="$work" GUARD_TEST_CGROUP="$cgroup" GUARD_TEST_NS="$ns"
bash -ec 'echo $$ > "$GUARD_TEST_CGROUP/cgroup.procs"; echo $$ > "$GUARD_TEST_WORK/pid"; exec ip netns exec "$GUARD_TEST_NS" python3 -u "$GUARD_TEST_WORK/server.py"' > "$work/server.log" 2>&1 &
launcher=$!
for i in {1..50}; do
  [[ -f "$work/ready" ]] && break
  if ! kill -0 "$launcher" 2>/dev/null; then cat "$work/server.log"; exit 1; fi
  sleep 0.1
done
[[ -f "$work/ready" ]]
export GUARD_TEST_PID="$(cat "$work/pid")" GUARD_TEST_NS="$ns"
printf 'Isolated test process cgroup: '
cat "/proc/$GUARD_TEST_PID/cgroup"
cat > "$work/mock/docker" <<'EOF_DOCKER'
#!/bin/bash
case "$*" in
 *State.Running*) echo true ;;
 *HostConfig.NetworkMode*) echo host ;;
 *Config.Env*) printf 'DNF_HOST_NETWORK=true\nSERVER_GROUP=3\nDNF_ADMIN_PORT=8003\n' ;;
 *State.Pid*) echo "$GUARD_TEST_PID" ;;
 *Mounts*) echo "$GUARD_TEST_WORK/data" ;;
 *) exit 2 ;;
esac
EOF_DOCKER
cat > "$work/mock/nft" <<'EOF_NFT'
#!/bin/bash
exec /usr/bin/nsenter -t "$GUARD_TEST_PID" -n /usr/sbin/nft "$@"
EOF_NFT
chmod 755 "$work/mock/docker" "$work/mock/nft"
PATH="$work/mock:$PATH" DNF_CONTAINER_NAME=dnf-test DNF_HOST_GUARD_LAN_IF="$peerif" DNF_HOST_GUARD_LAN_IPV4=198.18.241.1/32 bash "$project/deploy/integrated/host-guard.sh" --once
python3 - <<'PY'
import socket

def tcp(source, port, expected):
    s=socket.socket(); s.settimeout(0.8); s.bind((source,0))
    try:
        s.connect(('198.18.241.2',port)); result=s.recv(2)==b'ok'
    except OSError:
        result=False
    finally: s.close()
    if result != expected: raise SystemExit('TCP %s from %s: expected %s, got %s' % (port,source,expected,result))

def udp(source, port, expected):
    s=socket.socket(socket.AF_INET,socket.SOCK_DGRAM); s.settimeout(0.8); s.bind((source,0))
    try:
        s.sendto(b'ping',('198.18.241.2',port)); result=s.recv(16)==b'ok'
    except OSError:
        result=False
    finally: s.close()
    if result != expected: raise SystemExit('UDP %s from %s: expected %s, got %s' % (port,source,expected,result))

tcp('198.18.241.1',8003,True)
tcp('198.18.241.3',8003,True)
tcp('198.18.241.1',3000,True)
tcp('198.18.241.3',3000,False)
tcp('198.18.241.3',30011,True)
tcp('198.18.241.1',4000,False)
udp('198.18.241.3',31011,True)
udp('198.18.241.3',40403,False)
udp('198.18.241.3',49000,False)
print('PASS: public web and game TCP/UDP, restricted database, internal TCP and fixed/dynamic UDP')
PY
