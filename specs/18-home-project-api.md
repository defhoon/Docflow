# SPEC-18 홈 대시보드·프로젝트 상세와 조회 API (Home & Project)

- Issue: #71 · 브랜치: `feat/71/home-project-api` (SPEC-17 `feat/70/compare-workspace` 위 stacked)
- 상태: 구현 완료 (PR 리뷰 대기)
- 근거: Phase 1 디자인 승인(#67) 이미지 2·3, 사용자 승인(2026-11-25 "홈 화면의 읽기 전용 통합 조회 API 추가 … 문서 목록의 updatedAt, 최근 업로더, 파일 형식 필드 추가")
- 의존: SPEC-01·02·03·06·07, SPEC-15 셸

## 1. 목적

홈에서 참여 중인 프로젝트의 진행 상황(지표·최근 문서·최근 활동)을 한눈에 보고, 프로젝트 상세에서 문서를 찾고 상태를 파악한다. **DB 스키마 변경·새 인프라 없이** 기존 데이터로 정확히 집계한다.

## 2. 범위

- In
  - `GET /api/home`(읽기 전용): 참여 프로젝트 수, 협업 중·최종 확정 문서 수, 최근 업데이트 문서 최대 5개, 최근 활동 5개.
  - `GET /api/projects/{id}/documents` 응답에 `updatedAt`·`latestUploader`·`fileType` 추가.
  - 홈 대시보드, 전체 활동 화면(`/activity`, 기존 커서 API로 더 보기).
  - 프로젝트 상세 탭(문서·구성원·활동 이력·설정, URL `?tab=`), 문서 표(제목 검색·상태 필터·정렬·형식 아이콘·최종본 열·최근 업로더).
  - 활동 항목에 문서명·버전 표시, 누르면 해당 문서의 그 버전(`?v=N`)으로 이동.
- Out
  - 검토 요청·알림·내게 온 요청 등 데이터가 없는 지표(시안에 있어도 만들지 않음), 프로젝트별 문서 수(정확한 값을 주려면 별도 API 필요), 서버 페이지네이션·검색 API.

## 3. API

### 3.1 `GET /api/home`

```json
{
  "projectCount": 2,
  "activeDocumentCount": 3,
  "finalizedDocumentCount": 1,
  "recentDocuments": [
    { "id": 4, "title": "…", "status": "ACTIVE", "project": { "id": 1, "name": "…" },
      "latestVersionNumber": 3, "finalVersionNumber": null, "updatedAt": "…",
      "latestUploader": { "id": 1, "displayName": "…" }, "fileType": "PDF" }
  ],
  "recentActivities": [ /* ActivityResponse (SPEC-06), 최신순 5개 */ ]
}
```

- 접근 범위: 사용자가 Owner이거나 MEMBER인 프로젝트(프로젝트 목록·활동 API와 같은 조건). 그 밖의 프로젝트 데이터는 수·목록 어디에도 포함하지 않는다.
- `recentDocuments`: `documents.updated_at DESC, id DESC` 최대 5개. `updated_at`은 새 버전·설명 변경·최종 확정 때 갱신된다(피드백은 갱신하지 않음).
- `recentActivities`: `audit_logs.id DESC` 5개. 더 보기는 `GET /api/activities?limit=&beforeId=`(기존).
- 쿼리: 프로젝트 수 1 + 상태별 문서 수 집계 1 + 최근 문서 1 + 최근 활동 1 = **4회**(데이터 양과 무관).
- 설명·이메일·저장 키·Content Type 원문은 포함하지 않는다. 로그인하지 않으면 401.

### 3.2 문서 목록 필드 추가

| 필드 | 의미 |
| --- | --- |
| `updatedAt` | 문서 변경 시각(위와 같음) |
| `latestUploader` | 최신 버전 업로더 `{id, displayName}` |
| `fileType` | 최신 버전 형식 `PDF`·`DOCX`·`XLSX`·`PPTX`(서버가 저장한 Content Type에서 변환) |

- 최신 버전은 `(document_id, version_number)` 유일 인덱스로 찾는 조인 한 번으로 가져온다(목록 쿼리 1회 유지).

## 4. 화면

- 홈: 인사·새 프로젝트 → 지표 띠(참여 프로젝트는 프로젝트 목록 링크) → 최근 업데이트 문서(형식·제목·프로젝트·상태·버전·최종본·업로더·상대 시각) + 내 프로젝트(최대 6) | 최근 활동(5, 전체 활동 링크). 각 영역에 로딩·빈·오류 상태.
- 전체 활동: 30개씩, "더 보기"는 마지막 항목 ID를 `beforeId`로 보낸다. 마지막 페이지면 숨긴다.
- 프로젝트 상세: 머리글(이름·설명·역할·오너·생성일·문서 등록) + 탭. 문서 표는 데스크톱 표/모바일 카드. 상태 배지는 협업 중 원형 점, 최종 확정 사각 점(색 외 구분).

## 5. Acceptance Criteria

| ID | 기준 | 테스트 |
| --- | --- | --- |
| AC-HOME-01 | 접근 가능한 프로젝트만 세고, 다른 사용자의 프로젝트·문서는 포함하지 않는다 | `HomeApiIntegrationTest` |
| AC-HOME-02 | 상태별 문서 수를 정확히 집계한다(빈 사용자는 모두 0) | 〃 |
| AC-HOME-03 | 최근 문서는 변경 시각 최신순 최대 5개이며 최신 업로더·형식·프로젝트를 포함한다 | 〃 |
| AC-HOME-04 | 최근 활동은 최신순 5개 | 〃 |
| AC-HOME-05 | 쿼리 수가 데이터 양과 무관하게 4회 이하, 비로그인 401 | 〃 |
| AC-HOME-06 | 문서 목록에 `updatedAt`·`latestUploader`·`fileType`을 추가하고 쿼리 수는 그대로다 | `DocumentApiIntegrationTest` |
| AC-HOME-UI-01 | 홈은 서버가 센 지표만 표시한다 | `HomeDashboard.test.tsx` |
| AC-HOME-UI-02 | 최근 문서·활동에서 문서(·버전)로 이동하고 전체 활동 경로가 있다 | `HomeDashboard.test.tsx`, `ActivityPage.test.tsx` |
| AC-HOME-UI-03 | 프로젝트 탭은 URL과 동기화되고, 문서 표는 검색·필터·정렬된다 | `ProjectDetail.test.tsx`, `DocumentList.test.tsx` |

## 6. 검증 기록

환경: Claude Code 클라우드(JDK 21 toolchain, PostgreSQL 16, Node 22, Chromium 헤드리스). CI는 PR에서 확인한다.

| 항목 | 명령 | 결과 |
| --- | --- | --- |
| Backend 전체 | `./gradlew test --rerun` | 487 tests, 실패 0 (AC-HOME-01~06 포함) |
| Frontend | `npm test`, `tsc`, `eslint`, `npm run build` | 41 files / 322 tests 통과, 나머지 통과 |
| 실제 앱 | local Backend(시드: 프로젝트 2·문서 4·확정 1) + standalone Frontend | 홈 지표 2/3/1, 최근 문서 4, 활동 5, 프로젝트 문서 표·탭, `/activity`. 1440·390px 가로 넘침 없음, 콘솔 오류 없음 |
