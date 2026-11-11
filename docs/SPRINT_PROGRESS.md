# SPRINT_PROGRESS — 24시간 집중 개발 체크포인트

> 세션 재개 순서: `CLAUDE.md` → 이 문서 → `git status` / `git log` → "진행 중" 항목부터 이어서 진행.
> 이 문서는 체크포인트마다 갱신한다. 마지막 갱신 내용이 맨 위 "현재 상태"다.

## 현재 상태

| 항목 | 값 |
| --- | --- |
| 마지막 갱신 | 2026-11-11 체크포인트 12 (Windows 전체 검증 성공, PR #39 생성, main 병합 승인 대기) |
| 현재 브랜치 | `fix/39/windows-test-failures` (`feat/37` 위) |
| 마지막 검증 완료 커밋 | #39 커밋 (클라우드 Backend 419 통과: `.env`에 `SESSION_COOKIE_SECURE=false`를 넣은 Windows 재현 조건과 원래 조건 모두) |
| 기준 브랜치 | `feat/21/document-version-download` (`e22cad5`, CI run 79 green, main 미병합) |
| 배포 준비 상태 | 이미지·운영 프로파일·문서 준비 완료, **미배포**. Railway 리소스 생성·외부 공개는 사용자 승인 필요(docs/deployment.md) |

## Issue · 브랜치 스택

브랜치는 아래 순서로 쌓는다(앞 브랜치 위에서 생성). PR·병합도 이 순서이며 사용자 승인 후에만 진행한다.

| 순서 | Issue | 브랜치 | 내용 | 상태 |
| --- | --- | --- | --- | --- |
| 2026-11-11 | Windows 로컬(사용자) 전체 | Backend `clean build` 성공, Frontend lint·test·tsc·build 성공, 관리자·초대 가입, 프로젝트·구성원, v1/v2 업로드, 버전별 피드백, 과거 버전 확정, FINALIZED 차단, PDF·Office 미리보기·다운로드, LibreOffice 프로세스 정리, 모바일·데스크톱 UI 모두 정상 (`docs/windows-verification.md` §7.1) |
| #39 (2026-11-11) | Windows 로컬(사용자) | 실패했던 2건·설정 테스트 `BUILD SUCCESSFUL in 3m 42s`, `.\gradlew.bat clean build --rerun-tasks` `BUILD SUCCESSFUL in 11m 11s`. 조건: DB 접속 정보를 프로세스 환경 변수로 전달 |
| #39 | 클라우드 / GitHub Actions | Backend 419 통과 / run 100 success(Backend·Frontend·Docker) |
| 0 | #21 | `feat/21/document-version-download` | SPEC-05 버전 조회·다운로드·미리보기 | 구현·CI 완료, Windows 검증 완료(2026-11-11), PR #29 |
| 1 | #22 | `docs/22/collaboration-workflow` | spec.md v0.5, SPEC 재구성, 이 문서 | 완료 (`44cb4fb`), PR #30 |
| 2 | #23 | `feat/23/audit-log` | SPEC-06 활동 이력 | Backend 완료 (`de11cfc`, CI run 80 success), PR #31 |
| 3 | #24 | `feat/24/document-finalize` | SPEC-07 ACTIVE/FINALIZED, 최종 확정 | Backend 완료 (`620e0f7`, CI run 81 success), PR #32 |
| 4 | #25 | `feat/25/version-feedback` | SPEC-08 버전별 Feedback | Backend 완료 (`52ab113`), PR #33 |
| 5 | #26 | `feat/26/invite-signup` | 초대코드 가입, 로그인 시도 제한 | Backend 완료 (`0f10a10`), PR #34 |
| 6 | #27 | `feat/27/deployment-prep` | Dockerfile, 운영 설정, Office 변환 비활성화, 백업 | 완료(미배포), PR #35 |
| 7 | #28 | `feat/28/ui-overhaul` | 디자인 시스템, App Shell, 협업 작업 공간 | 1차 완료, PR #36 |
| 8 | #37 | `feat/37/pre-release-hardening` | 공개 전 보안 강화·통합 검증·Windows/배포 문서 | 완료, PR #38 |
| 9 | #39 | `fix/39/windows-test-failures` | Windows 실패 2건: prod의 `.env` 로딩 차단·비보안 쿠키 기동 거부, 명령 테스트 경로 | 완료, Windows 전체 검증 성공, PR(아래 기록) |

## 완료된 작업

- CP1 (#22): Issue #22~#28, `spec.md` v0.5(새 워크플로·대응표 §9.1), `CLAUDE.md` 개요, `specs/README.md`
- CP2 (#23): `audit_logs`(V6), `AuditLogService.record`(MANDATORY 트랜잭션), 활동 조회 API 3종, 문서·버전 생성 기록
- CP3 (#24): V7 상태 마이그레이션(ACTIVE/FINALIZED, APPROVED 존재 시 중단), 확정 API, 복합 FK·불변 트리거, 동시성 테스트
- CP4 (#25): V8 `feedbacks`, 버전별 작성·조회 API, 확정과 직렬화
- CP5 (#26): V9 초대코드·`system_role`, 가입 API, 관리자 초대 API, 최초 관리자 코드(로그 1회), 로그인·가입 시도 제한(429)
- CP6 (#27): `application-prod.yml`, Office 변환 비활성화·격리 필수 설정, Dockerfile 2종, CI docker 빌드 job, ADR-0007, `docs/deployment.md`(비용·변수·절차·점검표·백업)

- CP7 (#28): 디자인 토큰(`globals.css` @theme), `components/ui`(Button, Card, Field/Input/Textarea/Select, StatusBadge, Dialog, Toast, States, PageHeader/Breadcrumb, icons), AppShell(사이드바·모바일 서랍), '내 작업' 홈(내 프로젝트·최근 활동), 프로젝트 목록(검색·정렬)·상세(문서 상태 필터·구성원·활동), 문서 협업 작업 공간(미리보기·버전 선택·버전별 피드백·새 버전·오너 확정 Dialog·FINALIZED 읽기 전용·활동), 로그인·초대 가입·초대 관리 화면

- CP8 (#37): PR #29~#36 생성(스택 base), 최초 관리자 = 운영자 비밀 코드(로그 노출 제거, 관리자 존재 후 관리자 초대 무효), 신뢰 프록시 홉 기반 IP + (IP,이메일)/IP/계정 제한, 권한·불변성 회귀 테스트, 실제 브라우저 14단계 시나리오·재시작·백업 복구 실행, `docs/windows-verification.md`, `docs/deployment.md` 갱신

- CP10 (#39): Windows `clean build` 2건 실패 원인 수정. (1) `application.yml`의 `.env` import가 prod에도 적용되어 로컬 `SESSION_COOKIE_SECURE=false`가 prod 기본값을 덮어씀 → `!prod` 문서로 이동, prod에서 비보안 쿠키면 기동 거부(`ProductionCookieGuard`, 로컬 확인용 `DOCFLOW_ALLOW_INSECURE_COOKIES`만 예외). (2) 명령 테스트의 Linux 경로 리터럴 → 호스트 경로. 회귀 테스트 4개 추가

- CP11 (#39): **Windows Backend 검증 성공(사용자)** — 기존 실패 2건·설정 테스트 `BUILD SUCCESSFUL in 3m 42s`, `.\gradlew.bat clean build --rerun-tasks` `BUILD SUCCESSFUL in 11m 11s`(9 actionable tasks executed). prod 프로파일 테스트의 DB 인증 오류는 `.env`의 DB 값을 프로세스 환경 변수로 전달해 해결(prod의 `.env` 격리는 의도된 동작) → `docs/windows-verification.md` §4.1에 절차 추가

- CP12: 사용자 Windows 전체 검증 성공 기록(`docs/windows-verification.md` §7.1), #39 PR 생성. 다음: 사용자 main 병합 승인 → 병합 순서(아래)

## 진행 중

- 없음. 남은 UI 후보: FileUpload 드래그 앤 드롭 컴포넌트(현재 브라우저 기본 파일 입력에 스타일만 적용), 문서 등록·프로젝트 생성 폼의 Field 컴포넌트 전환

## 변경 파일

- 각 SPEC 문서 §검증 기록과 커밋 참고 (`git log --stat docs/22/collaboration-workflow..feat/27/deployment-prep`)

## 테스트 결과

| 시점 | 환경 | 결과 |
| --- | --- | --- |
| 기준(`e22cad5`) | 클라우드 / CI run 79 | Backend 365, Frontend 228, E2E 19/19 / success |
| #27 | 클라우드 | Backend 398 통과, Frontend Vitest 225·lint·tsc·build 통과 |
| #27 | 클라우드 수동 | prod 프로파일 jar + Next standalone: 최초 관리자 가입→로그인→프로젝트→업로드→DOCX 미리보기 503→다운로드 동일 바이트→피드백→확정, 재시작 후 데이터·파일 유지(세션은 재로그인), 22MiB 413 JSON |
| #28 | 클라우드 | Frontend Vitest 244·lint·tsc·build 통과 |
| #28 | 클라우드 실제 브라우저(Chromium, prod 프로파일 jar + standalone) | 390/768/1440px × 로그인·가입·홈·프로젝트 목록·상세·문서(오너/구성원/확정)·초대 관리: 가로 오버플로 0, 로그인 기본 입력값 없음, 구성원에게 확정·초대 메뉴 없음, UI로 피드백 작성(스크립트 문자열은 텍스트로 표시), PDF 미리보기 iframe 표시, PPTX 미리보기는 운영 비활성 안내, 오너가 Dialog로 확정 → 업로드·피드백 폼 제거 |
| #23, #24 | GitHub Actions | success (run 80, 81) |
| #25~#27 | GitHub Actions | 확인 필요 |

## 미해결 문제

- (해결 2026-11-11) SPEC-05 Windows 검증(AC-VDL-22)·Frontend 자동 검사·실제 미리보기·프로세스 정리: Windows 전체 검증으로 통과
- 테스트 DB 설정 검토: prod 프로파일 테스트는 `.env` 격리 때문에 OS 환경 변수의 DB 정보가 필요. CI는 환경 변수로 주입하므로 문제없음. 코드 변경(테스트 전용 DB 속성 등)은 prod 기본 경로를 우회하게 되어 하지 않고 문서 절차로 해결
- Docker 이미지 빌드는 클라우드에 Docker 데몬이 없어 CI job으로만 검증 예정
- 백업·복구 절차는 Railway 환경에서 미실행
- 재배포 시 세션 초기화(Spring Session JDBC는 새 의존성 → 승인 필요)
- #18: 로컬 standalone에서는 22MiB가 413 JSON으로 응답(재현 안 됨). dev 서버·Windows 재확인 필요
- `spec.md` §16.2: 삭제 기능, 피드백 수정·삭제, 파일 형식 범위 미결정

## 다음 실행 명령 (클라우드 재개 시)

```bash
cd /home/user/Docflow && git status -sb && git log --oneline -5
# PostgreSQL(클라우드 임시) 재시작
su postgres -c "/usr/lib/postgresql/16/bin/pg_ctl -D /tmp/docflow-pg/data -l /tmp/docflow-pg/log -o '-k /tmp/docflow-pg -p 5432' start"
# Backend 테스트 (클라우드는 JDK 21 init script 사용)
cd backend && ./gradlew test
# Frontend
cd frontend && npm run lint && npx tsc --noEmit && npm test -- --run && npm run build
```

## 병합 순서 (사용자 수행, Merge commit만 사용)

1. #29 → `main` 병합 → #30 base를 `main`으로 변경(Edit → base). 이후 #30 → #31 → … → #38 순으로 같은 방식 반복.
2. 각 base 변경 후 CI 재실행 결과 확인 뒤 병합. Squash·Rebase 금지(다음 PR에 이전 커밋이 중복 표시·충돌 위험).
3. 병합된 head 브랜치는 다음 PR base 변경 후 삭제(먼저 삭제해도 GitHub가 자동 재지정하지만 순서를 지키는 편이 안전).

## 다음 작업 우선순위

1. 사용자 main 병합 승인 → 위 "병합 순서"대로 사용자가 병합(Merge commit), 단계마다 base 변경·CI 확인
2. 병합 후 SPEC-05~08·#26 Done 판정과 `specs/README.md`·각 SPEC 검증 기록 갱신
3. 로그인 폭주 대응 방침 결정 → Railway 리소스 생성 승인 → `docs/deployment.md` §9 체크리스트
