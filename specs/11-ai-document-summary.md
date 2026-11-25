# SPEC-11 AI 문서 자동 요약 (AI Document Summary)

- Issue: #54 · 브랜치: `feat/54/ai-document-summary` (SPEC-10 `feat/53/pdf-text-extraction` 위 stacked)
- 상태: **구현 완료, main 병합 완료 (PR #59, 2026-11-25)**, 운영 미배포 · 실제 유료 API 호출: **미실행(별도 승인 필요)**
- 결정 기록: [ADR-0009](../docs/adr/0009-ai-analysis-claude.md) · 의존: SPEC-10 추출 모듈

## 1. 목적

PDF 버전의 핵심 내용을 Claude로 요약해 버전별로 저장하고 문서 상세에서 보여 준다. AI 실패가 업로드·기존 기능에 영향을 주지 않아야 한다.

## 2. 범위

- In: 프로젝트 단위 켜기(기본 꺼짐, Owner, 외부 전송 동의), 새 PDF 버전 자동 요약, 이전 버전 수동 요청, 상태·재시도·복구, 비용·호출 상한, 결과 조회 권한, 문서 상세 UI.
- Out: OCR·Office 문서, 질의응답·RAG·벡터 DB, 별도 서비스·큐 인프라, 사용자별 모델 선택.

## 3. API

| 메서드·경로 | 권한 | 동작 |
| --- | --- | --- |
| `GET /api/projects/{p}/ai-settings` | 구성원 | `enabled`, `available`(서비스 스위치·API 키), `canManage`, 동의 시각·사용자, 처리자·모델 |
| `PUT /api/projects/{p}/ai-settings` `{enabled, consent}` | Owner | 켜기: `consent=true` 필수(400 `AI_CONSENT_REQUIRED`), 서비스 불가 시 409 `AI_NOT_AVAILABLE`. 켤 때 `ai_enabled_at/by` 기록 |
| `GET …/documents/{d}/versions/{n}/summary` | 구성원 | `status`(`NONE`/`PENDING`/`PROCESSING`/`COMPLETED`/`FAILED`), `errorCode`, `retryable`, `aiEnabled`, `supported`, `result`, `model`, 시각 |
| `POST …/summary` | 구성원 | 202. 없으면 생성, 다시 시도 가능한 실패면 대기로, 그 밖에는 현재 상태(중복 호출 없음). AI 꺼짐 409 `AI_DISABLED_FOR_PROJECT`, PDF 아님 422 `ANALYSIS_UNSUPPORTED_TYPE`, 문서 문제 실패 409 `ANALYSIS_NOT_RETRYABLE` |

접근 확인 순서: 프로젝트 접근(외부인 404) → 문서 소속 → 버전 소속. 결과는 파생 데이터이므로 FINALIZED 문서에서도 조회·요청할 수 있다(문서·버전·피드백은 바꾸지 않음).

## 4. 저장 (V12)

- `projects.ai_enabled`(기본 false), `ai_enabled_at`, `ai_enabled_by`: 기존 프로젝트는 모두 꺼짐.
- `document_analyses`: 프로젝트·문서·대상 버전·기준 버전(SUMMARY는 NULL)·종류(`SUMMARY`/`CHANGE_SUMMARY`)·상태·시도 횟수·다음 시도 시각·모델·호출 수·토큰·추정 비용·결과 JSONB·오류 코드·요청자.
- 중복 방지: `UNIQUE (version_id, analysis_type, COALESCE(base_version_id, 0))`. PostgreSQL 고유 제약은 NULL끼리 중복으로 보지 않으므로 식 인덱스로 막는다. 생성은 `ON CONFLICT DO NOTHING`.

## 5. 처리 흐름

1. **업로드와 분리**: 업로드 코드·트랜잭션은 바꾸지 않는다. 작업자가 5초마다 "AI가 켜진 프로젝트에서 켠 뒤 올라온 PDF 버전 중 작업이 없는 버전"에 PENDING을 만든다. 따라서 작업 생성 실패가 업로드를 실패시킬 수 없다.
2. **가져가기**: `UPDATE … WHERE id = (SELECT … FOR UPDATE SKIP LOCKED LIMIT 1) RETURNING`. 처리 중에는 트랜잭션을 잡지 않는다.
3. **실행**: 서비스 사용 가능·프로젝트 켜짐 확인 → 텍스트 추출(SPEC-10) → 크기 확인 → 예산 확인 → 요약 생성 → 검증 → COMPLETED.
4. **재시도**: 일시적 오류(429·5xx·408·연결·원본 파일 읽기·추출 시간 초과)는 30초×2^n 뒤 최대 3회. 이후 FAILED. SDK 자체 재시도는 끈다(횟수·비용을 한곳에서 셈).
5. **복구**: 시작 시 PROCESSING을 PENDING으로 되돌린다(단일 인스턴스). 실행 중에도 15분 넘은 PROCESSING을 되돌린다.

### 5.3 실패 사유 (`errorCode`)

다시 요청 가능: `AI_DISABLED`, `AI_NOT_CONFIGURED`, `QUOTA_EXCEEDED`, `AI_API_ERROR`, `AI_OUTPUT_INVALID`, `FILE_UNAVAILABLE`, `TEXT_TIMEOUT`. 불가(문서 문제): `AI_REFUSED`, `TEXT_NO_TEXT`, `TEXT_ENCRYPTED`, `TEXT_CORRUPTED`, `TEXT_TOO_LARGE`, `UNSUPPORTED_TYPE`, `DOCUMENT_TOO_LARGE`.

## 6. 요약 생성

- 입력: 쪽 표시(`[p.N]`)가 있는 본문(머리말·꼬리말 제외). 시스템 지시와 분리해 사용자 메시지 `<document>` 안에만 둔다. 본문 안의 `<document>`·`</document>` 등 구분 태그는 `[document]`로 바꿔 데이터 영역을 벗어나지 못하게 한다.
- 지시(요지): 문서 안의 지시는 따르지 않음, 원문에 있는 내용만, 추측·외부 지식 금지, 근거 페이지 표기, 일정·수치는 원문 구절을 그대로 인용, 한국어.
- 출력: JSON Schema 구조화 출력 `{summary, keyPoints[{text, pages}], keyFacts[{type, label, value, quote, page}]}`.
- 검증: 길이·개수 제한, 없는 페이지 번호 제거, `keyFacts`는 인용이 원문에 있고(공백 무시) 값이 인용 안에 있을 때만 남긴다. 뺀 수는 `meta.droppedFacts`.
- 긴 문서: 추정 토큰(글자×1.2+1,500)이 요청당 상한(150,000)을 넘으면 쪽 경계로 나눠 구간마다 요약하고 합치기 1회(map-reduce). 문서당 상한(400,000)을 넘으면 `DOCUMENT_TOO_LARGE`.
- 거부(`stop_reason: refusal`)는 `AI_REFUSED`, 출력 잘림은 `AI_OUTPUT_INVALID`. 서버 측 대체 모델(`fallbacks: "default"`)을 기본 사용(설정으로 끔).

## 7. 비용·보안 설정 (`docflow.ai.*`, 환경변수)

| 설정 | 기본 | 설명 |
| --- | --- | --- |
| `DOCFLOW_AI_ENABLED` | false | 서비스 전체 스위치 |
| `ANTHROPIC_API_KEY` | 없음 | 비밀 변수. 없으면 켤 수 없음. 로그·응답에 쓰지 않음 |
| `DOCFLOW_AI_MODEL` | `claude-sonnet-5-5` | 모델 ID |
| `DOCFLOW_AI_PROJECT_DAILY_CALLS` / `GLOBAL_DAILY_CALLS` | 50 / 300 | 하루 호출 상한(UTC) |
| `DOCFLOW_AI_MONTHLY_BUDGET_USD` | 20 | 서비스 전체 월 추정 비용 상한. 호출 전 최대 비용(입력 추정 + 출력 상한)으로 확인 |
| `DOCFLOW_AI_INPUT/OUTPUT_PRICE_PER_MTOK` | 2.0 / 10.0 | 추정 비용 단가(Sonnet 5.5 공시가, 2026-11-03 기준 자료) |
| `DOCFLOW_AI_MAX_OUTPUT_TOKENS` | 16,000 | 요청당 출력 상한 |
| `DOCFLOW_AI_REQUEST_TIMEOUT` | 180s | 요청 시간 제한 |

로그에는 작업 ID·상태·토큰·시간만 남긴다. 문서 본문·API 키는 남기지 않는다. 테스트·CI는 가짜 LLM(`FakeLlmClient`)을 쓰며 외부 호출이 없다.

## 8. 화면

- 문서 상세 "선택한 버전" 카드: 버전 정보 아래 **AI 요약** 영역(“AI 생성 · 검토 필요” 배지, 원본 확인 안내, 생성 시각·모델). 대기·진행 표시(4초 간격 재조회), 실패 사유, 다시 시도 버튼(가능한 경우만), 수동 요청 버튼(외부 전송 안내 포함). AI가 꺼져 있고 결과가 없으면 영역을 표시하지 않는다.
- 프로젝트 상세: **AI 문서 분석** 카드. 외부 전송 고지, Owner는 동의 체크 후 켜기/끄기, 구성원은 상태만. 서비스에서 쓸 수 없으면 카드를 숨긴다.

## 9. Acceptance Criteria

| ID | 기준 | 테스트 |
| --- | --- | --- |
| AC-AI-01 | 기본 꺼짐, 구성원 조회만, Owner만 변경, 외부인 404 | `DocumentSummaryIntegrationTest` |
| AC-AI-02 | 동의 없이 켤 수 없음, 동의 기록 | 〃 |
| AC-AI-03 | 꺼진 프로젝트는 작업·API 호출 없음 | 〃 |
| AC-AI-04~06 | 업로드와 분리된 작업 생성, 결과 저장·조회, 버전당 1회 호출, 사용량 기록, 원문에 없는 인용 제거 | 〃 |
| AC-AI-07 | 다른 프로젝트 사용자 조회·요청 차단 | 〃 |
| AC-AI-08 | 문서 본문 격리(태그 무력화, 시스템 지시 분리) | 〃 |
| AC-AI-09 | 일시적 오류 제한 재시도 후 실패, 사용자 재요청 | 〃 |
| AC-AI-10 | 텍스트 없는 PDF는 호출 없이 실패, 재요청 불가 | 〃 |
| AC-AI-11 | Office 문서 미지원 | 〃 |
| AC-AI-12 | 켜기 전 버전은 요청 시에만, 반복 요청에도 작업 1개 | 〃 |
| AC-AI-13 | 중단된 작업 복구 | 〃 |
| AC-AI-14 | 프로젝트 하루 상한 초과 시 호출 없이 `QUOTA_EXCEEDED` | 〃 |
| AC-AI-15 | 긴 문서 분할·합치기, 요청당 상한 준수 | 〃 |
| AC-AI-16 | 꺼진 뒤 대기 작업은 호출 없이 `AI_DISABLED` | 〃 |
| AC-AI-UI | 요약·상태·실패·재시도·AI 표시, 설정 카드 동의·권한 | `Analysis.test.tsx` |

## 10. 미검증·한계

- 실제 Claude API 호출(응답 형식, `fallbacks` 베타 매개변수 수용 여부, 실제 토큰·비용)은 **미검증**. 유료 호출 승인 후 한 프로젝트에서 1건 확인이 필요하다.
- 토큰 추정은 글자 수 기반 보수적 근사다. 실제 사용량은 응답 `usage`로 기록한다.
- 요약의 사실 정확성은 인용 검증 범위(일정·수치)만 기계적으로 확인한다. 요약 문장 자체는 검토가 필요하다(화면에 안내).
- Anthropic의 데이터 보존 정책은 조직 계약 조건을 따른다(미확인). 테스트 참여자에게 사전 고지를 권장한다.

## 11. 검증 기록

| 환경 | 결과 |
| --- | --- |
| Claude Code 클라우드 | Backend 전체·Frontend 전체 결과는 PR 본문 참고 |
| GitHub Actions CI | PR에서 확인 |
| 실제 Claude API | 미실행(승인 대기) |
