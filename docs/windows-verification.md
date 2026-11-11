# Windows 로컬 검증 안내 (#37)

> 대상: SPEC-05 Windows 재검증(AC-VDL-22)과 새 워크플로 전체(가입·업로드·피드백·확정)의 로컬 수동 검증.
> 검증 브랜치: `feat/37/pre-release-hardening` (#21~#28 변경을 모두 포함하는 스택의 맨 위).
> 결과는 아래 §7 표를 채워 알려 주세요. 실행하지 않은 항목은 "미실행"으로 남겨 주세요.

## 0. 우선순위 요약 (이 순서로 진행)

1. 준비·DB: §1 → §2 → §3 (`docker compose up -d`)
2. **SPEC-05 LibreOffice**: §4.1의 preview 테스트 + `Get-Process soffice*` 출력 없음
3. **전체 Backend 테스트**: §4.1 `clean build --rerun-tasks` (실패 0, skipped 6)
4. **전체 Frontend 테스트**: §5.1 `npm run lint`, `npm test`, `npx tsc --noEmit`, `npm run build`
5. **새 협업 워크플로**: §4.2 실행(기본 프로파일이면 Office 미리보기까지) → §6 1~12
6. **UI/UX 실제 화면**: §6 13 + 데스크톱 폭에서 홈·프로젝트·문서 화면 확인
7. §7 표 작성 후 전달

## 1. 준비물

| 도구 | 버전 | 확인 명령 (PowerShell) |
| --- | --- | --- |
| JDK | 25 (Temurin 권장) | `java -version` |
| Node.js | 22 | `node -v` |
| Docker Desktop | 실행 중 | `docker version` |
| LibreOffice | 설치됨 (기존 확인: 26.8.2.2) | `& "C:\Program Files\LibreOffice\program\soffice.com" --version` |
| Git | — | `git --version` |

## 2. 코드와 환경 변수

```powershell
cd <저장소 경로>\Docflow
git fetch origin
git switch feat/37/pre-release-hardening
git pull
Copy-Item .env.example .env -ErrorAction SilentlyContinue   # 이미 있으면 기존 .env 유지
notepad .env
```

`.env`(Git에 올리지 않음)에 값을 넣습니다. 따옴표 없이, `\` 문자 없이 씁니다.

| 변수 | 값 |
| --- | --- |
| `POSTGRES_PASSWORD`, `DB_PASSWORD` | 같은 임의 비밀번호 |
| `SESSION_COOKIE_SECURE` | `false` (로컬 http) |
| `DEV_SEED_PASSWORD` | (선택) local 프로파일 개발 계정용 |

이번 검증에서만 PowerShell 창에 설정하는 변수(파일에 쓰지 않음):

```powershell
# 최초 관리자 초대코드(24~64자). 빈 DB에서 처음 실행할 때만 쓰인다.
$env:DOCFLOW_BOOTSTRAP_ADMIN_CODE = "Local-Windows-Check-Code-2026-XYZ"
# soffice.com이 기본 경로가 아니면:
# $env:DOCFLOW_PREVIEW_SOFFICE_PATH = "D:\LibreOffice\program\soffice.com"
```

## 3. DB

```powershell
docker compose up -d
docker compose ps          # docflow-postgres STATUS: healthy
```

새 워크플로 마이그레이션(V6~V9)이 기존 로컬 DB에 적용됩니다. 기존 로컬 DB에 `APPROVED` 상태 문서가 있으면 V7이 의도적으로 실패합니다(정상 앱에서는 만들 수 없는 값). 깨끗한 상태로 검증하려면 DB 볼륨을 새로 만드세요(로컬 데이터가 지워집니다):

```powershell
docker compose down -v; docker compose up -d
```

## 4. Backend

### 4.1 자동 테스트 (먼저 SPEC-05 Windows 재검증)

```powershell
cd backend
.\gradlew.bat test --tests "com.docflow.document.preview.*" --tests "com.docflow.document.DocumentPreviewIntegrationTest" --tests "com.docflow.document.DocumentPreviewEngineUnavailableIntegrationTest" --rerun --console=plain
Get-Process soffice* -ErrorAction SilentlyContinue   # 출력 없어야 함
.\gradlew.bat clean build --rerun-tasks --console=plain
```

예상: `BUILD SUCCESSFUL`. 전체 테스트 수는 클라우드 기준 415개이며 Windows에서는 Linux/Mac 전용 테스트 6개(심볼릭 링크·셸 스크립트 기반 가짜 엔진, `@EnabledOnOs`)가 skipped로 표시되는 것이 정상입니다(실패 0). 실패 시 `backend\build\reports\tests\test\index.html`을 확인합니다.

### 4.2 실행 (운영과 같은 prod 프로파일)

```powershell
$env:SPRING_PROFILES_ACTIVE = "prod"
$env:DOCFLOW_STORAGE_DIR = "$PWD\storage"            # prod 기본값(/data/storage) 대신 로컬 폴더
$env:SESSION_COOKIE_SECURE = "false"                 # 로컬 http에서 로그인하려면 필요
$env:DB_URL = "jdbc:postgresql://localhost:5432/docflow"
$env:DB_USERNAME = "docflow"
$env:DB_PASSWORD = "<.env의 DB_PASSWORD>"           # prod 프로파일은 .env를 읽지 않는다
.\gradlew.bat bootRun
```

시작 로그에서 확인:
- `Successfully applied N migrations` 또는 `Schema "public" is up to date`
- 빈 DB이면 `Administrator bootstrap invitation is ready (expires at …)` (코드 원문은 로그에 없어야 함)
- `Office preview disabled by configuration` (prod 기본: Office 변환 꺼짐)

Office 미리보기까지 확인하려면 prod 대신 기본 프로파일로 실행합니다(`$env:SPRING_PROFILES_ACTIVE = ""`, `.env`가 로딩됨). 기본 프로파일은 OS 격리 없이 LibreOffice를 실행하며 시작 시 WARN을 남깁니다(로컬 확인용).

## 5. Frontend

### 5.1 자동 검사

```powershell
cd ..\frontend
npm ci
npm run lint          # 오류 0
npm test              # Vitest: 32 files, 244 tests passed (클라우드 기준)
npx tsc --noEmit      # 출력 없음
npm run build         # Route 목록 출력, 오류 없음
```

### 5.2 실행

```powershell
npm run dev        # http://localhost:3000 (Backend가 8080에서 실행 중이어야 함)
```

## 6. 화면 확인 순서와 예상 결과

| # | 동작 | 예상 결과 |
| --- | --- | --- |
| 1 | `/signup`에서 §2의 관리자 코드로 가입 → 로그인 | 사이드바에 "초대 관리" 표시 |
| 2 | 같은 코드로 다시 가입 시도 | "초대코드가 올바르지 않거나 만료되었습니다" |
| 3 | 초대 관리에서 코드 2개 발급 | 코드는 발급 직후에만 보이고 목록에는 표시되지 않음 |
| 4 | 시크릿 창에서 사용자 B·C 가입 | 로그인 화면에 "가입이 완료되었습니다" |
| 5 | 관리자: 프로젝트 생성, B·C를 구성원으로 추가 | 구성원 목록에 오너·구성원 표시 |
| 6 | B: 문서 등록(PDF), C: v1에 피드백 | 피드백이 v1에 표시 |
| 7 | B: 새 버전(DOCX) 업로드 | 최신 v2, v1 피드백은 v1 선택 시 그대로 |
| 8 | DOCX v2 "미리보기" (기본 프로파일 실행 시) | PDF로 표시. prod 프로파일이면 "미리보기를 사용할 수 없습니다" 안내 + 다운로드 가능 |
| 9 | B 화면 | "최종 버전 확정" 영역 없음 |
| 10 | 관리자(오너): v1 선택 → 최종 확정 | 확인 창에 "최신 버전(v2)이 아닙니다" 경고 → 확정 후 초록 배너 |
| 11 | 확정 후 B·C 화면 | 새 버전·피드백 입력 없음, 버전·피드백 조회 가능, v1 다운로드 가능(파일명 한글 유지) |
| 12 | Backend 재시작 후 다시 로그인 | 문서 FINALIZED·피드백·파일 유지(로그인은 다시 필요) |
| 13 | 브라우저 폭 390px 근처(개발자 도구 기기 모드) | 메뉴 버튼·서랍 메뉴, 가로 스크롤 없음 |
| 14 | `Get-Process soffice* -ErrorAction SilentlyContinue` | 미리보기 후 일정 시간 뒤 출력 없음 |

## 7. 결과 기록 양식

| 항목 | 결과 (통과/실패/미실행) | 비고 |
| --- | --- | --- |
| 4.1 preview 테스트 | | |
| 4.1 clean build (테스트 수·skip 수) | | |
| 6-1 ~ 6-14 | | |

## 8. 문제 발생 시 확인할 것

| 증상 | 확인 위치 |
| --- | --- |
| 테스트 실패 | `backend\build\reports\tests\test\index.html`, `backend\build\test-results\test\*.xml` |
| Backend 시작 실패 | bootRun 콘솔: `Caused by:` 줄(DB 연결, Flyway, 설정 값) |
| 로그인 직후 다시 로그인 화면 | `SESSION_COOKIE_SECURE=false`인지, 브라우저 개발자 도구 → Application → Cookies에 `JSESSIONID` |
| 요청 403 `CSRF_INVALID` | 페이지 새로고침 후 재시도, `XSRF-TOKEN` 쿠키 존재 |
| Office 미리보기 503 | Backend 로그 `Office preview disabled: …` 사유, `soffice.com --version` |
| 업로드 실패 | 브라우저 개발자 도구 Network의 응답 `code`, Backend 로그 `storageKey` |
| Frontend 오류 | `npm run dev` 콘솔, 브라우저 Console |
