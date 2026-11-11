# SPRINT_PROGRESS — 24시간 집중 개발 체크포인트

> 세션 재개 순서: `CLAUDE.md` → 이 문서 → `git status` / `git log` → "진행 중" 항목부터 이어서 진행.
> 이 문서는 체크포인트마다 갱신한다. 마지막 갱신 내용이 맨 위 "현재 상태"다.

## 현재 상태

| 항목 | 값 |
| --- | --- |
| 마지막 갱신 | 2026-11-10 체크포인트 1 |
| 현재 브랜치 | `docs/22/collaboration-workflow` |
| 마지막 검증 완료 커밋 | (이 문서 커밋) — 문서 변경만, 코드 변화 없음 |
| 기준 브랜치 | `feat/21/document-version-download` (`e22cad5`, CI run 79 green, main 미병합) |
| 배포 준비 상태 | 미착수 (Issue #27). 유료 리소스·외부 공개는 별도 승인 필요 |

## Issue · 브랜치 스택

브랜치는 아래 순서로 쌓는다(앞 브랜치 위에서 생성). PR·병합도 이 순서이며 사용자 승인 후에만 진행한다.

| 순서 | Issue | 브랜치 | 내용 | 상태 |
| --- | --- | --- | --- | --- |
| 0 | #21 | `feat/21/document-version-download` | SPEC-05 버전 조회·다운로드·미리보기 | 구현·CI 완료, Windows 검증 대기, PR 없음 |
| 1 | #22 | `docs/22/collaboration-workflow` | spec.md v0.5, SPEC 재구성, 이 문서 | 진행 중 |
| 2 | #23 | `feat/23/audit-log` | SPEC-06 활동 이력 | 예정 |
| 3 | #24 | `feat/24/document-finalize` | SPEC-07 ACTIVE/FINALIZED, 최종 확정 | 예정 |
| 4 | #25 | `feat/25/version-feedback` | SPEC-08 버전별 Feedback | 예정 |
| 5 | #26 | `feat/26/invite-signup` | 초대코드 가입, 로그인 시도 제한 | 예정 |
| 6 | #27 | `feat/27/deployment-prep` | Dockerfile, 운영 설정, Office 변환 비활성화, 백업 | 예정 |
| 7 | #28 | `feat/28/ui-overhaul` | 디자인 시스템, App Shell, 협업 작업 공간 | 예정 |

## 완료된 작업

- 체크포인트 1: Issue #22~#28 생성, `spec.md` v0.5(새 워크플로·대응표 §9.1), `CLAUDE.md` 개요, `specs/README.md` 표

## 진행 중

- 없음 (다음: SPEC-06 브랜치 생성 → SPEC 초안 → 구현)

## 변경 파일 (현재 브랜치)

- `spec.md`, `CLAUDE.md`, `specs/README.md`, `docs/SPRINT_PROGRESS.md`

## 테스트 결과

| 시점 | 환경 | 결과 |
| --- | --- | --- |
| 기준(`e22cad5`) | 클라우드 | Backend 365 통과, Frontend 228 Vitest·lint·tsc·build 통과, E2E 19/19 |
| 기준(`e22cad5`) | GitHub Actions | run 79 success |

## 미해결 문제

- SPEC-05 Windows 재실행 결과 대기 (AC-VDL-22)
- #18 Next.js 경유 21MiB 초과 업로드 연결 리셋 (배포 프록시 제한과 관련, #27에서 재확인)
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

## 다음 작업 우선순위

1. SPEC-06 활동 이력: `audit_logs`, 기록 컴포넌트, 문서 생성·버전 등록 기록, 조회 API
2. SPEC-07 최종 확정: 상태 마이그레이션, 확정 API, FINALIZED 업로드 거부
3. SPEC-08 버전별 Feedback
4. #26 초대 가입·로그인 제한 → #27 배포 준비 → #28 UI
