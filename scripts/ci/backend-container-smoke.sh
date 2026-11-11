#!/usr/bin/env bash
# Backend 컨테이너 실행 검증 (#45, docs/deployment.md §13).
#
# 실제 backend 이미지를 임시 PostgreSQL 16 컨테이너·임시 볼륨과 함께 실행해 다음을 확인한다.
#   이미지 Java·pg_dump 버전 / root 소유 볼륨 연결 / 기동·Flyway·health / 비root 실행 /
#   볼륨 쓰기·읽기(API 업로드·다운로드 포함) / 백업·복원(docs §7) / 재시작 후 데이터 유지
#
# 운영 기본값(prod 프로파일, Secure 쿠키)을 그대로 쓴다. 쿠키 관련 완화 변수는 넣지 않는다.
# 만드는 리소스는 이 실행 전용 이름(docflow-smoke-*)이며 종료 시 모두 삭제한다. 기존 DB·볼륨에는 접근하지 않는다.
#
# 사용법(저장소 루트에서): scripts/ci/backend-container-smoke.sh <backend-image>
set -euo pipefail

IMAGE="${1:?usage: $0 <backend-image>}"
MIGRATION_DIR="backend/src/main/resources/db/migration"
EXPECTED_JAVA_MAJOR="25"
EXPECTED_PG_MAJOR="16"
STARTUP_TIMEOUT_SECONDS="${SMOKE_STARTUP_TIMEOUT_SECONDS:-180}"
DB_TIMEOUT_SECONDS=60
PORT="${SMOKE_PORT:-18080}"
BASE_URL="http://127.0.0.1:${PORT}"

PREFIX="docflow-smoke-${GITHUB_RUN_ID:-local}-$$"
NET="${PREFIX}-net"
DB="${PREFIX}-db"
APP="${PREFIX}-backend"
VOL="${PREFIX}-data"

# 이 실행에서만 쓰는 일회성 값. 실제 환경의 비밀정보가 아니다.
DB_PASSWORD="smoke-$(openssl rand -hex 12)"
BOOTSTRAP_CODE="Smoke-$(openssl rand -hex 16)"
ADMIN_EMAIL="smoke-admin@example.com"
ADMIN_PASSWORD="Smoke-$(openssl rand -hex 10)"
PROBE_TEXT="probe-$(openssl rand -hex 8)"
if [ -n "${GITHUB_ACTIONS:-}" ]; then
	echo "::add-mask::${DB_PASSWORD}"
	echo "::add-mask::${BOOTSTRAP_CODE}"
	echo "::add-mask::${ADMIN_PASSWORD}"
fi

WORK="$(mktemp -d)"

log() { echo "==> $*"; }
pass() { echo "  [OK] $*"; }
fail() {
	echo "::error::backend container smoke test: $*"
	exit 1
}

cleanup() {
	local status=$?
	if [ "$status" -ne 0 ]; then
		echo "---- backend container logs (tail) ----"
		docker logs "$APP" 2>&1 | tail -n 200 || echo "(no backend logs)"
		echo "---- database container logs (tail) ----"
		docker logs "$DB" 2>&1 | tail -n 50 || echo "(no database logs)"
	fi
	# 이 실행에서 만든 리소스만 지운다. 아직 만들지 않은 리소스는 없다는 메시지만 남기고 넘어간다.
	docker rm -f "$APP" "$DB" >/dev/null 2>&1 || echo "cleanup: containers already absent"
	docker volume rm "$VOL" >/dev/null 2>&1 || echo "cleanup: volume already absent"
	docker network rm "$NET" >/dev/null 2>&1 || echo "cleanup: network already absent"
	rm -rf "$WORK"
	exit "$status"
}
trap cleanup EXIT

# ---------------------------------------------------------------- helpers

# 조건이 참이 될 때까지 2초 간격으로 확인한다. 제한 시간을 넘기면 실패한다(무조건 대기하지 않는다).
wait_until() {
	local description="$1" timeout="$2"
	shift 2
	local deadline=$((SECONDS + timeout))
	until "$@"; do
		if [ "$SECONDS" -ge "$deadline" ]; then
			fail "${description}: not ready within ${timeout}s"
		fi
		sleep 2
	done
}

db_ready() {
	docker exec "$DB" pg_isready -h 127.0.0.1 -U docflow -d docflow >/dev/null 2>&1
}

app_running() {
	[ "$(docker inspect -f '{{.State.Running}}' "$APP")" = "true" ]
}

backend_healthy() {
	app_running || fail "backend container exited before becoming healthy"
	curl -fsS --max-time 5 "${BASE_URL}/actuator/health" 2>/dev/null | jq -e '.status == "UP"' >/dev/null 2>&1
}

psql_value() {
	docker exec "$DB" psql -U docflow -d docflow -tAc "$1"
}

# docker logs | grep -q 는 grep이 먼저 끝나면 SIGPIPE로 pipefail에 걸릴 수 있어 파일로 받아 검사한다.
app_log_has() {
	docker logs "$APP" >"$WORK/app.log" 2>&1
	grep -q "$@" "$WORK/app.log"
}

app_exec() {
	docker exec "$APP" sh -c "$1"
}

# 최소 HTTP 클라이언트. 쿠키는 Secure 속성과 관계없이 직접 보관·전송한다(HTTPS 종단 프록시 뒤 운영 구조와 같은 효과).
declare -A COOKIES=()
HTTP_STATUS=""
http() {
	local method="$1" path="$2"
	shift 2
	local cookie_header="" name
	for name in "${!COOKIES[@]}"; do
		cookie_header+="${name}=${COOKIES[$name]}; "
	done
	local args=(-sS --max-time 30 -X "$method" -D "$WORK/headers" -o "$WORK/body" -w '%{http_code}')
	if [ -n "$cookie_header" ]; then
		args+=(-H "Cookie: ${cookie_header}")
	fi
	if [ -n "${COOKIES[XSRF-TOKEN]:-}" ]; then
		args+=(-H "X-XSRF-TOKEN: ${COOKIES[XSRF-TOKEN]}")
	fi
	HTTP_STATUS="$(curl "${args[@]}" "$@" "${BASE_URL}${path}")"
	local line pair value
	while IFS= read -r line; do
		line="${line%$'\r'}"
		case "${line,,}" in
		set-cookie:*) ;;
		*) continue ;;
		esac
		pair="${line#*:}"
		pair="${pair# }"
		pair="${pair%%;*}"
		name="${pair%%=*}"
		value="${pair#*=}"
		if [ -z "$value" ]; then
			unset "COOKIES[$name]"
		else
			COOKIES[$name]="$value"
		fi
	done <"$WORK/headers"
}

expect_status() {
	local expected="$1" what="$2"
	if [ "$HTTP_STATUS" != "$expected" ]; then
		echo "response body:"
		cat "$WORK/body"
		echo
		fail "${what}: expected HTTP ${expected}, got ${HTTP_STATUS}"
	fi
}

login() {
	COOKIES=()
	http GET /api/auth/csrf
	expect_status 204 "GET /api/auth/csrf"
	http POST /api/auth/login -H 'Content-Type: application/json' \
		--data "$(jq -nc --arg e "$ADMIN_EMAIL" --arg p "$ADMIN_PASSWORD" '{email: $e, password: $p}')"
	expect_status 200 "POST /api/auth/login"
	# 운영 프로파일 세션 쿠키는 Secure여야 한다(#39 정책이 실제 이미지에서도 유지되는지 확인).
	grep -qiE '^set-cookie: JSESSIONID=[^;]+;.*secure' "$WORK/headers" ||
		fail "session cookie is not marked Secure in the prod profile"
	# 로그인 시 CSRF 토큰이 교체되므로 새 토큰을 받는다.
	http GET /api/auth/csrf
	expect_status 204 "GET /api/auth/csrf after login"
}

download_sha() {
	http GET "/api/projects/${PROJECT_ID}/documents/${DOCUMENT_ID}/versions/1/download"
	expect_status 200 "download version 1"
	sha256sum "$WORK/body" | cut -d' ' -f1
}

# 문서 업로드 검증(PdfStructureValidator)을 통과하는 최소 PDF
make_pdf() {
	local head body tail offset
	head=$'%PDF-1.4\n'
	body=$'1 0 obj\n<< /Type /Catalog >>\nendobj\n'
	offset=$(( ${#head} + ${#body} ))
	tail=$'xref\n0 2\n0000000000 65535 f \n0000000009 00000 n \ntrailer\n<< /Size 2 /Root 1 0 R >>\nstartxref\n'"${offset}"$'\n%%EOF\n'
	printf '%s%s%s' "$head" "$body" "$tail" >"$1"
}

# ---------------------------------------------------------------- 1. image versions

log "1. image: ${IMAGE}"
docker image inspect "$IMAGE" >/dev/null || fail "image ${IMAGE} not found (build it first)"
JAVA_VERSION_OUTPUT="$(docker run --rm --entrypoint java "$IMAGE" -version 2>&1)"
echo "$JAVA_VERSION_OUTPUT" | sed 's/^/    /'
grep -qE "version \"${EXPECTED_JAVA_MAJOR}(\.|\")" <<<"$JAVA_VERSION_OUTPUT" ||
	fail "image Java major version is not ${EXPECTED_JAVA_MAJOR}"
pass "image Java ${EXPECTED_JAVA_MAJOR}"
PG_DUMP_VERSION="$(docker run --rm --entrypoint pg_dump "$IMAGE" --version)"
echo "    ${PG_DUMP_VERSION}"
PG_DUMP_MAJOR="$(grep -oE '\) [0-9]+' <<<"$PG_DUMP_VERSION" | grep -oE '[0-9]+' || true)"
[ -n "$PG_DUMP_MAJOR" ] || fail "cannot read the pg_dump major version"
# pg_dump는 자신과 같거나 낮은 주 버전의 서버만 백업할 수 있다. 서버 버전과 비교는 2단계 이후, 실제 백업·복원은 8단계에서 확인한다.
pass "image pg_dump major ${PG_DUMP_MAJOR}"

# ---------------------------------------------------------------- 2. PostgreSQL 16

log "2. PostgreSQL ${EXPECTED_PG_MAJOR} test container"
docker network create "$NET" >/dev/null
docker run -d --name "$DB" --network "$NET" \
	-e POSTGRES_DB=docflow -e POSTGRES_USER=docflow -e POSTGRES_PASSWORD="$DB_PASSWORD" \
	"postgres:${EXPECTED_PG_MAJOR}" >/dev/null
wait_until "PostgreSQL" "$DB_TIMEOUT_SECONDS" db_ready
SERVER_VERSION="$(psql_value 'SHOW server_version')"
case "$SERVER_VERSION" in
"${EXPECTED_PG_MAJOR}".*) pass "PostgreSQL server ${SERVER_VERSION}" ;;
*) fail "unexpected PostgreSQL server version ${SERVER_VERSION}" ;;
esac
[ "$PG_DUMP_MAJOR" -ge "$EXPECTED_PG_MAJOR" ] ||
	fail "image pg_dump ${PG_DUMP_MAJOR} is older than the PostgreSQL ${EXPECTED_PG_MAJOR} server; backups would fail"
pass "image pg_dump ${PG_DUMP_MAJOR} >= server ${EXPECTED_PG_MAJOR}"

# ---------------------------------------------------------------- 3. root-owned volume

log "3. root-owned test volume"
docker volume create "$VOL" >/dev/null
# Railway 볼륨처럼 root 소유인 상태를 만든다(이미지의 /data 내용이 복사된 뒤 전체를 root로 바꾼다).
docker run --rm --user 0 --entrypoint sh -v "$VOL:/data" "$IMAGE" -c \
	'mkdir -p /data/lost+found && echo root > /data/lost+found/marker && chown -R 0:0 /data &&
	 test -z "$(find /data ! -user 0)" && ls -lnR /data' | sed 's/^/    /'
pass "volume prepared with root ownership only"

# ---------------------------------------------------------------- 4~6. start, Flyway, health

log "4. start backend container (prod profile defaults)"
docker run -d --name "$APP" --network "$NET" -p "127.0.0.1:${PORT}:8080" -v "$VOL:/data" \
	-e DB_URL="jdbc:postgresql://${DB}:5432/docflow" -e DB_USERNAME=docflow -e DB_PASSWORD="$DB_PASSWORD" \
	-e DOCFLOW_BOOTSTRAP_ADMIN_CODE="$BOOTSTRAP_CODE" \
	"$IMAGE" >/dev/null
wait_until "backend health" "$STARTUP_TIMEOUT_SECONDS" backend_healthy
FIRST_STARTED_AT="$(docker inspect -f '{{.State.StartedAt}}' "$APP")"
pass "container running"

log "5. Flyway migrations"
EXPECTED_MIGRATIONS="$(find "$MIGRATION_DIR" -name 'V*__*.sql' | wc -l | tr -d ' ')"
app_log_has "Successfully applied ${EXPECTED_MIGRATIONS} migrations" ||
	fail "Flyway did not report ${EXPECTED_MIGRATIONS} applied migrations"
APPLIED="$(psql_value 'SELECT count(*) FROM flyway_schema_history WHERE success')"
FAILED="$(psql_value 'SELECT count(*) FROM flyway_schema_history WHERE NOT success')"
[ "$APPLIED" = "$EXPECTED_MIGRATIONS" ] && [ "$FAILED" = "0" ] ||
	fail "flyway_schema_history: applied=${APPLIED} failed=${FAILED}, expected ${EXPECTED_MIGRATIONS}/0"
pass "${APPLIED} migrations applied (V1..V${EXPECTED_MIGRATIONS}), 0 failed"

log "6. health and runtime"
curl -fsS "${BASE_URL}/actuator/health" | sed 's/^/    /'
echo
docker logs "$APP" >"$WORK/app.log" 2>&1
RUNTIME_LINE="$(grep -m 1 -oE 'using Java [0-9][0-9.]* with PID [0-9]+ \([^)]*\)' "$WORK/app.log" || true)"
[ -n "$RUNTIME_LINE" ] || fail "startup log does not show the running Java version"
echo "    ${RUNTIME_LINE}"
grep -qE "using Java ${EXPECTED_JAVA_MAJOR}(\.| )" <<<"$RUNTIME_LINE" ||
	fail "application is not running on Java ${EXPECTED_JAVA_MAJOR}"
app_log_has "Administrator bootstrap invitation is ready" ||
	fail "bootstrap invitation log line not found"
if app_log_has -F "$BOOTSTRAP_CODE"; then
	fail "bootstrap code appears in the logs"
fi
pass "health UP, application runs on Java ${EXPECTED_JAVA_MAJOR}, bootstrap code not logged"

# ---------------------------------------------------------------- 7. non-root

check_non_root() {
	local docflow_uid process_name uids
	docflow_uid="$(app_exec 'id -u docflow')"
	process_name="$(app_exec 'cat /proc/1/comm')"
	uids="$(app_exec "awk '/^(Uid|Gid):/ {print \$1, \$2, \$3, \$4, \$5}' /proc/1/status")"
	echo "$uids" | sed 's/^/    /'
	[ "$process_name" = "java" ] || fail "PID 1 is '${process_name}', expected java"
	[ "$docflow_uid" != "0" ] || fail "docflow user has uid 0"
	awk -v uid="$docflow_uid" '/^Uid:/ { ok = ($2 == uid && $3 == uid && $4 == uid && $5 == uid) } END { exit !ok }' <<<"$uids" ||
		fail "java (PID 1) does not run as docflow (uid ${docflow_uid})"
	[ -z "$(app_exec 'find /data -xdev ! -user docflow')" ] || fail "files under /data not owned by docflow"
	pass "java is PID 1 and runs as docflow (uid ${docflow_uid}); /data fully owned by docflow"
}

log "7. non-root process and volume ownership"
check_non_root

# ---------------------------------------------------------------- 8. write / read on the volume

log "8. volume write/read"
docker exec -u docflow -e PROBE_TEXT="$PROBE_TEXT" "$APP" sh -c 'printf "%s" "$PROBE_TEXT" > /data/storage/smoke-probe.txt'
[ "$(app_exec 'cat /data/storage/smoke-probe.txt')" = "$PROBE_TEXT" ] || fail "probe file content mismatch"
pass "docflow user can write and read /data/storage"

COOKIES=()
http GET /api/auth/csrf
expect_status 204 "GET /api/auth/csrf"
http POST /api/auth/signup -H 'Content-Type: application/json' \
	--data "$(jq -nc --arg c "$BOOTSTRAP_CODE" --arg e "$ADMIN_EMAIL" --arg p "$ADMIN_PASSWORD" \
		'{inviteCode: $c, email: $e, password: $p, displayName: "Smoke Admin"}')"
expect_status 201 "POST /api/auth/signup (bootstrap admin)"
login
http POST /api/projects -H 'Content-Type: application/json' --data '{"name":"Smoke Project"}'
expect_status 201 "POST /api/projects"
PROJECT_ID="$(jq -r '.id' "$WORK/body")"
make_pdf "$WORK/smoke.pdf"
http POST "/api/projects/${PROJECT_ID}/documents" -F 'title=Smoke Document' -F "file=@${WORK}/smoke.pdf;type=application/pdf"
expect_status 201 "POST document upload"
DOCUMENT_ID="$(jq -r '.id' "$WORK/body")"
ORIGINAL_SHA="$(sha256sum "$WORK/smoke.pdf" | cut -d' ' -f1)"
[ "$(download_sha)" = "$ORIGINAL_SHA" ] || fail "downloaded file differs from the uploaded file"
STORED_FILES="$(app_exec 'find /data/storage -type f ! -name smoke-probe.txt | wc -l')"
[ "$STORED_FILES" -ge 1 ] || fail "uploaded file was not stored under /data/storage"
[ -z "$(app_exec 'find /data/storage ! -user docflow')" ] || fail "stored files not owned by docflow"
pass "API upload stored ${STORED_FILES} file(s) on the volume as docflow; download identical (sha256 ${ORIGINAL_SHA:0:12}…)"

# docs/deployment.md §7 백업 명령을 컨테이너 안에서 그대로 실행하고, 같은 임시 DB 서버의 새 DB로 복원해 비교한다.
log "8b. backup (docs §7) and restore with in-image pg_dump/pg_restore"
docker exec "$APP" sh -c 'set -e
	PGPASSWORD="$DB_PASSWORD" pg_dump --format=custom --no-owner \
	  --host "$(echo "$DB_URL" | sed -E "s#jdbc:postgresql://([^:/]+).*#\1#")" \
	  --port "$(echo "$DB_URL" | sed -E "s#.*:([0-9]+)/.*#\1#")" \
	  --username "$DB_USERNAME" --dbname "$(echo "$DB_URL" | sed -E "s#.*/##")" \
	  --file /tmp/smoke-backup.dump' || fail "pg_dump (docs §7 command) failed inside the backend image"
psql_value 'CREATE DATABASE docflow_restore_check' >/dev/null
docker exec -e PGPASSWORD="$DB_PASSWORD" "$APP" \
	pg_restore --no-owner --exit-on-error --host "$DB" --username docflow --dbname docflow_restore_check /tmp/smoke-backup.dump ||
	fail "pg_restore of the backup into an empty PostgreSQL ${EXPECTED_PG_MAJOR} database failed"
COUNT_SQL="SELECT (SELECT count(*) FROM users) || '/' || (SELECT count(*) FROM projects) || '/' || (SELECT count(*) FROM documents) || '/' || (SELECT count(*) FROM document_versions) || '/' || (SELECT count(*) FROM audit_logs) || '/' || (SELECT count(*) FROM flyway_schema_history WHERE success)"
SOURCE_COUNTS="$(psql_value "$COUNT_SQL")"
RESTORED_COUNTS="$(docker exec "$DB" psql -U docflow -d docflow_restore_check -tAc "$COUNT_SQL")"
echo "    users/projects/documents/versions/audit_logs/migrations: source ${SOURCE_COUNTS}, restored ${RESTORED_COUNTS}"
[ "$SOURCE_COUNTS" = "$RESTORED_COUNTS" ] || fail "restored row counts differ from the source"
psql_value 'DROP DATABASE docflow_restore_check' >/dev/null
app_exec 'rm -f /tmp/smoke-backup.dump'
pass "pg_dump ${PG_DUMP_MAJOR} backup of PostgreSQL ${EXPECTED_PG_MAJOR} restores with identical row counts"

# ---------------------------------------------------------------- 9. restart and persistence

log "9. restart container and verify persistence"
docker restart -t 60 "$APP" >/dev/null
wait_until "backend health after restart" "$STARTUP_TIMEOUT_SECONDS" backend_healthy
SECOND_STARTED_AT="$(docker inspect -f '{{.State.StartedAt}}' "$APP")"
[ "$SECOND_STARTED_AT" != "$FIRST_STARTED_AT" ] || fail "container start time did not change after restart"
app_log_has 'Schema "public" is up to date. No migration necessary.' ||
	fail "Flyway did not report an up-to-date schema after restart"
[ "$(app_exec 'cat /data/storage/smoke-probe.txt')" = "$PROBE_TEXT" ] || fail "probe file lost after restart"
# 세션은 서버 메모리에 있으므로 재시작 후에는 무효여야 한다(실제로 새 프로세스인지 확인).
http GET /api/auth/me
expect_status 401 "GET /api/auth/me with the pre-restart session"
login
[ "$(download_sha)" = "$ORIGINAL_SHA" ] || fail "downloaded file differs after restart"
check_non_root
pass "restarted (${FIRST_STARTED_AT} -> ${SECOND_STARTED_AT}); schema up to date; probe file, DB rows and uploaded file preserved"

log "backend container smoke test passed"
