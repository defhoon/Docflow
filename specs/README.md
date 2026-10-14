# specs/ — 기능별 상세 SPEC

이 디렉터리에는 [`spec.md`](../spec.md) §9의 기능 SPEC(SPEC-01 ~ SPEC-14)을 기능별 파일로 작성한다.
**실제 기능별 상세 SPEC은 다음 주차(7주차)부터 작성한다.** 현재는 안내 문서만 있다.

## 파일 명명 규칙

`spec.md` §11의 예시를 따른다.

```text
specs/
├── 01-project-create.md
├── 02-project-member.md
├── ...
└── 14-ai-version-summary.md   # Post-MVP
```

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
