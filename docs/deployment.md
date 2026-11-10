# 공개 배포 가이드 (Railway) — #27, #37

> 상태: 준비 완료, **미배포**. Railway 계정·유료 리소스 생성과 외부 공개는 사용자 승인 후 진행한다.
> 결정 배경: [ADR-0007](./adr/0007-deployment-railway.md). 비밀값은 Railway Variables(Sealed 권장)에만 입력하고 Git·채팅·이슈에 붙여넣지 않는다.

## 1. 구성과 서비스 목록

```text
브라우저 ──HTTPS──▶ frontend (Next.js standalone, 공개 도메인)
                       │ /api/*  rewrite (비공개 네트워크, X-Forwarded-For는 그대로 전달)
                       ▼
                    backend (Spring Boot prod, 공개 도메인 없음) ──▶ PostgreSQL 16 (비공개, TCP Proxy 끔)
                       │
                       └─ 볼륨 /data  (문서 원본 /data/storage)
```

| Railway 리소스 | 빌드 | 공개 여부 | 비고 |
| --- | --- | --- | --- |
| `frontend` | `frontend/Dockerfile` | 공개 도메인(HTTPS) | `BACKEND_URL` 빌드 변수 필요 |
| `backend` | `backend/Dockerfile` | **공개 도메인 없음** | 볼륨 `/data`, healthcheck `/actuator/health` |
| `Postgres` | Railway 템플릿(16) | **TCP Proxy 끔** | `pg_dump` 16과 주 버전 일치 |
| 볼륨 | backend에 연결 | — | Hobby 상한 5GB/볼륨 |

## 2. 예상 비용 (승인 필요)

Railway 공식 요금(2026-11 확인, [docs.railway.com/pricing](https://docs.railway.com/pricing/plans)): Hobby $5/월(사용량 크레딧 $5 포함), RAM $10/GB·월, CPU $20/vCPU·월, 볼륨 $0.15/GB·월, egress $0.05/GB. 배포 직전 다시 확인한다.

| 리소스 | 가정 | 월 추정 |
| --- | --- | --- |
| backend | 상시 RAM ≈0.5GB(JVM), CPU 낮음 | $5~7 |
| frontend | RAM ≈0.2GB | $2~3 |
| PostgreSQL | RAM ≈0.25GB | $2.5~3 |
| 볼륨 | 1~5GB | $0.15~0.75 |
| 합계 | 소규모 사용 | **약 $10~15 (크레딧 $5 차감 후 추가 $5~10)** |

변동 요인: 메모리는 유휴 중에도 과금, 사용자 증가 시 CPU·egress 증가(다운로드 트래픽), 볼륨 5GB 초과 시 Pro 요금제 필요, 백업 보관 방식에 따른 추가 저장 비용.

## 3. 환경 변수 (이름만, 값은 Railway에서 입력)

| 서비스 | 변수 | 값 / 설명 | 비밀 |
| --- | --- | --- | --- |
| backend | `SPRING_PROFILES_ACTIVE` | `prod` (이미지 기본값) | |
| backend | `DB_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` | |
| backend | `DB_USERNAME` / `DB_PASSWORD` | `${{Postgres.PGUSER}}` / `${{Postgres.PGPASSWORD}}` (참조 변수) | ✔ |
| backend | `DOCFLOW_BOOTSTRAP_ADMIN_CODE` | 최초 관리자 초대코드 24~64자(§4). 관리자 가입 후 **삭제** | ✔ |
| backend | `DOCFLOW_TRUSTED_PROXY_HOPS` | 처음에는 설정하지 않음(0). §5 확인 후 `1` | |
| backend | `DOCFLOW_STORAGE_DIR` | `/data/storage` (기본값) | |
| backend | `DOCFLOW_PREVIEW_OFFICE_ENABLED` | 설정하지 않음(기본 false). 격리 검증 전 켜지 않는다 | |
| frontend | `BACKEND_URL` | `http://<backend 서비스명>.railway.internal:8080` (빌드 시 필요) | |

## 4. 배포·최초 관리자 생성 절차 (승인 후)

1. Railway 프로젝트 생성 → GitHub 저장소 연결.
2. PostgreSQL 16 추가. **TCP Proxy를 켜지 않는다.**
3. backend: Root Directory `backend`, Dockerfile 빌드, 볼륨 `/data`, **Public Networking 없음**, healthcheck `/actuator/health`. Variables에 §3 값.
4. 최초 관리자 코드 만들기(로컬 PowerShell, 화면에만 표시하고 저장하지 않음): `-join ((48..57)+(65..90)+(97..122) | Get-Random -Count 40 | % {[char]$_})` → backend의 `DOCFLOW_BOOTSTRAP_ADMIN_CODE`에 Sealed 변수로 입력.
5. frontend: Root Directory `frontend`, Dockerfile 빌드, `BACKEND_URL`, Public Networking에서 도메인 생성.
6. backend 로그에서 `Administrator bootstrap invitation is ready (expires at …)` 확인(코드 원문은 로그에 없음).
7. `/signup`에서 그 코드로 가입 → 사이드바 "초대 관리" 확인.
8. backend의 `DOCFLOW_BOOTSTRAP_ADMIN_CODE` 삭제 → 재배포. 로그에 `still set` 경고가 없어야 한다. 관리자가 있으면 관리자 초대는 다시 만들어지지 않고 남은 관리자 초대는 폐기된다.
9. 초대 관리에서 사용자별 코드 발급(유효 기간 최소화) → 안전한 경로로 전달.

관리자를 잃은 경우: 사용자 존재 + 관리자 없음 상태에서는 자동 발급하지 않는다(누구나 관리자가 되는 경로 차단). 운영자가 DB에서 `UPDATE users SET system_role='ADMIN' WHERE email=...`로 복구한다(Railway DB 콘솔).

## 5. 시도 제한과 신뢰 프록시 확인 (#37)

- 기본(`DOCFLOW_TRUSTED_PROXY_HOPS` 미설정): 로그인은 이메일 기준 15분 5회 실패 시 429. 공격자가 특정 계정을 최대 15분 잠글 수 있다(알려진 제약).
- `1`로 설정하면 `X-Forwarded-For`의 마지막 값을 클라이언트 IP로 쓰고 (IP, 이메일) 5회 · IP 20회 · 계정 50회/15분으로 제한한다. 다른 IP의 공격자가 계정을 잠글 수 없게 된다.
- **로컬 확인 결과**: Next.js rewrite는 `X-Forwarded-For`를 덧붙이지 않고 그대로 전달한다. 따라서 `1`은 Railway 엣지가 실제 클라이언트 IP를 헤더 끝에 덧붙일 때만 안전하다.
- 배포 후 확인 절차:
  1. 내 PC에서 `curl -H "X-Forwarded-For: 6.6.6.6" -X POST https://<도메인>/api/auth/login …`로 틀린 비밀번호 요청(CSRF 토큰 필요, 브라우저 개발자 도구 Console에서 fetch로 보내도 됨).
  2. 임시로 `DOCFLOW_TRUSTED_PROXY_HOPS=1` 적용 후 backend 로그 `Login failed: clientIp=…`가 **내 공인 IP**이면 유지, `6.6.6.6`이면 즉시 0으로 되돌린다.

## 6. 백업·복구 (로컬에서 실제 실행 검증 완료, Railway 미실행)

볼륨은 백업이 아니다. DB와 파일을 같은 시점에 백업한다(DB 먼저).

```bash
# backend 컨테이너 셸 (railway ssh --service backend)
mkdir -p /data/backup
PGPASSWORD="$DB_PASSWORD" pg_dump --format=custom --no-owner \
  --host "$(echo "$DB_URL" | sed -E 's#jdbc:postgresql://([^:/]+).*#\1#')" \
  --port "$(echo "$DB_URL" | sed -E 's#.*:([0-9]+)/.*#\1#')" \
  --username "$DB_USERNAME" --dbname "$(echo "$DB_URL" | sed -E 's#.*/##')" \
  --file /data/backup/docflow-$(date +%Y%m%d%H%M).dump
tar -C /data -czf /data/backup/storage-$(date +%Y%m%d%H%M).tar.gz storage
```

- 백업 파일은 로컬 안전한 저장소로 옮긴 뒤 볼륨에서 삭제한다(볼륨 용량·노출 최소화). 덤프는 비밀정보로 취급한다.
- 복구: 빈 DB에 `pg_restore --no-owner --dbname <대상>`, 파일은 `tar -C /data -xzf storage-….tar.gz`. backend 시작 시 `Successfully validated 9 migrations`, 문서 다운로드가 원본과 같은지 확인.
- 로컬 검증(2026-11-10, 클라우드): 시나리오 데이터(확정 문서, 피드백, 2개 버전)를 `pg_dump -Fc` + `tar`로 백업 → 새 DB(`pg_restore --no-owner`)·새 디렉터리로 복구 → 같은 jar로 기동 → Flyway 9개 검증 통과, FINALIZED·최종 v1·피드백 유지, 최종본 다운로드 동일 바이트.
- Railway 자체 백업 기능은 요금제별 제공 여부를 확인한다(미확인).

## 7. 배포 후 필수 체크리스트

| 항목 | 방법 | 사전 검증(클라우드) |
| --- | --- | --- |
| HTTPS·Secure 쿠키 | 개발자 도구에서 `JSESSIONID`(HttpOnly, Secure), `XSRF-TOKEN`(Secure) | prod 프로파일 테스트 |
| Backend·DB 비공개 | backend 도메인 없음, Postgres TCP Proxy 꺼짐 | — |
| 최초 관리자 | §4 6~8, 로그에 코드 없음, 변수 삭제 후 경고 없음 | 확인 |
| 인증·인가·CSRF | 비로그인 401, 비구성원 404, 구성원 확정 403, CSRF 없는 POST 403 | 회귀 테스트 + 실제 브라우저 |
| FINALIZED 불변 | 업로드·피드백·재확정 409, 상태 변경 API 없음(405) | 실제 브라우저 시나리오 |
| 저장소 비노출 | `/data/…`, `/storage/…`, 저장 키 경로 요청이 200이 아님 | 회귀 테스트 |
| Office 변환 꺼짐 | DOCX 미리보기 안내, 다운로드 200 | 확인 |
| 업로드 용량 | 20MiB 이하 통과, 초과 413 | standalone 로컬 확인 |
| 신뢰 프록시 | §5 | Next 동작만 확인 |
| 재배포 후 데이터 유지 | 업로드 → Redeploy → 목록·다운로드 | 로컬 재시작 확인 |
| 백업·복구 | §6 1회 실행 | 로컬 확인 |

## 8. 알려진 제약과 개선 비용

- **재배포·재시작 시 로그인 세션 초기화(메모리 세션)**: 데이터 손실은 없고 사용자는 다시 로그인한다. 업로드 중이던 요청은 graceful shutdown으로 대부분 마무리되지만 배포 순간의 요청은 실패할 수 있다(재시도 필요). 개선안 Spring Session JDBC: 의존성 1개 + 세션 테이블 마이그레이션 + 만료 정리, 약 2~3시간과 테스트. 요청마다 DB 조회가 늘고 세션 직렬화 대상(`AuthenticatedUser`) 검토가 필요하다. 초기 공개에는 필수 아님으로 판단.
- 시도 제한·세션은 단일 인스턴스 메모리 기준(수평 확장 시 재설계).
- Hobby 볼륨 5GB 상한.
- Office 문서는 운영에서 미리보기 대신 다운로드 안내.
