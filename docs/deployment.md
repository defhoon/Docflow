# 공개 배포 가이드 (Railway) — #27

> 상태: 준비 완료, **미배포**. Railway 계정·유료 리소스 생성과 외부 공개는 사용자 승인 후 사용자가 직접(또는 승인 후 함께) 진행한다.
> 결정 배경은 [ADR-0007](./adr/0007-deployment-railway.md). 비밀값은 Railway 화면의 Variables에만 입력하고 Git·채팅에 붙여넣지 않는다.

## 1. 구성

```text
브라우저 ──HTTPS──▶ frontend (Next.js standalone, 공개 도메인)
                       │ /api/*  rewrite (비공개 네트워크)
                       ▼
                    backend (Spring Boot, 공개 도메인 없음) ──▶ PostgreSQL 16 (비공개)
                       │
                       └─ 볼륨 /data  (문서 원본 /data/storage)
```

## 2. 예상 비용 (승인 필요)

Railway 공식 요금(2026-11 확인): Hobby $5/월(사용량 크레딧 $5 포함), RAM $10/GB·월, CPU $20/vCPU·월, 볼륨 $0.15/GB·월, egress $0.05/GB. 실제 요금은 [railway.com/pricing](https://railway.com/pricing)에서 다시 확인한다.

| 리소스 | 가정 | 월 추정 |
| --- | --- | --- |
| backend | 상시 RAM ≈0.5GB, CPU 낮음 | $5~7 |
| frontend | RAM ≈0.2GB | $2~3 |
| PostgreSQL | RAM ≈0.25GB | $2.5~3 |
| 볼륨 | 1~5GB | $0.15~0.75 |
| 합계 | 소규모 사용 | **약 $10~15 (Hobby 크레딧 $5 차감 후 추가 $5~10)** |

## 3. 환경 변수 (이름만, 값은 Railway에서 입력)

| 서비스 | 변수 | 값 / 설명 |
| --- | --- | --- |
| backend | `SPRING_PROFILES_ACTIVE` | `prod` (이미지 기본값) |
| backend | `DB_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` (Railway 참조 변수) |
| backend | `DB_USERNAME` / `DB_PASSWORD` | `${{Postgres.PGUSER}}` / `${{Postgres.PGPASSWORD}}` (참조 변수, 직접 입력 금지) |
| backend | `DOCFLOW_STORAGE_DIR` | `/data/storage` (기본값) |
| backend | `DOCFLOW_PREVIEW_OFFICE_ENABLED` | 설정하지 않음(기본 `false`). 격리 검증 전 켜지 않는다 |
| backend | `SERVER_PORT` | `8080` (기본값) |
| frontend | `BACKEND_URL` | `http://<backend 서비스명>.railway.internal:8080` — **빌드 시** 필요(Railway는 서비스 변수를 빌드에도 전달) |
| frontend | `PORT` | Railway가 주입 |

## 4. 배포 절차 (사용자 승인 후)

1. Railway 프로젝트 생성 → GitHub 저장소 연결.
2. PostgreSQL 16 추가. **TCP Proxy(공개 접속)를 켜지 않는다.**
3. backend 서비스: Root Directory `backend`, Dockerfile 빌드. 볼륨 추가, Mount path `/data`. **Public Networking(도메인)을 만들지 않는다.** Healthcheck path `/actuator/health`.
4. frontend 서비스: Root Directory `frontend`, Dockerfile 빌드, Variables에 `BACKEND_URL`. Public Networking에서 도메인 생성(HTTPS 자동).
5. backend 배포 로그에서 `One-time administrator invitation code (valid PT24H): XXXX-...`를 확인 → frontend 도메인 `/signup`에서 그 코드로 관리자 가입(24시간 안). 로그는 프로젝트 구성원만 볼 수 있어야 한다.
6. 관리자 메뉴에서 사용자별 초대코드 발급 → 전달.
7. 공개 전 점검표(§5) 실행.

## 5. 공개 전 점검표

| 항목 | 방법 | 클라우드 사전 검증 |
| --- | --- | --- |
| HTTPS·Secure 쿠키 | 브라우저 개발자 도구에서 `JSESSIONID`·`XSRF-TOKEN`의 Secure, HttpOnly(세션) 확인 | prod 프로파일 테스트로 Secure 설정 확인 |
| Backend·DB 비공개 | backend에 공개 도메인 없음, Postgres TCP Proxy 꺼짐 | — (Railway 화면에서 확인) |
| 인증·인가·CSRF | 비로그인 `/api/projects` 401, CSRF 헤더 없는 POST 403 | standalone 프록시 경유 401/403 확인 |
| Actuator | `/backend/env` 등 404, health만 응답 | 확인 |
| Office 변환 꺼짐 | DOCX 미리보기 503, 다운로드 200 | 확인 |
| 업로드 용량 | 20MiB 이하 통과, 초과 413 `FILE_TOO_LARGE` | standalone 프록시에서 19MiB 본문 전달, 22MiB 413 JSON 확인 |
| Flyway | 첫 배포 로그 `Successfully applied 9 migrations` | 빈 DB에 prod 프로파일 기동으로 확인 |
| 재배포 후 데이터 유지 | 문서 업로드 → Redeploy → 목록·다운로드 확인 | 로컬 재시작으로 DB·파일 유지 확인(세션은 재로그인) |
| 백업·복구 | §6을 1회 실행 | 미실행(Railway 필요) |

## 6. 백업·복구

볼륨은 백업이 아니다. 공개 전에 아래를 1회 실행해 복구까지 확인한다.

### 6.1 DB

- 1순위: Railway PostgreSQL의 백업 기능(요금제별 제공 여부 확인 필요, 미검증).
- 수동(공개 접속 없이): `railway ssh --service backend`로 backend 컨테이너에 접속해 비공개 네트워크로 덤프한다.

```bash
# backend 컨테이너 안에서 (DB 변수는 이미 주입되어 있음, 비밀번호를 화면에 출력하지 않는다)
PGPASSWORD="$DB_PASSWORD" pg_dump --format=custom --no-owner \
  --host "$(echo "$DB_URL" | sed -E 's#jdbc:postgresql://([^:/]+).*#\1#')" \
  --username "$DB_USERNAME" --dbname "$(echo "$DB_URL" | sed -E 's#.*/##')" \
  --file /data/backup/docflow-$(date +%Y%m%d%H%M).dump
```

  덤프는 볼륨에 임시로 두고, 로컬로 옮긴 뒤(예: `railway ssh` 세션에서 `base64` 출력 후 로컬 복원, 또는 Railway CLI 파일 전송 기능) 볼륨에서 삭제한다. 덤프 파일은 비밀정보로 취급한다.

- 복구: 빈 DB에 `pg_restore --clean --if-exists --no-owner --dbname ...`. 복구 후 backend 재시작 → Flyway 검증 통과 확인.

### 6.2 파일

- 원본은 `/data/storage/documents/<UUID>`. DB `document_versions.storage_key`와 1:1이다. DB와 같은 시점에 백업한다(DB 먼저 덤프 → 파일 압축).

```bash
tar -C /data -czf /data/backup/storage-$(date +%Y%m%d%H%M).tar.gz storage
```

- 복구: 같은 경로로 압축 해제 → 문서 다운로드로 확인. 파일이 없으면 다운로드가 `500 DOCUMENT_FILE_UNAVAILABLE`로 표시된다(SPEC-05).

## 7. 운영 중 알려진 제약

- 재배포 시 로그인 세션 초기화(메모리 세션).
- 로그인·가입 시도 제한은 인스턴스 메모리 기준(재시작 시 초기화).
- Hobby 볼륨 5GB 상한.
- Office 문서는 미리보기 대신 원본 다운로드 안내.
