# ADR-0002: 패키지 및 디렉터리 구조

- 상태: **Accepted**
- 날짜: 2026-10-14
- 관련 문서: [`CLAUDE.md`](../../CLAUDE.md) §4, [`spec.md`](../../spec.md) §9, §11

## 배경

DocFlow는 기능을 SPEC-01~13 단위로 개발하고, AI Agent(Claude Code)로 기능별 작업을 지시·검토한다. 이 개발 방식에서는 하나의 기능 변경이 코드베이스의 어느 범위에 모이는지가 생산성과 리뷰 품질에 큰 영향을 준다.

## 검토한 대안

| 항목 | Domain 중심 (`review/controller`, `review/service` …) | 계층 중심 (`controller/ReviewController` …) |
| --- | --- | --- |
| 기능 SPEC 대응 | SPEC 하나 ≈ 패키지 하나, 변경 범위가 명확 | 기능 하나에 계층 패키지 4~5개가 관여 |
| 응집도 | 관련 코드가 한곳에 모임 | 같은 계층끼리만 모임 |
| 초기 단순함 | 도메인 경계를 정해야 함 | 직관적이고 예제가 많음 |
| 의존 관리 | 패키지 경계로 도메인 간 의존이 드러남 | 순환 의존이 잘 보이지 않음 |
| 규모 확장 | 패키지 수는 늘지만 각각은 작음 | `service/`에 파일이 수십 개 누적 |
| AI Agent 활용 | "SPEC-08 → `feedback/`"처럼 범위 지시가 쉬움 | 변경 파일이 흩어져 리뷰가 어려움 |

Hexagonal / Clean Architecture(Port-Adapter)도 검토했으나 제외했다. MVP 규모에 비해 보일러플레이트가 많고, 교체할 외부 어댑터도 아직 없다.

## 결정

### Backend — Domain 중심 + 도메인 내부 계층 분리

```text
com.docflow
├── DocflowApplication.java
├── global/           # 횡단 관심사 (config, error, common) — 필요해질 때 생성
└── <domain>/         # user, project, document, review, feedback, audit — 기능 SPEC 구현 시 생성
    ├── controller/   # HTTP 요청·응답만 담당
    ├── service/      # 비즈니스 규칙, 권한 검증, 상태 전이 검증, @Transactional
    ├── repository/   # Spring Data JPA
    ├── domain/       # Entity, Status enum (허용 전이 규칙)
    └── dto/          # Request/Response (Java record), Entity 직접 반환 금지
```

**도메인 간 참조 규칙 (Q11)**

- 다른 도메인의 **Repository를 직접 참조하는 것은 기본적으로 지양**하고, 그 도메인의 Service(또는 조회 메서드)를 통해 접근한다.
- 단, 모든 접근을 Service 경유로 **엄격히 강제하지는 않는다.** 단순 조회만 하려고 위임 메서드를 늘리는 과도한 추상화는 피한다. 예외를 둘 때는 이유를 코드 리뷰에서 설명한다.
- 도메인 간 **순환 의존은 만들지 않는다.** (예: `review` → `project` 참조는 허용, `project` → `review` 역참조는 지양)
- Controller는 같은 도메인의 Service에만 의존하며 Repository를 호출하지 않는다.

### Frontend — Next.js App Router + features 중심

```text
frontend/src
├── app/                    # 라우팅·레이아웃만 (page.tsx, layout.tsx)
├── features/<feature>/     # 기능별 api.ts, types.ts, components/ (+ 테스트 파일 colocate)
├── components/ui/          # 도메인 무관 공통 UI — 2개 이상 기능에서 필요해질 때 생성
└── lib/                    # 공통 유틸(fetch 래퍼 등) — 필요해질 때 생성
```

### 이번 단계에서 실제로 생성한 범위

- 아직 구현하지 않을 기능의 **빈 패키지·빈 클래스·빈 디렉터리는 만들지 않는다.**
- Backend: `com.docflow.DocflowApplication`과 테스트만 있다. (`global/`, 도메인 패키지는 없음)
- Frontend: `app/`과 `features/backend-health/`(개발용 연결 확인 화면)만 있다.

## 이유

- 기능 SPEC 단위 개발과 AI Agent 작업 지시에서 변경 범위를 패키지 단위로 모을 수 있다.
- 도메인 내부는 계층으로 나누므로 `spec.md` §11.4의 Controller/Service/Entity 규칙을 그대로 적용할 수 있다.

## 영향

- 첫 도메인 SPEC을 구현할 때 `global/error`(공통 에러 응답)와 `global/common`(BaseTimeEntity 등)을 함께 설계해야 한다.
- 도메인 간 의존 규칙은 지금은 코드 리뷰로 관리한다. 필요해지면 ArchUnit 등 정적 검사 도입을 제안한다(현재는 도입하지 않음).

## 검증 방법

- 기능 구현 PR 리뷰에서 Controller → Repository 직접 호출, Entity 직접 반환, 도메인 간 순환 의존이 없는지 확인한다.
