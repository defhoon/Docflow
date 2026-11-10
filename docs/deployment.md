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
| `Postgres` | Railway PostgreSQL 템플릿 (주 버전 16 선택·확인) | **TCP Proxy 끔** | backend가 참조 변수로 접속 |
| `backend` | GitHub 저장소, Root Directory `backend`, `backend/Dockerfile` | **Public Networking 없음** | `backend.railway.internal:8080` |
| `frontend` | GitHub 저장소, Root Directory `frontend`, `frontend/Dockerfile` | 공개 도메인(HTTPS) | `BACKEND_URL`로 backend 호출 |
| 볼륨 | backend에 연결, Mount path `/data` | — | Hobby 5GB/볼륨 상한 |

배포 브랜치: PR #29~#37이 `main`에 병합된 뒤 `main`을 연결한다(병합 전 배포가 필요하면 `feat/37/pre-release-hardening`).

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

볼륨은 백업이 아니다. DB → 파일 순서로 같은 시점에 백업한다. 초기 권장 주기: 주 1회 + 마이그레이션 포함 배포 직전.

```bash
# railway ssh --service backend  (backend 컨테이너 셸)
mkdir -p /data/backup
PGPASSWORD="$DB_PASSWORD" pg_dump --format=custom --no-owner \
  --host "$(echo "$DB_URL" | sed -E 's#jdbc:postgresql://([^:/]+).*#\1#')" \
  --port "$(echo "$DB_URL" | sed -E 's#.*:([0-9]+)/.*#\1#')" \
  --username "$DB_USERNAME" --dbname "$(echo "$DB_URL" | sed -E 's#.*/##')" \
  --file /data/backup/docflow-$(date +%Y%m%d%H%M).dump
tar -C /data -czf /data/backup/storage-$(date +%Y%m%d%H%M).tar.gz storage
```

- 백업 파일은 안전한 외부 저장소로 옮긴 뒤 볼륨에서 삭제한다. 덤프는 사용자 정보가 들어 있는 비밀정보다.
- 복구: 빈 DB에 `pg_restore --no-owner --dbname <대상>`, 파일은 `tar -C /data -xzf storage-….tar.gz` → backend 재시작 → `Successfully validated 9 migrations`, 최종본 다운로드 확인.
- 로컬 검증(2026-11-10): 확정 문서·피드백·2개 버전 데이터를 백업 → 새 DB·새 디렉터리로 복구 → Flyway 9개 검증 통과, FINALIZED·피드백 유지, 최종본 동일 바이트.
- Railway 자체 Postgres 백업 기능의 요금제별 제공 여부는 콘솔에서 확인(미검증).

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
- 비용 상한: Workspace → Usage(또는 Billing)에서 **알림 금액**과 **하드 리밋**을 설정한다. 하드 리밋에 도달하면 서비스가 중지될 수 있으므로 알림은 $15, 하드 리밋은 $25 정도로 시작(콘솔에서 기능·동작 확인, 미검증).
- backend 메모리 상한: 서비스 Settings의 리소스 제한에서 1GB 이하로 제한(JVM은 `MaxRAMPercentage=75`).

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
| 새 마이그레이션 후 롤백 | 이전 앱은 미래 마이그레이션을 무시하고 기동하나(`*:future` 무시 기본값) 스키마 호환이 보장되지 않는다. 배포 전 백업 → 문제 시 백업으로 복구 후 이전 배포 |
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
