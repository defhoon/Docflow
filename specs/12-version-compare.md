# SPEC-12 문서 버전 변경 비교 (Version Compare)

- Issue: #55 · 브랜치: `feat/55/version-compare` (SPEC-11 `feat/54/ai-document-summary` 위 stacked)
- 상태: **구현 완료, main 병합 완료 (PR #60, 2026-11-25)**, 운영 미배포
- 결정 기록: [ADR-0010](../docs/adr/0010-version-compare-diff.md) · 의존: SPEC-10 추출·정규화

## 1. 목적

같은 문서의 두 PDF 버전에서 추가·삭제·수정된 텍스트를 **결정론적 Diff**로 찾아 나란히 보여 준다. LLM은 쓰지 않는다. 결과의 변경 ID·좌표는 SPEC-13(하이라이트), SPEC-14(AI 변경 요약)가 그대로 쓴다.

## 2. 범위

- In: 두 버전 선택, 문단·단어 단위 비교, 유형별 개수, 페이지·좌표, 변경 없음 안내, 결과 캐시, 대용량 제한, 권한.
- Out: Office 문서, 스캔 PDF(OCR), 표 구조·서식(글꼴·색) 비교, 이미지 비교.

## 3. API

`GET /api/projects/{p}/documents/{d}/compare?base={n}&target={n}` (구성원)

- 응답: `base`/`target` 버전 정보(번호·파일명·쪽수·쪽 크기·회전), `result`:
  - `identical`, `counts{added, deleted, modified}`, `coarseBlocks`
  - `changes[]`: `id`, `type`(`ADDED`/`DELETED`/`MODIFIED`), `base`/`target` 각각 `text`, `pages`(1부터), `boxes[{page, rect}]`(줄 단위로 합친 상자, SPEC-10 좌표계), `location`(`FULL`/`PARTIAL`/`NONE`/`EMPTY`)
  - `blocks[]`: 문서 순서. `EQUAL`(같은 문단 수와 첫·끝 문단 미리보기 160자) 또는 `CHANGED`(양쪽 구간 `segments[{op: EQUAL/INSERT/DELETE/MODIFY, text, changeId}]`)
- 오류: 같은 버전 400 `COMPARE_SAME_VERSION`, 매개변수 누락 400, 외부인 404 `PROJECT_NOT_FOUND`, 다른 문서·없는 버전 404 `DOCUMENT_VERSION_NOT_FOUND`(같은 문서의 버전만 조회하므로 다른 문서 버전은 비교할 수 없다), PDF 아님 422 `COMPARE_UNSUPPORTED_TYPE`, 텍스트 없음 422 `COMPARE_TEXT_UNAVAILABLE` + `fieldErrors[{field: base|target, message: 추출 상태}]`.
- FINALIZED 문서도 비교할 수 있다(조회만, 데이터 변경 없음). `base`가 `target`보다 큰 번호여도 그대로 비교한다(화면은 "이전/이후"로 표시).

## 4. 알고리즘

1. SPEC-10 정규화: 쪽 번호 줄 제외, 줄 끝 하이픈 연결, 따옴표·대시 통일. 공백·줄바꿈은 토큰 경계로만 쓰이므로 줄바꿈 위치·공백 차이는 변경이 아니다.
2. 문단 단위 Myers Diff(java-diff-utils)로 같은 문단을 맞춘다. 통째로 추가·삭제된 문단은 문단마다 `ADDED`/`DELETED` 하나.
3. 바뀐 문단 묶음은 문단 구분 토큰을 넣고 단어 단위 Myers Diff. 단어 삽입 → `ADDED`, 삭제 → `DELETED`, 교체 → `MODIFIED`. 문단 구분 토큰만 바뀐 경우(문단 나눔 변화)는 변경이 아니다. 한 변경 구간이 문단 구분을 넘으면 문단별로 나눠 짝짓고(예: 수정 + 새 문단 추가), 강조 구간 앞뒤의 문단 구분은 떼어 낸다.
4. 변경 토큰의 원본 단어 색인으로 상자를 모으고, 같은 쪽·같은 줄(세로 50% 이상 겹침)에 이어진 상자를 합친다. 위치가 없는 단어(SPEC-10 `null`)는 상자를 만들지 않고 `location`으로 알린다.
5. 제한: 묶음 토큰 합 20,000개 초과 시 단어 단위로 나누지 않고 묶음 전체를 `MODIFIED` 하나로(`coarseBlocks` 증가). 문서 크기는 SPEC-10 상한(300쪽·20만 단어)을 따른다.
6. 캐시: 버전은 수정되지 않으므로 `(base, target, 추출기 버전)`별 결과를 메모리에 최근 16개 보관한다. 추출 결과는 SPEC-10 테이블로 재사용된다.

## 5. 화면 (`/projects/{p}/documents/{d}/compare?base=&target=`)

- 문서 상세 "버전" 카드의 **버전 비교** 버튼(버전 2개 이상). 기본값: 선택한 버전과 직전 버전.
- 이전·이후 버전 선택(주소에 반영), 서로 바꾸기, 같은 버전 선택 시 안내(요청 안 함).
- 개수 배지: `+ 추가 n`(녹색), `− 삭제 n`(빨강), `~ 수정 n`(노랑).
- 나란히 보기: 데스크톱 2열, 모바일은 이전→이후 순서로 세로. 추가는 녹색·밑줄·`+`, 삭제는 빨강·취소선·`−`, 수정은 노랑·점선 밑줄·`~`, 화면 읽기 프로그램용 "추가:/삭제:/수정 전:/수정 후:" 문구. 같은 문단은 "변경 없는 문단 N개"로 접는다.
- 변경 목록: 유형 배지·텍스트·페이지, 선택 시 해당 구간으로 스크롤·강조, 이전/다음 이동.
- 변경 없음: "두 버전의 본문 텍스트가 같습니다(공백·줄바꿈·쪽 번호 차이는 무시)". 실패: 사유별 안내(PDF 아님, 어느 버전의 어떤 추출 문제인지).

## 6. Acceptance Criteria

| ID | 기준 | 테스트 |
| --- | --- | --- |
| AC-CMP-01 | 줄바꿈 위치·공백·쪽 번호·하이픈 분리만 다르면 변경 없음 | `VersionDifferTest` |
| AC-CMP-02 | 단어 수정은 MODIFIED, 양쪽 텍스트·페이지·상자 | 〃 |
| AC-CMP-03 | 문단·단어 추가·삭제 | 〃 |
| AC-CMP-04 | 위치 없는 단어는 상자 없음, `PARTIAL`/`NONE` | 〃 |
| AC-CMP-05 | 문단 나눔만 바뀐 경우 변경 아님 | 〃 |
| AC-CMP-06 | 같은 줄 상자 합치기 | 〃 |
| AC-CMP-09 | API 결과(개수·좌표·버전 정보) | `VersionCompareIntegrationTest` |
| AC-CMP-10 | 같은 내용 → `identical` | 〃 |
| AC-CMP-11 | 외부인 차단, 다른 문서 버전 비교 불가 | 〃 |
| AC-CMP-12 | 같은 버전·매개변수 누락 400 | 〃 |
| AC-CMP-13 | PDF 아님·텍스트 없음 422(쪽·사유) | 〃 |
| AC-CMP-14 | FINALIZED 문서 비교 가능 | 〃 |
| AC-CMP-UI | 개수·나란히 보기·색 외 표시·목록 이동·변경 없음·실패 안내·버전 변경 | `VersionCompare.test.tsx` |

## 7. 한계

- 표·다단 문서는 PDF 내용 순서로 비교하므로 셀 이동이 삭제+추가로 보일 수 있다.
- 서식만 바뀐 경우(굵게·색)는 찾지 않는다.
- 문장 일부가 크게 바뀌면 여러 단어가 한 `MODIFIED`로 묶인다.

## 8. 검증 기록

| 환경 | 결과 |
| --- | --- |
| Claude Code 클라우드 | 신규 Backend 14개·Frontend 6개 포함 전체 결과는 PR 본문 |
| 실제 브라우저 E2E (Chromium, 로컬 Backend jar + Next dev) | 문서 상세 → 버전 비교 이동, 개수·나란히 보기·목록 이동, 모바일 390px 가로 넘침 0. 발견: 문단을 넘는 변경이 한 구간으로 묶이는 문제 → 문단별 분리로 수정(AC-CMP-03 테스트 추가) |
| 실제 PDF (LibreOffice 한글·영문·표, 의도한 변경 4개) | 수정 3(`12,000,000→15,000,000`, `8명→10명`, `300만원→350만원`)·추가 1 정확히 탐지, 모두 위치 `FULL` (임시 프로브, 저장소 미포함) |
| GitHub Actions CI | PR에서 확인 |
