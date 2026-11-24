# ADR-0009: AI 문서 분석 — Claude API(공식 Java SDK) + PostgreSQL 작업 큐

- 상태: 승인 (2026-11-24 사용자 승인: Anthropic Claude API, 공식 Java SDK, Sonnet 계열 기본, Spring Scheduler + PostgreSQL)
- Issue: #54 (SPEC-11), #57 (SPEC-14)

## 결정

1. `com.anthropic:anthropic-java:3.8.0`(공개 2026-11-10)을 도입한다. 최신 3.13.0(2026-11-23)은 공개 직후라 2주 이상 지난 버전을 골랐다. 이 SDK는 Jackson 2(`com.fasterxml`)와 OkHttp를 함께 가져온다. 애플리케이션 JSON 처리는 기존 Jackson 3를 그대로 쓴다.
2. 기본 모델 `claude-sonnet-5-5`(입력 $2 / 출력 $10 per 1M 토큰, 2026-11-03 기준 모델 표). `DOCFLOW_AI_MODEL`로 바꾼다. 단가도 설정값이며 비용 상한은 이 단가로 계산한 추정치다.
3. 비동기 처리는 Spring `@Scheduled` 작업자와 PostgreSQL `document_analyses`(`FOR UPDATE SKIP LOCKED`)로 한다. Redis·Kafka·별도 서비스를 두지 않는다.
4. 응답 형식은 구조화 출력(JSON Schema)으로 고정하고, 결과를 서버에서 다시 검증한다. 재시도는 SDK가 아니라 작업 큐가 맡는다.
5. 테스트·CI는 `LlmClient` 경계를 가짜로 바꿔 외부 호출 없이 검증한다.

## 고려한 대안

| 대안 | 채택하지 않은 이유 |
| --- | --- |
| FastAPI 등 별도 AI 서비스(`spec.md` §11.1 초기 안) | 새 런타임·배포·통신. 사용자 요청(Spring Boot 안에서 직접 연동)과 충돌 |
| Spring AI | 추상화 계층이 추가되고 Claude 최신 기능(구조화 출력·fallbacks) 지원 시점이 SDK보다 늦을 수 있음 |
| HTTP 직접 호출 | 공식 SDK가 있으므로 타입·오류 분류를 다시 만들 이유가 없음 |
| PDF 원본을 Claude에 그대로 전송 | 비용이 더 크고, 버전 비교(SPEC-12)와 같은 추출 결과를 공유할 수 없음 |
| 업로드 트랜잭션에서 작업 생성 / 커밋 후 이벤트 | 업로드 코드 변경과 실패 전파 위험. 주기 스캔은 업로드 경로를 전혀 바꾸지 않음 |

## 영향

- 외부 전송: 프로젝트 Owner가 고지에 동의하고 켠 프로젝트의 PDF 텍스트만 전송한다. 기본 꺼짐, 서비스 스위치·API 키가 없으면 켤 수 없다.
- 비용: 프로젝트·서비스 하루 호출 상한, 월 추정 비용 상한, 문서·요청 토큰 상한.
- 단일 인스턴스 전제(ADR-0007). 여러 인스턴스로 늘려도 `SKIP LOCKED`로 중복 처리는 없지만, 시작 시 PROCESSING 복구 규칙은 바꿔야 한다.
