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
| SPEC-03 문서 등록 | `03-document-create.md` | #15 | Draft v0.2 (설계 보완 완료, 구현 미승인) |

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
