# DocFlow

Document Review Workflow Management System

문서 등록 → 검토 요청 → 피드백 → 수정 → 재검토 → 승인 과정을 관리하는 시스템이다. 요구사항과 MVP 범위는 [`spec.md`](./spec.md)를 따른다.

> **현재 단계 (7주차):** SPEC-AUTH(세션 기반 로그인·로그아웃·현재 사용자 조회)를 구현 중이다([Issue #4](https://github.com/defhoon/Docflow/issues/4), [`specs/00-auth.md`](./specs/00-auth.md)). 문서·검토·피드백 등 비즈니스 기능은 아직 구현되지 않았다.

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
