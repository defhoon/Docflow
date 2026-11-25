# SPEC-14 AI 버전 변경사항 요약 (AI Version Summary)

- Issue: #57 · 브랜치: `feat/57/ai-version-summary` (SPEC-13 `feat/56/pdf-change-highlight` 위 stacked)
- 상태: **구현 완료, main 병합 완료 (PR #62, 2026-11-25)**, 운영 미배포 · 실제 유료 API 호출: **미실행(별도 승인 필요)**
- 의존: SPEC-11 작업 구조·설정·비용 통제, SPEC-12 Diff 결과(변경 ID)

## 1. 목적

결정론적 Diff(SPEC-12)가 찾은 변경 목록만 입력으로 Claude가 변경 내용을 문장으로 요약한다. 변경 위치·존재 여부는 Diff가 정하고 AI는 정하지 않는다.

## 2. 범위

- In: 추가·삭제·수정 요약, 일정·수치·요구사항 핵심 변경(이전 → 이후), 중요도 참고 설명, 근거 변경 ID 검증, 버전 조합별 저장·중복 방지, 비교 화면 UI.
- Out: 자동 생성(비용 통제를 위해 요청 시에만), 원문 전체 전송, 변경 위치 추론.

## 3. API

| 메서드·경로 | 동작 |
| --- | --- |
| `GET /api/projects/{p}/documents/{d}/compare/summary?base=&target=` | 조합의 상태·결과(SPEC-11 `AnalysisResponse`와 같은 형태) |
| `POST …/compare/summary?base=&target=` | 202. 없으면 생성, 다시 시도 가능한 실패면 대기로, 그 밖에는 현재 상태 |

같은 버전 400 `COMPARE_SAME_VERSION`, 없는 버전 404, 외부인 404, AI 꺼짐 409 `AI_DISABLED_FOR_PROJECT`, PDF 아님 422 `ANALYSIS_UNSUPPORTED_TYPE`. 저장은 `document_analyses`(`analysis_type=CHANGE_SUMMARY`, `version_id=target`, `base_version_id=base`)이며 조합마다 한 행(SPEC-11 고유 인덱스). `base`·`target`을 바꾼 조합은 다른 행이다.

## 4. 처리

1. 작업자가 `VersionCompareService.compareVersions(base, target)`로 Diff를 계산한다(SPEC-10·12 결과 재사용).
2. 변경이 없으면 API를 호출하지 않고 "변경 없음"(`significance.level = NONE`)으로 완료한다.
3. 입력: `<changes>` 안에 `#id TYPE p.N` + `before:`/`after:` 텍스트(변경당 600자, 줄바꿈 제거, 구분 태그 무력화). 바뀌지 않은 본문은 보내지 않는다.
4. 추정 토큰이 요청당 상한(150,000)을 넘으면 `COMPARE_TOO_LARGE`(다시 요청 불가). 예산 확인은 SPEC-11과 같다.
5. 출력(JSON Schema): `overview`, `added[]`/`deleted[]`/`modified[]`(`text`, `changeIds`), `keyChanges[]`(`type`, `label`, `before`, `after`, `changeIds`), `significance{level: LOW|MEDIUM|HIGH, reason}`.
6. 검증(없는 변경 생성 방지):
   - 존재하지 않는 변경 ID는 근거에서 뺀다. `added`는 ADDED·MODIFIED, `deleted`는 DELETED·MODIFIED, `modified`는 MODIFIED 변경만 근거로 인정.
   - 근거가 하나도 남지 않은 항목은 버린다.
   - `keyChanges`의 `before`는 근거 변경의 이전 텍스트에, `after`는 이후 텍스트에 있어야 한다(공백 무시). 아니면 버린다.
   - 버린 수는 `meta.droppedItems`.

## 5. 화면

비교 화면(SPEC-12) 위쪽 **AI 변경 요약** 카드: "AI 생성 · 검토 필요" 배지, 중요도(참고) 배지와 이유, 개요, 주요 일정·수치 변경(이전 취소선 → 이후), 추가/삭제/수정 목록. 각 항목의 `#n` 버튼을 누르면 비교 결과의 해당 변경으로 이동한다(텍스트·PDF 보기 모두). 요청 버튼(외부 전송 안내), 진행·실패·다시 시도. AI가 꺼진 프로젝트에서 결과가 없으면 표시하지 않는다.

## 6. Acceptance Criteria

| ID | 기준 | 테스트 |
| --- | --- | --- |
| AC-CS-01~03 | Diff 변경만 입력(바뀌지 않은 본문 미전송), 근거 ID·유형·값 검증 후 저장 | `ChangeSummaryIntegrationTest` |
| AC-CS-04 | 변경 없음 → 호출 없이 완료 | 〃 |
| AC-CS-05 | 조합별 1회 생성·호출, 조합이 다르면 별도 저장 | 〃 |
| AC-CS-06 | 권한·AI 꺼짐·같은 버전·없는 버전·PDF 아님 | 〃 |
| AC-CS-07 | 텍스트 없음 → 호출 없이 실패, 재요청 불가 | 〃 |
| AC-CS-08 | 근거 없는 항목 제거 | 〃 |
| AC-CS-UI | 요약·핵심 변경·중요도(참고)·변경 이동·요청·꺼짐 숨김 | `ChangeSummaryPanel.test.tsx` |

## 7. 검증 기록

| 환경 | 결과 |
| --- | --- |
| Claude Code 클라우드 | Backend 신규 6개, Frontend 신규 3개. 전체 결과는 PR 본문 |
| 실제 Claude API | 미실행(승인 대기) |
