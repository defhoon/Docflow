# 공개 배포 가이드 (Railway) — #27, #37

> 상태: 준비 완료, **미배포**. Railway 계정·유료 리소스 생성과 외부 공개는 사용자 승인 후 진행한다.
> 결정 배경: [ADR-0007](./adr/0007-deployment-railway.md). 비밀값은 Railway Variables(Sealed)에만 입력하고 Git·채팅·Issue·로그에 남기지 않는다.
> "미검증" 표시는 Railway 계정 없이 확인할 수 없어 배포 시 콘솔에서 확인해야 하는 항목이다.

## 1. 서비스와 연결 관계

```text
브라우저 ──HTTPS──▶ frontend (Next.js standalone, 공개 도메인)
                       │ /api/*, /backend/health  rewrite (Railway 비공개 네트워크)
                       ▼
                    backend (Spring Boot prod, 공개 도메인 없음) ──▶ Postgres 16 (비공개, TCP Proxy 끔)
                       │
                       └─ 볼륨 /data  (문서 원본 /data/storage, 임시 백업 /data/backup)
```

| Railway 리소스 | 소스·빌드 | 공개 여부 | 연결 |
| --- | --- | --- | --- |
| `Postgres` | Railway PostgreSQL 템플릿, **주 버전 16으로 고정**(backend 이미지의 `pg_dump`·`pg_restore`가 16이다. 클라이언트보다 높은 서버는 백업할 수 없고, 더 높은 클라이언트로 만든 덤프는 16에 복원되지 않을 수 있다. §7.1) | **TCP Proxy 끔** | backend가 참조 변수로 접속 |
| `backend` | GitHub 저장소, Root Directory `backend`, `backend/Dockerfile` | **Public Networking 없음** | `backend.railway.internal:8080` |
| `frontend` | GitHub 저장소, Root Directory `frontend`, `frontend/Dockerfile` | 공개 도메인(HTTPS) | `BACKEND_URL`로 backend 호출 |
| 볼륨 | backend에 연결, Mount path `/data` | — | Hobby 5GB/볼륨 상한 |

배포 브랜치: **`production`** (2026-11-25, Issue #65). `main`은 기능 통합·CI 검증용이며 운영에 자동 배포하지 않는다. 절차는 §1.1.

### 1.1 배포 브랜치와 운영 배포 절차

배경: Backend·Frontend 서비스가 `main`에 연결되어 있어 `main` 병합 커밋마다 Railway 운영 배포가 자동으로 일어났다(GitHub 배포 기록 `railway-app[bot]`, 환경 `handsome-heart / production`). 백업 없이 운영 마이그레이션이 실행될 수 있어 배포 브랜치를 분리한다.

**Railway 설정(사용자 작업, 서비스마다: backend·frontend)**

1. 서비스 → **Settings** → **Source**(연결된 GitHub 저장소) 영역에서 배포 브랜치(Branch)를 `main` → `production`으로 바꾼다. 메뉴 이름은 Railway 화면 기준으로 확인한다.
2. 두 서비스 모두 바꾼 뒤, `production`(현재 `main`과 같은 운영 커밋 `3386fcc`)으로 바꾼 것 자체가 새 배포를 만들지 않았는지 Deployments 목록에서 확인한다.
3. 이후 `main` 병합 커밋에 새 Railway 배포 기록이 생기지 않는지 확인한다(Claude Code가 GitHub 배포 기록으로 확인할 수 있다).

**운영 배포(명시적 승인 후에만)**

1. 반영할 `main` 커밋의 CI(Backend, Frontend, 컨테이너 스모크, Docker 빌드) 통과 확인
2. §7 백업: DB 덤프와 볼륨 파일 tar 생성, `SHA256SUMS`·`storage_key` 검증 스크립트로 정상 여부 확인, 로컬로 내려받아 보관
3. `git checkout production && git merge --ff-only <main 커밋> && git push origin production` (fast-forward가 안 되면 중단하고 원인 확인. `production`에 직접 커밋하지 않는다)
4. Railway 배포 로그에서 Flyway 적용 버전 확인(예: `Migrating schema "public" to version "11 - document version texts"`, `"12 - ai analysis"`, 이후 `Successfully applied`)
5. §9 체크리스트로 기능 확인. 실패 시 §10 롤백

## 2. 빌드·실행 설정 (서비스별 Settings)

| 항목 | backend | frontend |
| --- | --- | --- |
| Root Directory | `backend` | `frontend` |
| Builder | Dockerfile (자동 감지) | Dockerfile (자동 감지) |
| Start command | 비움(이미지 ENTRYPOINT: 볼륨 소유권 정리 → 비root `java -jar`) | 비움(`node server.js`, 비root) |
| Port | `PORT=8080` 변수 지정(헬스체크·내부 통신 포트, Spring 기본 8080) | Railway가 `PORT` 주입 |
| Healthcheck path | `/actuator/health` (UP/DOWN만, 세부 정보 없음) | `/login` |
| Healthcheck timeout | 300초(첫 기동 시 Flyway 포함) | 기본 |
| Restart policy | On failure | On failure |
| Replicas | **1** (메모리 세션·시도 제한·볼륨 전제) | 1 |

- 볼륨이 연결된 서비스는 새 배포가 기존 컨테이너와 동시에 볼륨을 쓸 수 없어 배포 중 짧은 중단이 생길 수 있다(미검증, Railway 동작 확인).
- frontend의 `BACKEND_URL`은 rewrite 대상이 **빌드 시** 고정되므로 서비스 변수로 넣고 변수 변경 후에는 재빌드한다.

### 2.1 Railway 콘솔 입력용 최종 설정표 (2026-11-11, #43)

필수 = 빠지면 배포 실패·데이터 유실·보안 정책 위반. 권장 = 운영 안정성·비용 관리.

| 순서 | 서비스 | 항목 | 값 | 구분 |
| --- | --- | --- | --- | --- |
| 1 | (워크스페이스) | 사용량 알림 / 하드 리밋 | $15 / $25 (§8.1) | 권장(하드 리밋은 강력 권장) |
| 2 | Postgres | 생성 | PostgreSQL **16** | 필수 |
| 2 | Postgres | TCP Proxy(공개 접속) | 끔 | 필수 |
| 3 | backend | Source / Root Directory | GitHub 저장소 / `backend` | 필수 |
| 3 | backend | Dockerfile 경로 | `backend/Dockerfile`(Root Directory 기준 `Dockerfile`, 자동 감지) | 필수 |
| 3 | backend | 볼륨 Mount path | `/data` (없으면 재배포 때 파일 유실, 경고 없음) | 필수 |
| 3 | backend | Variables | `DB_URL`·`DB_USERNAME`·`DB_PASSWORD`(참조 변수), `PORT=8080` | 필수 |
| 3 | backend | Secret (Sealed) | `DOCFLOW_BOOTSTRAP_ADMIN_CODE`(24~64자, 가입 후 삭제) | 필수(최초 1회) |
| 3 | backend | 넣지 않는 변수 | `SESSION_COOKIE_SECURE`, `DOCFLOW_ALLOW_INSECURE_COOKIES`, `DOCFLOW_TRUSTED_PROXY_HOPS`(0 유지), `DOCFLOW_PREVIEW_OFFICE_ENABLED` | 필수(정책) |
| 3 | backend | Public Networking | **없음**(내부 주소만) | 필수 |
| 3 | backend | Healthcheck path / timeout | `/actuator/health` / 300초 | 필수 / 권장 |
| 3 | backend | Replicas | 1 | 필수(메모리 세션·시도 제한·볼륨) |
| 3 | backend | 메모리 제한 | 1GB | 권장 |
| 3 | backend | Restart policy | On failure | 권장 |
| 4 | frontend | Source / Root Directory | GitHub 저장소 / `frontend` | 필수 |
| 4 | frontend | Dockerfile 경로 | `frontend/Dockerfile`(자동 감지) | 필수 |
| 4 | frontend | Variables | `BACKEND_URL=http://${{backend.RAILWAY_PRIVATE_DOMAIN}}:8080`(빌드 시 필요) | 필수 |
| 4 | frontend | Public Networking | 도메인 생성(HTTPS) | 필수 |
| 4 | frontend | Healthcheck path | `/login` | 권장 |
| 4 | frontend | 메모리 제한 | 512MB | 권장 |
| 5 | (배포 순서) | backend 배포 → 로그 확인(§5, §4) → frontend 배포 → §9 | | 필수 |

## 3. 환경 변수와 입력 방법

입력 위치: 서비스 → Variables. 비밀은 **Sealed**로 추가(값이 UI·API에서 다시 보이지 않음). DB 접속 정보는 직접 복사하지 않고 **참조 변수**(`${{Postgres.…}}`)를 쓴다.

| 서비스 | 변수 | 값 | 비밀 |
| --- | --- | --- | --- |
| backend | `SPRING_PROFILES_ACTIVE` | `prod` (이미지 기본값, 생략 가능) | |
| backend | `PORT` | `8080` | |
| backend | `DB_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` | |
| backend | `DB_USERNAME` | `${{Postgres.PGUSER}}` | |
| backend | `DB_PASSWORD` | `${{Postgres.PGPASSWORD}}` | ✔ |
| backend | `DOCFLOW_BOOTSTRAP_ADMIN_CODE` | 최초 관리자 코드(§4). 가입 후 **삭제** | ✔ Sealed |
| backend | `DOCFLOW_TRUSTED_PROXY_HOPS` | **설정하지 않음(0)**. §9-14 확인 후에만 `1` | |
| backend | `DOCFLOW_STORAGE_DIR` | 생략(기본 `/data/storage`) | |
| backend | `DOCFLOW_PREVIEW_OFFICE_ENABLED` | 생략(기본 false). 격리 검증 전 켜지 않음 | |
| frontend | `BACKEND_URL` | `http://${{backend.RAILWAY_PRIVATE_DOMAIN}}:8080` 또는 `http://backend.railway.internal:8080` | |

`SESSION_COOKIE_SECURE`는 prod 기본값 true이므로 설정하지 않는다(HTTPS 전용 쿠키). false로 두면 backend가 기동을 거부한다(#39). `DOCFLOW_ALLOW_INSECURE_COOKIES`는 로컬 HTTP 확인 전용이며 Railway에 **설정하지 않는다**. prod 프로파일은 저장소의 `.env`를 읽지 않는다(이미지에도 없음).

## 4. 최초 관리자 생성

1. 코드 생성(로컬 PowerShell, 화면에서 바로 복사해 Sealed 변수에 붙여넣고 저장하지 않음):
   `-join ((48..57)+(65..90)+(97..122) | Get-Random -Count 40 | % {[char]$_})`
2. backend에 `DOCFLOW_BOOTSTRAP_ADMIN_CODE`(Sealed) 추가 → 배포.
3. backend 로그: `Administrator bootstrap invitation is ready (expires at …)` (코드 원문 없음). 24시간 안에 가입해야 한다.
4. `https://<frontend 도메인>/signup`에서 그 코드로 가입 → 로그인 → 사이드바 "초대 관리" 확인.
5. 변수 삭제 → 재배포 → 로그에 `still set` 경고 없음 확인. 관리자가 있으면 관리자 초대는 다시 만들 수 없고 남은 것은 폐기된다.
6. 관리자 분실 시: 자동 발급 없음. Railway Postgres 콘솔에서 `UPDATE users SET system_role='ADMIN' WHERE email='…';`.

## 5. DB 초기화와 마이그레이션

- backend 기동 시 Flyway가 V1~V9를 자동 적용한다(빈 DB면 전체 생성). 로그: `Successfully applied 9 migrations` → 이후 `Schema "public" is up to date`.
- PostgreSQL은 DDL이 트랜잭션이라 마이그레이션 실패 시 해당 버전 전체가 롤백된다. 기동 실패 → 헬스체크 실패 → 이전 배포가 유지된다(첫 배포는 서비스 없음).
- V7은 `APPROVED` 상태 문서가 있으면 의도적으로 중단한다(새 운영 DB에는 해당 없음).
- 새 마이그레이션이 포함된 배포 전에는 반드시 §7 백업을 먼저 수행한다(마이그레이션은 앞으로만 진행).

## 6. 영속 볼륨

- backend → Settings → Volumes(또는 프로젝트 캔버스에서 우클릭 → Volume) → Mount path `/data`.
- 이미지 entrypoint가 `/data` 소유권을 비root 사용자(docflow)로 맞춘다. 첫 업로드 후 재배포해도 파일이 남는지 §9-12에서 확인.
- 볼륨 사용량은 Railway 메트릭에서 확인. 4GB를 넘으면 Pro 전환 또는 저장소 확장 검토.

## 7. 백업·복구 (로컬 실제 실행 검증 완료, Railway 미실행)

볼륨은 백업이 아니다. **DB를 먼저, 파일 저장소를 나중에** 백업하고 두 파일을 한 쌍으로 관리한다(이유와 한계는 §7.2). 초기 권장 주기: 주 1회 + 마이그레이션 포함 배포 직전.

```sh
# railway ssh --service backend  (backend 컨테이너 셸). 아래 전체를 붙여넣는다. 중간에 실패하면 멈춘다.
sh -eu <<'BACKUP'
STAMP=$(date -u +%Y%m%dT%H%M%SZ)          # DB·파일 백업이 같은 이름을 쓰도록 한 번만 계산
B=/data/backup/$STAMP
mkdir -p "$B"
DB_HOST=$(echo "$DB_URL" | sed -E 's#jdbc:postgresql://([^:/]+).*#\1#')
DB_PORT=$(echo "$DB_URL" | sed -E 's#.*:([0-9]+)/.*#\1#')
DB_NAME=$(echo "$DB_URL" | sed -E 's#.*/##')
export PGPASSWORD="$DB_PASSWORD"
# 1) DB 먼저
pg_dump --format=custom --no-owner --host "$DB_HOST" --port "$DB_PORT" \
  --username "$DB_USERNAME" --dbname "$DB_NAME" --file "$B/docflow.dump"
# 2) 파일 저장소 나중 (업로드 임시 파일·미리보기 캐시 제외)
tar -C /data -czf "$B/storage.tar.gz" --exclude=storage/tmp --exclude=storage/previews storage
# 3) 한 쌍 검증: 덤프의 document_versions가 가리키는 파일이 모두 아카이브에 있는지
pg_restore --data-only --table=document_versions --file - "$B/docflow.dump" | awk -F'\t' '
  /^COPY public\.document_versions / { h=$0; sub(/^[^(]*\(/, "", h); sub(/\).*$/, "", h)
    n=split(h, c, ", "); for (i=1; i<=n; i++) if (c[i]=="storage_key") k=i; on=1; next }
  on && $0=="\\." { on=0; next }
  on { print "storage/" $k }' | sort > "$B/referenced.txt"
tar -tzf "$B/storage.tar.gz" | sort > "$B/archived.txt"
MISSING=$(comm -23 "$B/referenced.txt" "$B/archived.txt")
[ -z "$MISSING" ] || { echo "MISSING in archive:"; echo "$MISSING"; exit 1; }
(cd "$B" && sha256sum docflow.dump storage.tar.gz > SHA256SUMS)
echo "OK $STAMP: $(wc -l < "$B/referenced.txt") referenced files, all archived"
BACKUP
```

- 결과물: `/data/backup/<STAMP>/` 아래 `docflow.dump`, `storage.tar.gz`, `SHA256SUMS`(+ 검증용 목록 2개). 이 디렉터리 하나가 백업 한 쌍이다.
- 백업 디렉터리는 안전한 외부 저장소로 옮긴 뒤 볼륨에서 삭제한다(볼륨 5GB 상한을 같이 쓴다). 덤프는 사용자 정보가 들어 있는 비밀정보다.
- 복구(빈 DB·빈 `/data/storage` 기준): `sha256sum -c SHA256SUMS` → `pg_restore --no-owner --exit-on-error --single-transaction --dbname <대상> docflow.dump` → `tar -C /data -xzf storage.tar.gz` → backend 재시작(`Successfully validated 9 migrations`) → §7.2-7 파일 검증 → 최종본 다운로드 확인. 제외한 `storage/tmp`·`storage/previews`는 backend가 필요할 때 다시 만든다.
- 로컬 검증(2026-11-10): 확정 문서·피드백·2개 버전 데이터를 백업 → 새 DB·새 디렉터리로 복구 → Flyway 9개 검증 통과, FINALIZED·피드백 유지, 최종본 동일 바이트.
- Railway 자체 Postgres 백업 기능의 요금제별 제공 여부는 콘솔에서 확인(미검증).

### 7.1 백업 범위와 PostgreSQL 클라이언트 버전 (#45)

- **DB 덤프와 파일 저장소는 별개다.** `pg_dump`에는 메타데이터(사용자·문서·버전·`storage_key`)만 있고 업로드 파일은 없다. DB만 복원하면 문서는 보이지만 다운로드가 실패한다. 항상 같은 시점의 `docflow-*.dump`와 `storage-*.tar.gz`를 한 쌍으로 보관·복원한다.
- 클라이언트 버전 사고 기록(2026-11-11, CI 실제 컨테이너): 버전 없이 쓰던 `eclipse-temurin:25-jre`가 Ubuntu 26.04 기반으로 바뀌어 이미지 안 `pg_dump`·`pg_restore`가 **18.6**이었다. 그 덤프를 PostgreSQL 16에 복원하면 `ERROR: unrecognized configuration parameter "transaction_timeout"`(`SET transaction_timeout = 0;`)으로 실패했다(`--exit-on-error`).
- 조치: 베이스 이미지를 Ubuntu 24.04 기반 `eclipse-temurin:25-jdk-noble` / `25-jre-noble`로 고정하고, 클라이언트를 `postgresql-client-16` 패키지 이름으로 설치한다. noble 태그도 보안 갱신으로 내용이 바뀌므로 영구 고정은 아니다. 주 버전은 CI 스모크 테스트가 매번 확인한다(16이 아니면 실패).
- 수정 후 CI 확인 값(2026-11-17, run 44045682347): OS `Ubuntu 24.04.5 LTS`, Java `25.0.5`(Temurin), `pg_dump`·`pg_restore` `16.15 (Ubuntu 16.15-0ubuntu0.24.04.1)`, 테스트 서버 PostgreSQL 16.16.
- 수정 후 CI 백업·복원 결과: 위 §7 명령으로 덤프 → **별도의 빈 PostgreSQL 16 컨테이너**에 `pg_restore --no-owner --exit-on-error --single-transaction` → SQL 오류 없음, public 9개 테이블의 행 수·전체 행 해시와 시퀀스 8개 값이 원본과 동일. 파일 저장소는 `tar`로 따로 백업·복원해 저장 파일이 바이트 단위로 같고, 복원한 DB의 모든 `storage_key`에 파일이 있음을 확인했다.
- 미검증: Railway 환경에서의 실제 백업·복원(`railway ssh` 접속, 백업 파일 반출), 대용량 데이터에서의 소요 시간.

### 7.2 백업 일관성 (#47)

DB 덤프와 파일 아카이브는 서로 다른 시점에 만들어진다(덤프는 시작 시점 스냅샷, `tar`는 그 뒤 파일을 차례로 읽는다). 아래 설명은 **현재 코드 동작**에 근거하며, 성립 조건(가정)과 한계를 구분한다.

**1. 현재 파일 저장 → DB 커밋 순서** (`DocumentFileIntake`, `LocalFileStorage`, `StoredFileTransaction`)

1. 업로드 본문을 `storage/tmp/upload-*.tmp`에 받으며 크기·SHA-256을 계산하고 형식을 검증한다.
2. 새 UUID 저장 키(`documents/<UUID>`)로 `storage/documents/`에 복사하고 디스크에 동기화(`force`)한다. 같은 키가 있으면 덮어쓰지 않고 실패한다.
3. 그다음 DB 트랜잭션에서 `document_versions` 행(`storage_key`, `size_bytes`, `sha256`)을 쓰고 커밋한다.
4. 트랜잭션이 롤백되면 이 요청이 만든 파일만 지운다. 커밋 결과가 불명확하면 파일을 남긴다(고아 파일). 임시 파일은 요청이 끝나면 지운다.

**가정 (현재 구조에서 성립)**

- A1. 커밋된 버전 행이 가리키는 파일은 커밋 **전에** 이미 완전히 기록되어 있다.
- A2. 커밋된 버전의 파일을 지우거나 바꾸는 기능이 없다(문서·버전 삭제 API, 파일 교체 API 없음. 확정 문서는 읽기 전용). 저장 키는 재사용하지 않는다.
- A3. `storage/documents/` 밖의 `storage/tmp`(업로드 임시), `storage/previews`(미리보기 작업 공간·캐시, 용량 초과 시 자동 삭제)는 원본이 아니다.

**2. DB를 먼저 백업하는 이유**

A1·A2가 성립하면, 덤프 스냅샷에 들어간 모든 버전 행의 파일은 덤프 시작 전에 이미 기록되어 있고 이후 지워지지 않는다. 따라서 그 뒤에 만든 파일 아카이브에는 덤프가 가리키는 파일이 모두 들어간다. 순서를 반대로 하면(파일 → DB) 아카이브 이후에 커밋된 업로드가 덤프에만 남아, 복원 후 **DB에는 있는데 파일이 없는** 버전이 생긴다(다운로드 실패 = 데이터 유실).

**3. 백업 중 업로드가 있을 때의 위험**

| 상황 | 결과 | 영향 |
| --- | --- | --- |
| 덤프 이후·`tar` 이전에 커밋된 업로드 | 파일은 아카이브에 있고 DB 행은 없음 | 복원 후 고아 파일(용량만 차지, 화면에 안 보임). 그 업로드는 이 백업에서 빠짐 |
| `tar`가 읽는 중에 저장 중인 파일 | 일부만 기록된 파일이 아카이브에 들어갈 수 있음 | 해당 행이 덤프에 없으므로 고아 파일. 또는 `tar`가 "file changed as we read it"로 실패 코드를 내며 위 스크립트가 멈춤 → 다시 실행(무시하지 않는다) |
| 순서를 반대로 실행 | DB 행만 있고 파일 없음 | 데이터 유실 |
| A2가 깨지는 기능 추가 후 | 덤프에 있는 파일이 `tar` 전에 지워짐 | 데이터 유실(8 참고) |

즉 현재 구조에서 백업 중 업로드는 **유실이 아니라 "이번 백업에서 빠짐·고아 파일"** 로 나타난다. 그래도 백업 시점을 명확히 하려고 업로드를 멈추고 실행한다.

**4. 소규모 테스트 환경의 백업 전 업로드 중단 절차**

앱에는 점검(쓰기 중지) 모드가 없다. 비공개 테스트에서는 운영 절차로 멈춘다.

1. 테스터에게 백업 시간(예: 10분)을 미리 공지하고 그동안 업로드·피드백을 하지 않도록 요청한다.
2. 시작 직전 backend 로그에서 진행 중인 업로드가 없는지 확인한다(최근 `Document created`·`Document version created` 로그 이후 1~2분 대기. 로그는 진행 중인 요청을 보여 주지 않으므로 참고용이다).
3. 참고 기록: 덤프 직전과 `tar` 직후에 `SELECT max(id) FROM audit_logs;`를 실행해 기록한다. 값이 같으면 **그 사이 커밋되어 기록된 활동이 없었다는 뜻일 뿐**이며 일관성을 보장하지 않는다(커밋 전 진행 중 요청, 실패한 요청, 활동 기록 대상이 아닌 변경은 드러나지 않는다). 일관성의 근거는 2의 순서와 A1·A2, 그리고 아래 3)·7)의 파일 검증이다.
4. §7 스크립트를 실행한다. 값이 달라졌으면 그 업로드가 이번 백업에 빠졌을 수 있으니 필요하면 다시 실행한다.
5. 완료를 공지한다.

공개 서비스로 전환하면 공지 방식은 쓸 수 없다. 그때는 쓰기 중지 모드나 저장소 스냅샷 같은 기능이 필요하다(별도 검토·승인).

**5. `storage/tmp` 제외와 tar 경로 검증**

- 제외한다: `storage/tmp`(요청 중 임시 파일, 읽는 중 사라지면 `tar` 오류 원인), `storage/previews`(원본에서 다시 만드는 캐시·작업 공간). 원본은 `storage/documents/`에만 있다.
- 경로: `tar -C /data ... storage` 이므로 아카이브 안 경로는 `storage/documents/<UUID>`다. GNU tar(1.35, 2026-11-17 로컬 확인)에서 `--exclude=storage/tmp --exclude=storage/previews`가 두 디렉터리를 빼고 `storage/documents/`는 남기는 것을 확인했다. 백업 이미지(Ubuntu 24.04)의 tar 버전은 실행 시 `tar --version`으로 확인한다(미확인).
- 스크립트 3단계가 덤프의 모든 `storage_key`를 `storage/<key>` 형태로 아카이브 목록과 비교한다. 경로가 어긋나면(예: `-C` 누락으로 `data/storage/...`) 모든 파일이 MISSING으로 나와 실패한다.

**6. 한 쌍 관리**

- 같은 `STAMP` 디렉터리에 `docflow.dump`와 `storage.tar.gz`를 함께 만들고 `SHA256SUMS`로 묶는다. 두 파일은 항상 같이 옮기고, 같이 보관하고, 같이 지운다.
- 복원은 반드시 같은 `STAMP`의 두 파일로 한다. 다른 시점의 아카이브를 섞으면 DB가 가리키는 파일이 없을 수 있다.
- 외부 보관 시 `SHA256SUMS`도 함께 옮기고, 복원 전에 `sha256sum -c SHA256SUMS`로 손상 여부를 확인한다.

**7. 복원 후 파일 존재·무결성 검증**

DB에는 버전마다 `size_bytes`, `sha256`이 있다. 복원한 backend 컨테이너에서 실행한다.

```sh
sh -eu <<'VERIFY'
DB_HOST=$(echo "$DB_URL" | sed -E 's#jdbc:postgresql://([^:/]+).*#\1#')
DB_PORT=$(echo "$DB_URL" | sed -E 's#.*:([0-9]+)/.*#\1#')
DB_NAME=$(echo "$DB_URL" | sed -E 's#.*/##')
export PGPASSWORD="$DB_PASSWORD"
psql --host "$DB_HOST" --port "$DB_PORT" --username "$DB_USERNAME" --dbname "$DB_NAME" -v ON_ERROR_STOP=1 -tA -F ' ' \
  -c "SELECT storage_key, size_bytes, sha256 FROM document_versions ORDER BY id" > /tmp/docflow-verify.txt
FAIL=0; N=0
while read -r key size sha; do
  N=$((N + 1)); f="/data/storage/$key"
  if [ ! -f "$f" ]; then echo "MISSING $key"; FAIL=1; continue; fi
  [ "$(wc -c < "$f" | tr -d ' ')" = "$size" ] || { echo "SIZE MISMATCH $key"; FAIL=1; }
  [ "$(sha256sum "$f" | cut -d' ' -f1)" = "$sha" ] || { echo "SHA256 MISMATCH $key"; FAIL=1; }
done < /tmp/docflow-verify.txt
rm -f /tmp/docflow-verify.txt
[ "$FAIL" = 0 ] && echo "OK: $N versions, all files present and identical" || { echo "FAILED"; exit 1; }
VERIFY
```

- 실패가 하나라도 있으면 복원을 완료로 보지 않는다. 고아 파일(DB에 없는 파일)은 이 검사에서 실패로 보지 않는다.

**검증 기록 (2026-11-17, Claude Code 클라우드, 로컬 PostgreSQL 16 임시 DB·임시 디렉터리)**: §7 스크립트와 위 검증 스크립트의 접속 부분만 로컬 값으로 바꿔 실행했다. 정상 데이터에서 통과, 파일 하나를 지우면 3단계 `MISSING`으로 실패, 복원 파일을 1바이트 바꾸면 `SIZE`·`SHA256 MISMATCH`로 실패했다. 탭이 들어간 원본 파일명도 `storage_key` 추출에 영향이 없었다. Docker 이미지·Railway에서는 이 스크립트를 실행하지 않았다(미검증). CI(#45, §13.3)는 같은 순서(덤프 → `tar`)와 `storage_key` 존재 확인을 실제 컨테이너에서 검증한다.

**8. 파일 삭제·교체 기능 추가 시 재검토**

다음 기능이 생기면 A2가 깨지므로 이 절차를 그대로 쓰면 안 된다: 문서·버전 삭제, 파일 교체(같은 키 덮어쓰기), 보관 기간 정리, 고아 파일 정리 작업, 저장소 이전. 그때는 기능 설계와 함께 백업 방식을 다시 정한다. 예: 논리 삭제 후 일정 기간 뒤 물리 삭제(백업 주기보다 길게), 백업 중 쓰기 중지, 볼륨 스냅샷 기능 사용(Railway 제공 여부 미검증).

## 8. 비용과 상한

Railway 공식 요금(2026-11 확인, [docs.railway.com/pricing](https://docs.railway.com/pricing/plans)): Hobby $5/월(사용량 크레딧 $5 포함), RAM $10/GB·월, CPU $20/vCPU·월, 볼륨 $0.15/GB·월, egress $0.05/GB.

| 리소스 | 가정 | 월 추정 |
| --- | --- | --- |
| backend | 상시 RAM ≈0.5GB(JVM), CPU 낮음 | $5~7 |
| frontend | RAM ≈0.2GB | $2~3 |
| Postgres | RAM ≈0.25GB | $2.5~3 |
| 볼륨 | 1~5GB | $0.15~0.75 |
| 합계 | 소규모 사용 | **약 $10~15 (크레딧 $5 차감 후 추가 $5~10)** |

- 변동 요인: 메모리는 유휴 중에도 과금, 로그인 대량 시도·다운로드 트래픽에 따른 CPU·egress 증가, 볼륨 5GB 초과 시 Pro.
- 비용 상한: Workspace → Usage에서 **사용량 알림**과 **하드 리밋**을 함께 설정한다(§8.1). 알림만으로는 지출이 막히지 않는다.
- 재평가(2026-11-11, 가정): RAM 합계 약 0.7~0.95GB + CPU 평균 0.05~0.1 vCPU + 볼륨 1.5~5.5GB + egress 수 GB → 사용량 약 $8~12.5. 청구액은 `max($5, 사용량)`이므로 **약 $8~13**. 위 표의 $10~15와 같은 범위이며 실제 값은 배포 후 Metrics·Usage로 확인한다. 단가는 확인된 공식 요금, 사용량은 가정이다.
- backend 메모리 상한: 서비스 Settings의 리소스 제한에서 1GB 이하로 제한(JVM은 `MaxRAMPercentage=75`).

### 8.1 알림과 하드 리밋의 차이 (초기 비공개 테스트 방침: 앱 수준 제한 + 감시)

출처: Railway [Cost Control](https://docs.railway.com/reference/usage-limits) 문서의 검색 요약(2026-11-11). 이 작업 환경에서는 `docs.railway.com`에 직접 접속할 수 없어 원문을 읽지 못했다. 콘솔에서 다시 확인한다.

| 구분 | 동작 (요약 기준) | 권장 시작값 |
| --- | --- | --- |
| 사용량 알림(custom email alert) | 설정 금액을 넘으면 **이메일만** 보낸다. 서비스는 계속 동작하고 과금도 계속된다 | $15 |
| 하드 리밋(hard limit, compute) | 도달하면 **워크스페이스의 모든 워크로드를 오프라인으로 전환**해 추가 사용을 막는다. 75%·90%·100%에서 이메일. **최소 설정 금액 $10** | $25 |
| 서비스별 리소스 제한 | 서비스가 쓸 수 있는 vCPU·메모리 최대치. 메모리 초과 시 OOM 재시작 가능 | backend 1GB, frontend 512MB |

미검증(공식 문서에서 확인 못 함): 하드 리밋 도달 후 실제 중단까지의 지연·초과 사용 범위, 중단 중 볼륨 데이터 처리, 한도 상향 후 자동 재배포 여부(제3자 가이드에만 언급). 하드 리밋은 "지출 차단"이지만 서비스 전체 중단을 뜻하므로 너무 낮게 잡지 않는다.

### 8.2 사용량·부하 확인 방법

- 서비스 → **Metrics**: CPU·메모리·네트워크 그래프(서비스별). 로그인 폭주 시 backend CPU가 먼저 오른다(bcrypt 비교).
- Workspace → **Usage**: 기간별 누적 사용 금액과 예측.
- backend 로그(Observability/Logs) 필터: `Login failed`(실패, `clientIp` 포함), `Signup rejected`, `prod profile is running with INSECURE cookies`(운영에서 보이면 즉시 조치).
- 현재 한계: 시도 제한으로 **차단된(429) 요청은 로그에 남지 않는다**. 차단 규모는 실패 로그의 급증과 CPU 그래프로 간접 판단한다. 차단 로그(키는 마스킹)를 추가하려면 별도 작업·승인이 필요하다.

### 8.3 서비스·볼륨 삭제 후에도 남을 수 있는 비용

근거는 대부분 Railway 커뮤니티 포럼(공식 정책 아님, 콘솔·지원으로 확인 필요).

- 데이터가 있는 볼륨을 삭제하면 **48시간 보존 기간** 후 완전히 지워지며, 그 기간에도 GB·분 단위로 과금된다는 안내가 있다.
- **서비스를 삭제해도 볼륨이 분리된 채 남아** 계속 과금된 사례가 보고되었다. 서비스 삭제 후 Volumes 목록에서 남은 볼륨이 없는지 확인한다.
- 삭제 전까지의 사용량은 해당 청구 기간에 포함된다.
- Hobby 기본료($5)는 플랜을 해지하기 전까지 청구된다(추정).
- 정리 순서: 백업 확보 → 서비스 중지 → 볼륨 삭제 확인 → Usage에서 증가가 멈췄는지 확인 → 필요 시 플랜 해지.

### 8.4 앞단 요청 제한(Cloudflare 등)이 필요해지는 조건

다음 중 하나라도 해당하면 앞단 요청 제한을 검토한다(도입 시 DNS·프록시 신뢰 경계 변경 → ADR·승인).

1. backend CPU가 로그인 요청 때문에 5분 이상 80% 이상 유지되거나 응답이 느려짐
2. `Login failed`가 분당 수십 건 이상 반복되거나 서로 다른 IP·계정으로 분산됨
3. 사용량 알림이 예상보다 빨리 도달(월 $15 이전)
4. 초대 기반 비공개 테스트를 넘어 불특정 사용자에게 공개
5. 특정 계정 잠금 신고가 반복됨(hops=0 상태의 알려진 제약)

## 9. 배포 후 검증 체크리스트

순서대로 실행하고 결과(통과/실패/메모)를 `docs/SPRINT_PROGRESS.md`에 기록한다. `<F>` = frontend 도메인.

| # | 항목 | 방법 | 기대 결과 |
| --- | --- | --- | --- |
| 1 | 헬스 | backend Deploy 로그·`curl https://<F>/backend/health` | `{"status":"UP"}`만(세부 정보 없음) |
| 2 | Backend 직접 접근 차단 | backend 서비스에 Public Domain이 없는지 확인. `curl https://<F>/actuator/env` | 도메인 없음, 404 |
| 3 | DB 직접 접근 차단 | Postgres → Settings → Networking에 TCP Proxy 없음 | 공개 호스트·포트 없음 |
| 4 | Secure Cookie | 브라우저 개발자 도구 → Application → Cookies | `JSESSIONID`: Secure·HttpOnly·SameSite=Lax, `XSRF-TOKEN`: Secure |
| 5 | CSRF | 로그인 상태에서 Console: `fetch("/api/projects",{method:"POST",headers:{"Content-Type":"application/json"},body:"{\"name\":\"x\"}"}).then(r=>r.status)` | 403 |
| 6 | 최초 관리자 | §4 | 관리자 가입, 로그에 코드 없음, 변수 삭제 후 경고 없음 |
| 7 | 초대코드 가입 | 초대 관리에서 발급 → 시크릿 창 가입 → 같은 코드 재사용 | 가입 성공, 재사용 거부 |
| 8 | 프로젝트·구성원 | 관리자 프로젝트 생성 → 구성원 추가 | 구성원 목록 표시 |
| 9 | 업로드·피드백 | 구성원 PDF 업로드 → 다른 구성원 v1 피드백 → v2(DOCX) 업로드 → v1 선택 | v1 피드백 유지, 버전별 분리 |
| 10 | 오너 확정·불변성 | 오너가 v1(최신 아님) 확정. 구성원 화면 확인. Console로 `POST …/versions/1/feedbacks` | 확정 배너, 업로드·피드백 폼 없음, API 409 |
| 11 | 미리보기·다운로드 | PDF 미리보기, DOCX 미리보기, DOCX 다운로드 | PDF 표시, DOCX는 "사용할 수 없음" 안내, 다운로드 파일명 한글 유지 |
| 12 | 재배포 후 유지 | backend Redeploy → 다시 로그인 | 문서·버전·피드백·파일 유지(세션은 재로그인) |
| 13 | 백업·복구 | §7 백업 실행 → 로컬로 옮겨 복구 리허설(로컬 PostgreSQL) | 복구본에서 최종본 다운로드 동일 |
| 14 | 신뢰 프록시 | 아래 절차 | 결과에 따라 hops 결정 |

#14 절차(확인 전까지 `DOCFLOW_TRUSTED_PROXY_HOPS`는 0 유지):
1. 로그인 화면 Console: `await fetch("/api/auth/csrf"); fetch("/api/auth/login",{method:"POST",headers:{"Content-Type":"application/json","X-XSRF-TOKEN":decodeURIComponent(document.cookie.match(/XSRF-TOKEN=([^;]+)/)[1]),"X-Forwarded-For":"6.6.6.6"},body:JSON.stringify({email:"nobody@example.com",password:"wrong-password"})}).then(r=>r.status)` → 401.
2. backend 로그 `Login failed: clientIp=unknown`(hops 0) 확인.
3. 임시로 `DOCFLOW_TRUSTED_PROXY_HOPS=1` → 재배포 → 1을 다시 실행.
4. 로그의 `clientIp`가 **내 공인 IP**(검색 "what is my ip")이면 1 유지. `6.6.6.6`이거나 내부 주소(10.x, fd..)이면 즉시 0으로 되돌린다.

## 10. 배포 실패 대응과 롤백

| 상황 | 대응 |
| --- | --- |
| 빌드 실패 | 이전 배포가 계속 동작. Build 로그 확인 후 수정 커밋 |
| 기동·헬스체크 실패 | 이전 배포 유지. Deploy 로그의 `Caused by:`(DB 연결, 변수 누락, Flyway) 확인 |
| 배포 후 기능 오류 | 서비스 → Deployments → 이전 성공 배포 → **Rollback**(또는 Redeploy) |
| 새 마이그레이션 후 롤백 | 이전 앱은 미래 마이그레이션을 무시하고 기동한다(`*:future` 무시 기본값). V11·V12(테이블·열 추가만)는 이전 앱(V10)이 V12 DB에서 정상 기동함을 로컬에서 확인했다(2026-11-25). 일반적으로는 스키마 호환이 보장되지 않으므로 배포 전 백업 → 문제 시 백업으로 복구 후 이전 배포 |
| 코드 롤백 방법 | Railway Deployments에서 이전 성공 배포를 Redeploy(Rollback)한다. `production`에 직접 커밋하거나 강제 push하지 않는다. 수정은 `main`에서 PR로 검증한 뒤 정상 절차(§1.1)로 다시 배포한다 |
| 데이터 손상·삭제 | 서비스 일시 중지 → §7 복구 → 검증 후 재개 |
| 비밀 노출 의심 | 해당 변수 교체(DB 비밀번호는 Postgres 재설정 후 참조 변수 자동 반영), 모든 세션 무효화는 backend 재배포로 가능 |

## 11. 로그인·가입 시도 제한 분석 (#37 재검토)

현재 정책(15분 창): hops=0이면 이메일당 실패 5회 → 429. hops≥1이면 (IP, 이메일) 5회, IP 20회, 계정 50회. 가입은 이메일 5회, IP 10회(IP를 알 때), 전체 100회.

| 위험 | 분석 | 분류 |
| --- | --- | --- |
| 계정 잠금 악용 (hops=0, 공개 직후 기본) | 피해자 이메일을 아는 공격자가 5번 틀리면 피해자는 15분간 로그인 불가. 반복하면 지속 가능 | 초대 기반 소규모 공개에서는 **수용 가능한 알려진 제약**(데이터 노출 없음, 가용성만 영향). 공개 후 hops 확인 시 완화 |
| 계정 잠금 악용 (hops=1) | (IP, 이메일) 잠금은 다른 IP에 영향 없음. 계정 50회 상한은 공격자가 IP 50개 이상(프록시·봇넷)을 쓰면 15분 잠금 가능 | 추후 개선: 이전에 로그인 성공한 IP는 계정 상한에서 제외, 또는 상한 도달 시 차단 대신 지연 |
| IP 제한 우회 | 신뢰 경계 밖 `X-Forwarded-For`는 무시(위조 불가). 대신 IP를 여러 개 쓰면 IP 상한은 우회되고 계정 상한(50)과 (IP,이메일) 5회만 남음 | 설계상 한계. 강한 비밀번호(10자 이상)와 함께 수용 |
| 메모리 기반 한계 | 재시작 시 카운터 초기화. 키 10,000개 초과 시 오래된 키를 버리므로 대량의 다른 이메일·IP로 카운터를 밀어낼 수 있음(1회 우회에 약 1만 요청 필요) | 추후 개선: 차단 중인 키는 버리지 않기. 다중 인스턴스 시 공유 저장소 필요 |
| 로그인 요청 폭주로 CPU 고갈 | 실패도 bcrypt 비교(존재하지 않는 계정 포함)를 수행해 초당 수십 건이면 단일 인스턴스 CPU가 포화될 수 있음. Railway에는 기본 WAF·요청 제한 없음 | **공개 전 결정 필요**: 소규모 초대 공개로 수용하고 CPU 알림·비용 상한으로 감시하거나, Cloudflare 등 앞단 요청 제한 추가(별도 작업·승인) |
| 프록시 헤더 신뢰 | Next.js는 헤더를 덧붙이지 않고 그대로 전달(로컬 확인). 신뢰는 Railway 엣지가 실제 IP를 덧붙이는지에 달림 | **공개 전 필수 절차**: §9-14 확인 전 hops=0 유지 |
| 단일 인스턴스 적절성 | 세션·제한·볼륨 모두 단일 인스턴스 전제. 초기 사용자 규모에서 적절 | Replicas 1 고정 |

결론: 코드 수정이 필요한 공개 차단 결함은 없다. 공개 전 필수는 (1) hops=0으로 시작해 §9-14로 확인, (2) 로그인 폭주 대응 방침 결정(수용 + 감시 / 앞단 제한), (3) 첫 백업·복구 리허설이다.

## 12. 알려진 제약

- 재배포·재시작 시 로그인 세션 초기화(데이터 유지). Spring Session JDBC는 의존성 1개 + 세션 테이블, 약 2~3시간 + 요청당 DB 조회 증가. 초기 공개에 필수 아님.
- 시도 제한·세션은 단일 인스턴스 메모리 기준.
- Hobby 볼륨 5GB 상한, Office 문서는 운영에서 미리보기 대신 다운로드 안내.

## 13. Backend 컨테이너 실행 검증 (#43)

CI의 `Docker images` job은 이미지 빌드와 비root·`.env` 미포함만 확인한다. 컨테이너 실행은 #45에서 추가한 `Backend container run (smoke test)` job(`scripts/ci/backend-container-smoke.sh`)이 확인한다(13.3).

### 13.1 클라우드 entrypoint 시뮬레이션 (2026-11-11, Docker 아님)

Claude Code 클라우드에는 Docker daemon이 없어, 같은 Ubuntu 24.04 호스트에서 **실제 `docker-entrypoint.sh`와 같은 jar**를 이미지와 같은 경로(`/app`, `/data`)로 격리된 마운트 네임스페이스에서 root로 실행했다(JRE는 21, 이미지는 25).

| 항목 | 결과 |
| --- | --- |
| PostgreSQL 16 연결, Flyway V1~V9 | `Successfully applied 9 migrations` |
| `/actuator/health` | `UP` |
| 소유권 변경 | root 소유로 준비한 `/data`·`/data/lost+found/marker`가 docflow(uid 999) 소유로 변경 |
| 비root 전환 | java 프로세스 uid 999(docflow) |
| `/data/storage` 쓰기·읽기 | API로 관리자 가입·프로젝트·PDF 업로드 → 저장 파일 docflow 소유, 다운로드 원본과 동일 |
| 재시작 후 유지 | SIGTERM 후 재기동: `Schema "public" is up to date`, 재로그인 후 같은 파일 다운로드 동일, uid 999 유지 |

한계: 실제 이미지(Temurin 25 JRE, 이미지 내부 사용자 생성)와 Docker 볼륨 마운트는 검증하지 않았다 → 13.3 CI 실제 컨테이너 검증으로 보완했다. 이 시뮬레이션에서 본 `pg_dump` 16은 호스트 버전이었고, 당시 실제 이미지는 18.6이었다(§7.1).

### 13.2 Docker Desktop(Windows PowerShell) 실행 검증 명령

테스트 전용 이름(`docflow-smoke-*`)만 만들고 마지막에 지운다. 개발 DB·볼륨은 건드리지 않는다.

```powershell
cd <저장소 경로>\Docflow
docker network create docflow-smoke
docker run -d --name docflow-smoke-db --network docflow-smoke -e POSTGRES_DB=docflow -e POSTGRES_USER=docflow -e POSTGRES_PASSWORD=smoke-only-password postgres:16
docker build -t docflow-backend:smoke backend
docker volume create docflow-smoke-data
# Railway처럼 root 소유 볼륨을 흉내 낸다(빈 볼륨에 root 파일 생성)
docker run --rm -v docflow-smoke-data:/data alpine sh -c "mkdir -p /data/lost+found && echo root > /data/lost+found/marker && chown -R 0:0 /data"
docker run -d --name docflow-smoke-backend --restart on-failure --network docflow-smoke -p 18080:8080 -v docflow-smoke-data:/data `
  -e DB_URL=jdbc:postgresql://docflow-smoke-db:5432/docflow -e DB_USERNAME=docflow -e DB_PASSWORD=smoke-only-password `
  -e DOCFLOW_BOOTSTRAP_ADMIN_CODE=Smoke-Bootstrap-Code-0123456789 `
  -e SESSION_COOKIE_SECURE=false -e DOCFLOW_ALLOW_INSECURE_COOKIES=true docflow-backend:smoke

function Wait-Up { for ($i=0; $i -lt 60; $i++) { try { if ((Invoke-RestMethod http://localhost:18080/actuator/health).status -eq 'UP') { return 'UP' } } catch {}; Start-Sleep 2 }; 'TIMEOUT' }
Wait-Up                                                                        # 기대: UP
docker logs docflow-smoke-backend 2>&1 | Select-String "Successfully applied|bootstrap invitation is ready"   # 기대: 9 migrations, ready(코드 원문 없음)
docker exec docflow-smoke-backend sh -c "id docflow; grep -E '^(Uid|Gid):' /proc/1/status"  # 기대: PID 1(java) Uid가 docflow uid
docker exec docflow-smoke-backend ls -ln /data /data/lost+found                # 기대: 모두 docflow uid 소유
docker exec -u docflow docflow-smoke-backend sh -c "echo probe > /data/storage/probe.txt && cat /data/storage/probe.txt"  # 기대: probe
docker restart docflow-smoke-backend; Wait-Up                                  # 기대: UP
docker exec docflow-smoke-backend cat /data/storage/probe.txt                  # 기대: probe (재시작 후 유지)
docker logs docflow-smoke-backend 2>&1 | Select-String "up to date"            # 기대: Schema "public" is up to date
docker exec docflow-smoke-backend sh -c "pg_dump --version && pg_restore --version"  # 기대: 둘 다 16.x (noble 기반 이미지)

# 정리(테스트 전용 리소스만)
docker rm -f docflow-smoke-backend docflow-smoke-db; docker volume rm docflow-smoke-data; docker network rm docflow-smoke
```

`--restart on-failure`는 DB보다 먼저 뜨면 재시도하도록 한 것이다(Railway Restart policy와 같은 역할). `DOCFLOW_ALLOW_INSECURE_COOKIES=true`는 로컬 HTTP 확인 전용이며 Railway에는 넣지 않는다.

### 13.3 CI 실제 컨테이너 검증 (#45)

job `Backend container run (smoke test)`가 PR·push마다 실제 backend 이미지를 빌드해 실행한다. 이 실행 전용 네트워크·PostgreSQL 16 컨테이너 2개·볼륨만 만들고 끝나면 지운다. 운영 기본값(prod 프로파일, Secure 쿠키)을 그대로 쓴다.

| 단계 | 확인 내용 | 결과 (2026-11-17, run 44045682347) |
| --- | --- | --- |
| 1 | 이미지 Java·OS·`pg_dump`/`pg_restore` 주 버전(16이어야 함) | Java 25.0.5, Ubuntu 24.04.5 LTS, 16.15 / 16.15 |
| 2 | PostgreSQL 16 테스트 컨테이너 | 16.16 |
| 3 | root 소유 볼륨 연결 | 통과 |
| 4 | backend 기동 | 통과 |
| 5 | Flyway | 9개 적용, 실패 0 |
| 6 | health, 실행 Java, 부트스트랩 코드 비노출 | `UP`, `using Java 25.0.5 with PID 1 (... started by docflow ...)` |
| 7 | 비root | PID 1 java, Uid 999(docflow), `/data` 전체 docflow 소유 |
| 8 | 볼륨 쓰기·읽기, API 업로드·다운로드 | 통과(sha256 동일, 세션 쿠키 Secure) |
| 9 | 재시작 후 유지 | `Schema "public" is up to date`, 파일·DB 유지, 이전 세션 401 |
| 10 | §7 백업 → 별도 빈 PostgreSQL 16 복원 → 데이터 비교, 파일 저장소 백업·복원 | SQL 오류 없음, 9개 테이블·8개 시퀀스 동일, 파일 2개 동일 |

수정 전(2026-11-11, run 120)에는 1~9단계가 통과하고 10단계가 18.6 클라이언트 때문에 실패했다(§7.1).
