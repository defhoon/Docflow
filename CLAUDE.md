# CLAUDE.md — DocFlow 개발 지침

## 1. 프로젝트와 기준 문서

DocFlow는 문서 등록 → 검토 요청 → 피드백 → 수정 → 재검토 → 승인 과정을 관리하는 Document Review Workflow 시스템이다. Google Docs와 같은 실시간 공동 편집 도구가 아니다.

문서의 역할과 우선순위는 다음 순서를 따른다.

| 문서 | 역할 |
| --- | --- |
| `spec.md` | 목표, 범위(In Scope / Post-MVP / Out of Scope), 핵심 Workflow, 공통 정책, MVP Acceptance Criteria. 기술 스택(§11.1), 패키지 구조(§11.3), 개발 규칙(§11.4), 비밀정보(§11.5), DoD(§14), 검증 명령(§15) |
| `specs/*.md` | 기능별 상세 동작, 예외 처리, Acceptance Criteria. 진행 중인 SPEC·Issue·브랜치와 상태는 `specs/README.md` 표 |
| `CLAUDE.md` | 작업 절차, 승인 경계, 보고 방식, Git 규칙 |
| `docs/` | 아키텍처 설명, 결정 기록(`docs/adr/`: 기술 스택·버전, 패키지 구조, 인증 등) |

- 문서 간 충돌, 불명확한 요구사항, 구현에 영향을 주는 미결정 정책은 임의로 해석하지 않고 사용자에게 확인한다. 불확실한 내용은 "미결정"으로 표시한다.
- 실행 명령어는 `spec.md` §15와 `README.md`를 따른다.

## 2. 작업 절차 (SPEC-driven)

| 단계 | 내용 | 산출물 |
| --- | --- | --- |
| 1. Analyze | 관련 SPEC·코드·의존성·테스트·제약·Open Questions 확인 | 현황과 질문 |
| 2. Draft SPEC | 기능 SPEC(`specs/NN-*.md`) 초안: 범위, 정상·실패 흐름, Acceptance Criteria | 초안 |
| 3. Review | 질문에 대한 결정 반영, Acceptance Criteria 확정 | 확정 SPEC |
| 4. Approval | 구현 범위, 접근 방식·대안, 변경 대상 파일, 검증 방법 제시 후 승인 | 승인 |
| 5. Implement | 승인된 범위만 구현, 작은 단위로 커밋 | 커밋 |
| 6. Test | 자동 테스트 → CI → 수동 검증(사용자). 결과를 SPEC 검증 절에 기록 | 검증 기록 |
| 7. PR | 템플릿에 따라 작성, `Closes #N` | PR |

- 기능 SPEC 하나는 Issue 하나, 브랜치 하나, PR 하나로 설계·구현·테스트한다.
- SPEC 범위 밖 작업(보안·의존성 업데이트, 공통 문서 정리 등)은 별도 Issue·브랜치로 분리한다.
- 구현 중 SPEC과 다른 결정이나 새 Edge Case가 생기면 SPEC에 반영한다. 명세대로 구현하기 부적절한 기술적 이유가 있으면 임의로 바꾸지 않고 보고한다.

## 3. 승인 경계

| 행위 | 조건 |
| --- | --- |
| Issue·브랜치 생성 | 사용자 승인 후 |
| 문서 생성·수정 | 사용자 승인 후. 승인 전에는 대화로 초안을 제시한다. 단, 승인된 작업 범위 안의 SPEC 작성과 검증 기록 갱신은 추가 승인 없이 진행한다 |
| 코드·설정 파일 생성·수정 | 계획(범위, 대안, 변경 대상 파일) 승인 후, 승인된 범위만 |
| 새 의존성, 버전 변경, 아키텍처 변경 | 도입 이유·대안·영향 설명 → 승인 → ADR 기록 |
| Redis, Kafka, Microservices 등 인프라 | 실제 필요성이 확인되지 않으면 도입하지 않는다 |
| PR 생성 | 사용자 요청 시 |
| `main` 병합·직접 수정 | 하지 않는다. 병합은 사용자가 직접 수행한다 |

- 승인된 범위 안의 커밋·Push는 진행한다. 한 번의 승인은 그 범위에만 적용한다.

## 4. 개발 원칙

- `spec.md`의 In Scope / Post-MVP / Out of Scope 구분을 지키고, SPEC 범위 밖 기능 추가·리팩터링을 하지 않는다.
- `spec.md` §11.4를 따른다. Controller는 HTTP 요청·응답만 담당하고 핵심 비즈니스 규칙은 Service에서 처리한다. Controller는 Repository를 직접 호출하지 않으며 Entity를 API 응답으로 반환하지 않는다. 일관성이 필요한 데이터 변경은 Transaction으로 처리한다.
- 권한 검증과 허용 가능한 상태 전이 검증은 Backend에서 수행한다. Frontend 검증은 사용성 보조다.
- 비밀번호, API Key, 실제 `.env` 등 비밀정보를 Git에 포함하지 않는다(`spec.md` §11.5).
- 보안 기능을 끄거나 테스트를 건너뛰는 방식으로 테스트를 통과시키지 않는다.

## 5. 탐색 범위 (효율성 원칙)

- 작업과 직접 관련된 파일부터 확인한다: 해당 SPEC → 변경 대상 코드와 테스트 → 직접 의존하는 코드.
- `spec.md`와 ADR은 작업에 필요한 절을 중심으로 읽는다. 이미 확인했고 이후 바뀌지 않은 문서는 가능하면 다시 읽지 않는다.
- 근거가 부족하거나 영향 범위가 불분명하면 범위를 넓혀 확인한다.

## 6. 검증과 보고

- 성공 여부는 실제 실행 결과로만 판단한다. 실행하지 않은 테스트를 성공했다고 보고하지 않는다.
- Acceptance Criteria를 기준으로 정상·실패 시나리오를 테스트하고, 완료 여부는 SPEC의 Acceptance Criteria와 `spec.md` §14 DoD로 판단한다.
- 검증 환경을 구분한다: Claude Code 클라우드 / GitHub Actions CI / 로컬(사용자). 환경 제약으로 Docker, PostgreSQL, JDK, 로컬 실행 등을 확인하지 못했다면 미검증으로 보고한다.
- 보고는 한국어로 간결하게 작성한다. SPEC 내용을 다시 옮겨 적지 않는다.
  1. 변경 파일
  2. 실행한 명령과 실제 결과
  3. 실패·미실행 항목과 사유, 미해결 사항, 남은 Open Questions (생략하지 않는다)
  4. 상태 한 줄
- 중요한 기술 결정에는 선택 이유와 고려한 대안을 함께 적는다.
- 프로젝트에 존재하지 않는 파일, 명령어, 테스트 결과를 존재하는 것처럼 설명하지 않는다.
- 코드의 변수명, 함수명, 클래스명, API 경로 등은 일관된 영어 명명 규칙을 사용한다.

## 7. Git 컨벤션

모든 작업은 실제 GitHub Issue 번호를 기준으로 한다. Issue 없이 브랜치나 커밋을 만들지 않는다.

### 7.1 브랜치

형식: `type/issueNumber/kebab-case-description`

- `type`: `feat`(기능), `fix`(버그 수정), `docs`(문서만 변경), `hotfix`(main 긴급 수정)
- `issueNumber`: 실제 GitHub Issue 번호 (숫자만, `#` 금지)
- `description`: 소문자 kebab-case
- 예: `feat/4/auth`, `fix/12/login-error-message`, `docs/11/claude-md-cleanup`
- 최신 `origin/main`에서 생성하고, PR로 `main`에 병합한다.
- CI는 `docs/**` 브랜치 push에서는 실행되지 않으며 PR에서 실행된다.

### 7.2 커밋 메시지

형식: `type/#issueNumber: subject`

| type | 용도 |
| --- | --- |
| `feat` | 기능 추가 |
| `fix` | 버그 수정 |
| `docs` | 문서 (`spec.md`, `specs/`, `docs/`, `README.md`, `CLAUDE.md`) |
| `style` | 동작 변경 없는 코드 포맷 |
| `refactor` | 동작 변경 없는 구조 개선 |
| `test` | 테스트 추가·수정 |
| `chore` | 빌드·의존성·설정·CI |
| `design` | UI 디자인·스타일 변경 |
| `hotfix` | main 긴급 수정 |

- `type`은 영어 소문자, `#issueNumber`는 실제 Issue 번호
- 제목(`subject`) 끝에 마침표를 쓰지 않으며 50자 이내를 권장
- 본문이 필요하면 제목 다음에 한 줄을 비우고 작성
- Claude Code가 작성하는 커밋은 본문 끝에 `Co-Authored-By`, `Claude-Session` trailer를 둘 수 있다. 제목은 반드시 위 형식을 따른다.
- 예: `docs/#4: add SPEC-AUTH detailed spec`, `feat/#4: add session login API`

### 7.3 Pull Request

- 제목은 커밋 메시지와 같은 형식(`type/#issueNumber: subject`)을 사용한다.
- 본문은 `.github/pull_request_template.md`를 따르며 `Closes #issueNumber`를 포함한다.
- 병합 방식은 Merge commit을 사용한다.
- `main` 병합은 사용자가 직접 수행한다. Claude Code는 사용자의 명시적 요청 없이 `main`에 병합하지 않는다.
