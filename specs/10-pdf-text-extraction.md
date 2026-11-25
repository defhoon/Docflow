# SPEC-10 PDF 텍스트·좌표 추출 공통 모듈 (PDF Text Extraction)

- Issue: #53 · 브랜치: `feat/53/pdf-text-extraction` (기준 `main` `3386fcc`)
- 상태: **구현 완료, 리뷰 대기**
- 사용처: SPEC-11 AI 문서 요약, SPEC-12 버전 비교, SPEC-13 PDF 하이라이트, SPEC-14 AI 변경 요약
- 결정 기록: [ADR-0008](../docs/adr/0008-pdf-text-extraction.md)

## 1. 목적

AI 요약과 버전 비교가 같은 추출 결과를 쓰고, 나중에 PDF 위 하이라이트를 위해 추출을 다시 설계하지 않도록 처음부터 **단어 단위 원본 좌표**를 함께 저장한다.

## 2. 범위

- In: PDF 버전의 페이지별 텍스트, 페이지 크기·회전, 단어 좌표, 비교용 정규화(원본 단어 색인 유지), 버전별 저장·재사용, 상한·예외 처리.
- Out: OCR(스캔 PDF), Office 문서 분석, 사용자용 API(이 모듈은 내부 서비스다. 결과는 SPEC-11~14 API로만 노출).

## 3. 동작

1. 업로드 트랜잭션은 바뀌지 않는다. 추출은 사용하는 기능(요약 작업자·비교 요청)이 처음 필요할 때 `DocumentTextService.getOrExtract(versionId)`로 실행한다.
2. 저장된 결과가 있으면 그대로 쓴다. 없으면 버전별 잠금 안에서 다시 확인하고 원본을 임시 파일로 복사해 추출한다. 저장은 `ON CONFLICT`로 하므로 행은 버전당 하나다.
3. 성공·실패 상태를 모두 저장한다(같은 파일을 반복 처리하지 않음). `EXTRACTOR_VERSION`보다 낮은 버전으로 저장된 결과와 `TIMEOUT`(일시적일 수 있음)은 다음 요청에서 다시 추출해 덮어쓴다.
4. 원본 파일을 읽을 수 없으면(누락·크기 불일치) 저장하지 않고 오류를 돌려준다(다음 요청에서 다시 시도).

## 4. 저장 (V11 `document_version_texts`)

| 열 | 내용 |
| --- | --- |
| `version_id` | PK, `document_versions` FK |
| `status` | `EXTRACTED` / `NO_TEXT` / `ENCRYPTED` / `CORRUPTED` / `TOO_LARGE` / `TIMEOUT` / `UNSUPPORTED_TYPE` |
| `extractor_version`, `page_count`, `word_count`, `char_count` | 메타데이터 |
| `content` | `EXTRACTED`일 때만 gzip JSON (`CHECK`로 강제) |

## 5. 추출 결과 모델 (`ExtractedText`)

- `pages[]`: CropBox 너비·높이(pt), `/Rotate`.
- `words[]`: 원문(NFKC, 보이지 않는 문자 제거), 페이지 색인, 상자 `[x1,y1,x2,y2]`.
  - 좌표계: PDF 사용자 공간, CropBox 왼쪽 아래 원점, **페이지 회전 적용 전**. 회전·확대는 표시 단계(SPEC-13)에서 적용한다.
  - 상자는 글자 진행 폭 × 기준선 아래 0.22em~위 0.8em을 글자 행렬로 옮긴 축 정렬 사각형이다. 글자 방향이 0·90·180·270도가 아니면 `null`(위치 미확정, 하이라이트하지 않음).
- `lines[]`: 단어 범위, 문단 시작, 머리말·꼬리말 여부.
  - 문단: 페이지 첫 줄, 기준선 간격 > 글자 높이 × 1.8, 위로 되돌아감(다단), 글자 높이 20% 이상 변화.
  - 머리말·꼬리말: 페이지 위·아래 8% 영역에 있는 쪽 번호 형태 줄(`3`, `- 3 -`, `3 / 10`, `Page 3`, `3쪽`).
- 단어 합치기: 같은 줄에서 공백 없이 붙은 단어(간격 < 0.1 높이), 한글과 숫자·영문 경계는 자동 간격을 고려해 < 0.22 높이.

## 6. 정규화 (`TextNormalizer`)

머리말·꼬리말 제외, 줄 끝 영어 하이픈 연결(`docu-`+`ment` → `document`), 따옴표·대시 변형 통일, 소프트 하이픈 제거. 토큰마다 원본 단어 색인 배열을 가진다. 공백·줄바꿈은 토큰 경계로만 쓰인다.

## 7. 상한과 예외

| 설정 (`docflow.extraction.*`) | 기본값 | 초과 시 |
| --- | --- | --- |
| 파일 크기 | 업로드 상한 20MiB (기존) | 업로드 거부 |
| `max-pages` | 300 | `TOO_LARGE` |
| `max-words` | 200,000 | `TOO_LARGE` |
| `timeout` | 60초 (페이지·글자마다 확인) | `TIMEOUT` |
| `concurrency` | 2 | 대기 |

메모리: PDFBox 버퍼는 임시 파일만 사용한다. 암호가 필요하거나 추출이 금지된 PDF → `ENCRYPTED`, 해석 불가 → `CORRUPTED`, 단어 0개 → `NO_TEXT`, PDF가 아님 → `UNSUPPORTED_TYPE`. 로그에는 버전 ID·상태·쪽수·단어 수·시간만 남기고 문서 내용·파일명은 남기지 않는다.

## 8. Acceptance Criteria

| ID | 기준 | 테스트 |
| --- | --- | --- |
| AC-EXT-01 | 한글·영문(내장 없는 CID 글꼴, 표준 글꼴) 단어·페이지 추출 | `PdfTextExtractorTest` |
| AC-EXT-02 | 단어 좌표가 글자 위치와 일치, CropBox 원점·페이지 회전(0/90/180/270)에 무관 | 〃 |
| AC-EXT-03~05 | 텍스트 없음·암호화·손상 PDF 상태 | 〃 |
| AC-EXT-06 | 페이지·단어·시간 상한 | 〃 |
| AC-EXT-07 | 정규화 후에도 원본 단어 색인 유지(하이픈 연결·쪽 번호 제외·문단) | 〃 |
| AC-EXT-08 | 한글·숫자 자동 간격은 한 단어, 실제 공백은 분리 | 〃 |
| AC-EXT-09 | 업로드 시 추출하지 않음, 첫 요청 시 추출·저장, 이후 재사용 | `DocumentTextServiceIntegrationTest` |
| AC-EXT-10 | 동시 요청에도 행 1개 | 〃 |
| AC-EXT-11 | 실패 상태도 본문 없이 저장, 반복 처리 없음 | 〃 |
| AC-EXT-12 | 낮은 추출기 버전은 다시 추출 | 〃 |
| AC-EXT-13 | `TIMEOUT`은 재사용하지 않고 다시 추출 | 〃 |

## 9. 지원 범위와 한계

- 일반 텍스트 PDF(워드프로세서·LibreOffice 출력)를 우선 지원한다. 스캔 PDF는 `NO_TEXT`.
- 상자 높이는 글꼴 크기로 어림하므로 실제 글리프보다 조금 크거나 작을 수 있다.
- 다단·표는 PDF 내용 순서를 따른다. 읽기 순서가 화면과 다를 수 있다.

## 10. 검증 기록

| 환경 | 결과 |
| --- | --- |
| Claude Code 클라우드 (JDK 21 toolchain override, PostgreSQL 16.15) | `com.docflow.extraction.*` 15개 포함 전체 Backend 446개 통과 (실패·건너뜀 0) |
| 실제 PDF (LibreOffice 24 HTML→PDF, 한글·영문·표) | 제목/본문 문단 분리, `1차`·`11월`·`300만원` 단어 유지, 좌표 확인 (임시 프로브, 저장소에 넣지 않음) |
| GitHub Actions CI | PR에서 확인 |
| 로컬(사용자) | 미실행 |
