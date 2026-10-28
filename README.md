# DocFlow

Document Review Workflow Management System

문서 등록 → 검토 요청 → 피드백 → 수정 → 재검토 → 승인 과정을 관리하는 시스템이다. 요구사항과 MVP 범위는 [`spec.md`](./spec.md)를 따른다.

> **현재 단계 (7주차):** SPEC-AUTH(세션 기반 로그인·로그아웃·현재 사용자 조회)는 병합되었다([`specs/00-auth.md`](./specs/00-auth.md)). SPEC-01(프로젝트 생성·내 프로젝트 목록·상세 조회)은 구현·검증을 마치고 PR [#10](https://github.com/defhoon/Docflow/pull/10)으로 `main`에 병합되었다([Issue #6](https://github.com/defhoon/Docflow/issues/6), [`specs/01-project-create.md`](./specs/01-project-create.md)). SPEC-02(프로젝트 구성원 관리)는 PR [#14](https://github.com/defhoon/Docflow/pull/14)로 `main`에 병합되었다([Issue #13](https://github.com/defhoon/Docflow/issues/13), [`specs/02-project-members.md`](./specs/02-project-members.md)). SPEC-03(문서 등록·목록·상세)은 `feat/15/document-create` 브랜치에서 구현과 자동·클라우드 검증을 마쳤고 Windows E2E를 남겨 두고 있다([Issue #15](https://github.com/defhoon/Docflow/issues/15), [`specs/03-document-create.md`](./specs/03-document-create.md)). 버전 추가·다운로드·검토·피드백 기능은 아직 구현되지 않았다.

## 기술 스택

| 구분 | 기술 |
| --- | --- |
| Frontend | Next.js 16.4, React 19.3, TypeScript 5.9, Tailwind CSS 4, Vitest |
| Backend | Java 25, Spring Boot 4.1.1, Spring Data JPA, Flyway, Gradle 9.8 (Groovy DSL) |
| Database | PostgreSQL 16 |

자세한 버전과 선택 이유는 [`docs/adr/0001-technology-stack.md`](./docs/adr/0001-technology-stack.md)를 참고한다.

## 사전 준비

- JDK 25 — Gradle이 JDK를 자동으로 다운로드하지 않으므로 직접 설치한다 (예: Eclipse Temurin 25). Java 21에서 변경된 이유는 [ADR-0004](./docs/adr/0004-java-25-upgrade.md) 참고
- Node.js 22, npm
- Docker (Docker Compose v2) — 로컬 PostgreSQL 실행용

## 로컬 실행

### 1. 환경변수 설정

```bash
cp .env.example .env
# .env의 POSTGRES_PASSWORD, DB_PASSWORD를 같은 값으로 변경
```

| 변수 | 사용처 | 설명 |
| --- | --- | --- |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT` | docker-compose | PostgreSQL 컨테이너 설정 |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Backend | JDBC 연결 정보 (`DB_PASSWORD`는 필수, 기본값 없음) |
| `BACKEND_URL` | Frontend | Next.js rewrite 대상 (기본 `http://localhost:8080`) |
| `DEV_SEED_PASSWORD` | Backend (`local` 프로파일) | 개발 계정 공통 비밀번호. 비어 있으면 계정을 만들지 않음 |
| `SESSION_COOKIE_SECURE` | Backend | 세션·CSRF 쿠키 `Secure` 속성 (로컬 `false`, 운영 `true`) |
| `DOCFLOW_STORAGE_DIR` | Backend | 업로드 문서 파일 저장 루트. 지정하지 않으면 `backend/`에서 실행할 때 `backend/storage`(Git 제외). 빈 값이면 Backend가 시작을 거부하므로 쓰지 않을 때는 `.env`에서 주석 처리한다 |

`.env`는 Git에 포함하지 않는다.

**`.env` 자동 로딩 범위**

- Backend는 `backend/src/main/resources/application.yml`의 `spring.config.import: optional:file:../.env[.properties]` 설정으로 `.env`를 읽는다.
- 경로 `../.env`는 **실행 시 작업 디렉터리 기준**이다. `backend/`에서 `./gradlew bootRun`, `./gradlew test`, `./gradlew build`를 실행하면 Gradle의 작업 디렉터리가 `backend/`이므로 루트 `.env`가 로딩된다.
- 다른 작업 디렉터리(예: IDE 실행 설정이 저장소 루트를 가리키는 경우)에서 실행하면 `.env`를 찾지 못한다. `optional:`이므로 오류 없이 넘어가지만 `DB_PASSWORD`가 없어 DB 연결이 실패한다. 이때는 IDE의 작업 디렉터리를 `backend`로 지정하거나 환경변수를 직접 설정한다.
- OS 환경변수가 설정되어 있으면 `.env` 값보다 우선한다.
- `.env`는 Java properties 형식으로 해석된다. 값에 따옴표를 붙이지 말고(따옴표까지 값에 포함됨), 비밀번호에 `\` 문자를 쓰지 않는다(이스케이프 문자로 처리됨).
- Frontend(Next.js)는 루트 `.env`를 읽지 않는다. `BACKEND_URL`을 바꿀 때만 셸 환경변수로 지정한다(기본값 `http://localhost:8080`).

### 2. PostgreSQL 실행

```bash
docker compose up -d
docker compose ps        # STATUS가 healthy인지 확인
```

### 3. Backend 실행

```bash
cd backend
./gradlew bootRun --args='--spring.profiles.active=local'   # 루트 .env를 자동으로 읽는다
curl http://localhost:8080/actuator/health   # {"status":"UP", ...}
```

`local` 프로파일로 실행하면 `.env`의 `DEV_SEED_PASSWORD`로 개발 계정을 만든다(이미 있으면 건너뛰고, 기존 비밀번호는 바꾸지 않는다). 프로파일 없이 실행해도 서버는 동작하지만 계정이 생성되지 않는다.

| 개발 계정 | 표시 이름 | 비밀번호 |
| --- | --- | --- |
| `author@docflow.local` | Author | `.env`의 `DEV_SEED_PASSWORD` |
| `reviewer@docflow.local` | Reviewer | `.env`의 `DEV_SEED_PASSWORD` |

Author/Reviewer 구분은 테스트 시나리오용이며 권한 차이는 없다(전역 Role 없음).

### 4. Frontend 실행

```bash
cd frontend
npm install
npm run dev              # http://localhost:3000
```

브라우저에서 `http://localhost:3000`을 열면 로그인 화면(`/login`)으로 이동한다. 개발 계정으로 로그인하면 `/`에 사용자 정보, 로그아웃 버튼, Backend·Database 연결 상태(UP / DOWN / 연결 오류)가 표시된다.

#### 프로젝트 화면 (SPEC-01, SPEC-02)

로그인이 필요하며, 로그인하지 않았으면 로그인 화면으로 이동한다.

| 경로 | 화면 |
| --- | --- |
| `/projects` | 내가 Owner이거나 구성원(Member)인 프로젝트 목록 (최신순, 내 역할 표시). `/`의 "내 프로젝트" 링크로 이동 |
| `/projects/new` | 프로젝트 생성 (이름 필수 1~100자, 설명 선택 0~1000자). 성공하면 상세 화면으로 이동 |
| `/projects/{projectId}` | 프로젝트 상세 (이름, 설명, Owner, 생성일)와 구성원 섹션. 구성원이 아닌 사용자의 접근과 없는 프로젝트는 모두 "프로젝트를 찾을 수 없습니다." |

프로젝트 Owner는 로그인한 사용자로 자동 지정된다.

**구성원 관리 (SPEC-02):**

- Owner는 상세 화면의 구성원 섹션에서 가입된 사용자의 이메일을 입력해 Member로 추가하고, 확인 후 Member를 제거할 수 있다.
- Member는 프로젝트와 구성원 목록을 조회만 할 수 있다.
- 구성원 목록에는 표시 이름과 역할만 표시되며 이메일은 보이지 않는다.

수동 확인 절차는 [`specs/01-project-create.md`](./specs/01-project-create.md) §10.4, [`specs/02-project-members.md`](./specs/02-project-members.md) §10.4를 참고한다(예: `author@docflow.local`이 `reviewer@docflow.local`을 추가·제거).

#### 문서 화면 (SPEC-03)

| 경로 | 화면 |
| --- | --- |
| `/projects/{projectId}` | 프로젝트 상세의 "문서" 섹션: 문서 목록(최신순, 제목·상태·작성자·최신 버전·등록 시각)과 "문서 등록" 링크 |
| `/projects/{projectId}/documents/new` | 문서 등록: 제목(필수, 1~200자), 설명(선택, 0~1000자), 파일(필수). 성공하면 상세로 이동 |
| `/projects/{projectId}/documents/{documentId}` | 문서 상세: 제목, 상태, 작성자, 등록일, 최신 버전(v1), 원본 파일명, 크기, 업로드 시각, 설명. 다운로드·미리보기는 제공하지 않는다 |

- 프로젝트 Owner와 Member 모두 문서를 등록·조회할 수 있다. 구성원이 아닌 사용자는 "프로젝트를 찾을 수 없습니다."를 본다.
- 허용 형식은 PDF·DOCX·XLSX·PPTX이며 크기는 1바이트~20MB(20,971,520바이트)다. Backend가 확장자와 함께 파일 내용(PDF 최소 구조, OOXML `[Content_Types].xml`)을 검증한다. 형식 검증은 안전성을 보증하지 않으며 악성코드 검사는 하지 않는다.
- 등록한 문서의 상태는 "초안"(`DRAFT`)이며, 이 단계에는 상태 변경·새 버전·삭제 기능이 없다.
- 업로드 파일은 `DOCFLOW_STORAGE_DIR`(기본 `backend/storage`) 아래 `documents/<UUID>`로 저장된다. 원본 파일명은 저장 경로에 쓰지 않는다.
- **알려진 제한사항:** 20MB를 넘는 파일은 등록 화면이 전송 전에 막는다. 화면을 거치지 않은 요청 중 요청 전체가 21MiB를 넘는 경우에는 Next.js 경유 응답이 `413` 대신 연결 리셋으로 끝날 수 있다(SPEC-03 §5.6, 결정 D4-1, 미해결). 업로드 중 연결이 끊기면 화면은 파일 크기와 네트워크 상태를 확인하라는 안내를 표시한다.

Windows 수동 확인 절차는 [`specs/03-document-create.md`](./specs/03-document-create.md) §16.2를 참고한다.

브라우저는 `localhost:3000`만 호출한다. `/api/*` 요청은 Next.js rewrite로 Backend에 전달되므로 세션 쿠키(`JSESSIONID`)와 CSRF 쿠키(`XSRF-TOKEN`)는 `localhost:3000`에 저장된다.

## Windows (PowerShell) 실행 절차

사전 준비: JDK 25(설치 시 `JAVA_HOME` 설정), Node.js 22, Docker Desktop(WSL 2 backend), Git for Windows.

```powershell
# 0) 버전 확인
java -version                      # openjdk version "25.0.x"
node -v                            # v22.x
docker version                     # Server 항목이 표시되어야 한다 (Docker Desktop 실행 중)

# JAVA_HOME이 JDK 25를 가리키지 않으면 현재 세션에서만 지정한다 (경로는 실제 설치 경로로 수정)
# $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.x-hotspot"
# $env:Path = "$env:JAVA_HOME\bin;$env:Path"

# 1) 환경변수 파일 (저장소 루트)
Copy-Item .env.example .env
notepad .env                       # POSTGRES_PASSWORD와 DB_PASSWORD를 같은 값으로, DEV_SEED_PASSWORD에 개발 계정 비밀번호 입력

# 2) PostgreSQL
docker compose up -d
docker compose ps                  # STATUS: healthy

# 3) Backend (backend\에서 실행해야 ..\.env가 로딩된다)
cd backend
.\gradlew.bat -version            # Gradle 9.8.1
.\gradlew.bat clean build         # 컴파일 + 테스트 (PostgreSQL 실행 필요)
.\gradlew.bat bootRun --args="--spring.profiles.active=local"   # 개발 계정 생성(local 프로파일)
# 대안: $env:SPRING_PROFILES_ACTIVE = "local"; .\gradlew.bat bootRun
# 다른 PowerShell 창에서:
curl.exe http://localhost:8080/actuator/health    # {"status":"UP",...}

# 4) Frontend (새 PowerShell 창, 저장소 루트 기준)
cd frontend
npm ci
npm run lint
npm run test
npm run build
npm run dev                        # http://localhost:3000
```

`.env`를 쓰지 않거나 IDE 등 다른 작업 디렉터리에서 실행할 때는 PowerShell 환경변수로 지정한다. 현재 PowerShell 창에만 적용된다.

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/docflow"
$env:DB_USERNAME = "docflow"
$env:DB_PASSWORD = "<.env의 POSTGRES_PASSWORD와 같은 값>"
cd backend
.\gradlew.bat bootRun
```

참고:

- PowerShell 5.1에서 `curl`은 `Invoke-WebRequest`의 별칭이므로 `curl.exe`를 사용한다.
- Windows에 PostgreSQL이 이미 설치되어 5432 포트를 사용 중이면 `.env`에서 `POSTGRES_PORT=5433`, `DB_URL=jdbc:postgresql://localhost:5433/docflow`로 변경한다.
- 저장소의 `.gitattributes`가 `backend/gradlew`는 LF, `*.bat`은 CRLF로 체크아웃되도록 고정한다.
- 문서 업로드 크기 경계(19·20MB) 확인은 클라우드에서 `npm run build` 후 `npm run start`(프로덕션 모드)로 검증했다. `npm run dev`에서의 동작은 확인하지 않았다. 대용량 업로드는 `next.config.ts`의 `proxyClientMaxBodySize: "25mb"`(Next.js rewrite 본문 상한)에 의존한다.

## 테스트·검증 명령어

```bash
# Backend — PostgreSQL이 실행 중이어야 한다 (docker compose up -d)
cd backend && ./gradlew build          # 컴파일 + 테스트

# Frontend
cd frontend && npm run lint && npm run test && npm run build
```

GitHub Actions(`.github/workflows/ci.yml`)가 push·PR마다 같은 검증을 수행한다.

## 문서

- [`spec.md`](./spec.md): 프로젝트 명세 (Draft v0.2)
- [`CLAUDE.md`](./CLAUDE.md): Claude Code 개발 지침
- [`docs/architecture.md`](./docs/architecture.md): 아키텍처 개요
- [`docs/adr/`](./docs/adr): 아키텍처 결정 기록
- [`specs/`](./specs): 기능별 상세 SPEC (7주차부터 작성)
