#!/usr/bin/env bash
# Destructive integration tests: create and remove an isolated local database.
set -euo pipefail
project_dir=$(cd "$(dirname "$0")/.." && pwd)
if [ -n "${DNF_TEST_CONTAINER_CLI:-}" ]; then
  engine=$DNF_TEST_CONTAINER_CLI
elif command -v docker >/dev/null 2>&1; then
  engine=docker
elif command -v container >/dev/null 2>&1; then
  engine=container
else
  echo 'Docker or Apple container is required for the disposable test database.' >&2
  exit 1
fi
name="dnf-disposable-test-$$"
marker=$(openssl rand -hex 16)
test_dir=$(mktemp -d "${TMPDIR:-/tmp}/dnf-disposable.XXXXXX")
cleanup() { "$engine" stop "$name" >/dev/null 2>&1 || true; rm -rf "$test_dir"; }
trap cleanup EXIT
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$test_dir/private.key" >/dev/null 2>&1
"$engine" run -d --rm --name "$name" -e MARIADB_ROOT_PASSWORD=dnf-test-only \
  -e MARIADB_ROOT_HOST=% -p 127.0.0.1:19308:3306 mariadb:10.11 \
  --skip-name-resolve --innodb-buffer-pool-size=128M >/dev/null
for attempt in $(seq 1 90); do
  if "$engine" exec "$name" mariadb -uroot -pdnf-test-only -h127.0.0.1 --protocol=tcp -e 'SELECT 1' >/dev/null 2>&1; then break; fi
  if [ "$attempt" -eq 90 ]; then echo 'Test database did not start.' >&2; exit 1; fi
  sleep 1
done
"$engine" exec -i "$name" mariadb -uroot -pdnf-test-only < "$project_dir/tests/disposable-db-fixture.sql"
"$engine" exec "$name" mariadb -uroot -pdnf-test-only -e \
  "INSERT INTO dnf_service.codex_disposable_marker(marker) VALUES ('$marker')"
cd "$project_dir"
DNF_RUN_DISPOSABLE_DB_TEST=true DNF_DISPOSABLE_DB_MARKER="$marker" DNF_PRIVATE_KEY_PATH="$test_dir/private.key" \
SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:19308/dnf_service?useSSL=false' \
SPRING_DATASOURCE_USERNAME=root SPRING_DATASOURCE_PASSWORD=dnf-test-only \
  mvn -Dmaven.test.skip=false -Dtest=DisposableDatabaseIntegrationTest test "$@"
