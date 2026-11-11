# specs/ — 기능별 상세 SPEC

이 디렉터리에는 [`spec.md`](../spec.md) §9의 기능 SPEC(SPEC-AUTH, SPEC-01 ~ SPEC-14)을 기능별 파일로 작성한다.
7주차부터 기능별 상세 SPEC을 작성한다.

## 파일 명명 규칙

`spec.md` §11의 예시를 따른다.

```text
specs/
├── 00-auth.md                 # SPEC-AUTH (기능 SPEC보다 먼저 진행)
├── 01-project-create.md
├── 02-project-members.md
├── 03-document-create.md
├── ...
└── 14-ai-version-summary.md   # Post-MVP
```

## SPEC과 Issue·브랜치·PR

하나의 기능 SPEC은 하나의 GitHub Issue, 하나의 브랜치, 하나의 PR로 진행한다(`CLAUDE.md` §2, 브랜치·커밋·PR 규칙은 §7).

```text
GitHub Issue (#N)  →  브랜치 feat/N/description  →  specs/NN-name.md 작성·승인  →  구현·테스트  →  PR (Closes #N)
```

- SPEC 문서 상단에 관련 Issue 번호를 기록한다.
- SPEC 문서 작성과 기능 구현은 같은 브랜치에서 진행하되, 구현은 SPEC 승인 이후 시작한다.

| SPEC | 파일 | Issue | 상태 |
| --- | --- | --- | --- |
| SPEC-AUTH 사용자 인증 | `00-auth.md` | #4 | 구현·병합 완료 |
| SPEC-01 프로젝트 생성·조회 및 소유권 | `01-project-create.md` | #6 | 구현·검증 완료, main 병합 완료 (PR #10) |
| SPEC-02 프로젝트 구성원 관리 | `02-project-members.md` | #13 | 구현·검증 완료, main 병합 완료 (PR #14) |
| SPEC-03 문서 등록 | `03-document-create.md` | #15 | Done(2026-11-03, 변경된 완료 기준 기준), main 병합 완료 (PR #16, 2026-11-03). 후속: Windows 실환경 수동 E2E #17(미실행), D4 미해결 #18 |
| SPEC-04 문서 버전 등록 | `04-document-version-create.md` | #19 | Done (2026-11-04, AC-VER-01~19 충족), main 병합 완료 (PR #20). 후속: Windows 실제 브라우저 수동 E2E(OQ-9, Issue 생성 별도 승인) |
| SPEC-05 문서 버전 조회·다운로드·미리보기 | `05-document-version-download.md` | #21 | 구현·클라우드·CI 검증 완료 (2026-11-04, ADR-0006). Windows 검증(AC-VDL-22) 대기, PR 미생성 |
| SPEC-06 활동 이력 (Audit Log) | `06-audit-log.md` | #23 | Draft v0.1, 구현 중 (브랜치 `feat/23/audit-log`, `feat/21` 기반) |
| SPEC-07 문서 협업 상태와 최종 버전 확정 | `07-document-finalize.md` | #24 | 예정 |
| SPEC-08 버전별 Feedback 작성·조회 | `08-version-feedback.md` | #25 | 예정 |
| SPEC-AUTH 확장 초대코드 회원가입·로그인 제한 | `00-auth.md` §확장 | #26 | 예정 |
| (비 SPEC) 공개 배포 준비 | `docs/deployment.md`, ADR-0007 | #27 | 예정 |
| (비 SPEC) UI/UX 전면 개선 | — | #28 | 예정 |

> 2026-11-10 워크플로 변경(Issue #22): v0.4의 SPEC-06~13(Review 중심)은 폐기·재구성되었다. 대응표는 `spec.md` §9.1.
> 브랜치는 미병합 SPEC-05(`feat/21`) 위에 순서대로 쌓는다: `feat/21` → `docs/22` → `feat/23` → `feat/24` → `feat/25` → `feat/26` → `feat/27` → `feat/28`. 병합도 이 순서여야 한다.

## 작성 원칙

- 기능을 구현하기 **직전에** 해당 SPEC을 작성하고 Acceptance Criteria를 확정한다(`spec.md` §18).
- `spec.md` §10(SPEC-06 Review 요청)의 구조를 기본 형식으로 쓴다.
  1. 배경 & 목적
  2. 범위 (In Scope / Out of Scope)
  3. 정상 흐름
  4. 예시
  5. 실패 시나리오 (`spec.md` §12 공통 실패 상황 검토)
  6. Acceptance Criteria
- `spec.md` §16 Open Questions 중 해당 기능에 영향을 주는 항목은 SPEC에서 결정하거나 "미결정"으로 명시한다. 임의로 가정하지 않는다.
- 구현 후 실제 동작과 SPEC이 다르면 SPEC을 갱신한다.
