# SPEC-15 UI/UX 2.0 디자인 시스템과 앱 셸 (Design System & App Shell)

- Issue: #68 · 브랜치: `feat/68/design-system` (main 기준)
- 상태: 구현 완료 (PR 리뷰 대기)
- 근거: Phase 1 디자인 승인(#67, `design/ui-ux-2/`) · 결정 기록: [ADR-0012](../docs/adr/0012-pretendard-self-hosting.md)
- 후속: SPEC-16(문서 워크스페이스) → SPEC-17(비교) → SPEC-18(홈·프로젝트) → SPEC-19(등록·인증)

## 1. 목적

UI/UX 2.0의 모든 화면이 같은 토큰·셸·기본 컴포넌트를 쓰도록 공통 기반을 만든다. 기능·API·권한은 바꾸지 않는다.

## 2. 범위

- In
  - Pretendard 자체 호스팅(가변 글꼴 동적 서브셋 WOFF2, OFL 라이선스 동봉).
  - 토큰 보강: 뷰어 바탕(`canvas-sunken`), 변경 표시 색(추가·삭제·수정), 작업 공간 머리글 높이.
  - 앱 셸 3모드
    - `default`: 240px 사이드바 + 가운데 정렬 본문(현재와 같음).
    - `workspace`: 56px 아이콘 레일 + 화면 전체 높이 본문(패널별 독립 스크롤용). 문서 상세가 사용.
    - `focus`: 데스크톱 사이드바 없음 + 화면 전체 높이 본문. 버전 비교가 사용.
    - 모든 모드에서 1024px 미만은 상단 바 + 서랍 메뉴.
  - 기본 컴포넌트: `IconButton`(접근 가능한 이름 필수, 마우스·키보드 포커스 모두에서 보이는 툴팁), `Tabs`(WAI-ARIA tablist, 화살표·Home·End 이동), `Segmented`(단일 선택 버튼 묶음).
  - 메뉴 이름 "내 작업" → "홈"(Phase 1 시안).
- Out
  - 새 UI 프레임워크·npm 의존성, 다크 모드, 사용자별 레이아웃 저장, 사이드바의 프로젝트 목록(셸이 화면마다 추가 API를 부르게 되므로 제외).

## 3. 동작

1. 루트 레이아웃이 `fonts/pretendard/pretendard.css`를 import한다. `--font-sans` 첫 항목은 `"Pretendard Variable"`이다.
2. `AppShell`은 `mode` 속성(기본 `default`)을 받는다. 인증 보호(`RequireAuth`)·토스트·모바일 서랍 동작은 모드와 무관하게 같다.
3. 레일 모드의 메뉴 항목은 아이콘만 보이며 `aria-label`과 툴팁으로 이름을 제공한다. 현재 위치는 `aria-current="page"`.
4. `workspace`·`focus` 모드의 본문은 `h-dvh`(모바일은 상단 바 제외 높이)이고 페이지 자체는 스크롤하지 않는다. 각 화면이 패널별로 스크롤한다.

## 4. Acceptance Criteria

| ID | 기준 | 테스트 |
| --- | --- | --- |
| AC-DS-01 | 글꼴 파일이 저장소 안에 있고 외부 출처를 참조하지 않으며 라이선스 파일이 함께 있다 | `design-system.test.ts` |
| AC-DS-02 | 기본 모드: 사이드바 메뉴는 실제 화면만(홈·프로젝트, 관리자에게 초대 관리) 표시하고 현재 위치를 표시한다 | `AppShell.test.tsx` |
| AC-DS-03 | 레일 모드: 메뉴 링크에 접근 가능한 이름이 있고, 포커스하면 툴팁이 보인다 | `AppShell.test.tsx` |
| AC-DS-04 | 집중 모드: 데스크톱 사이드바를 그리지 않고 모바일 메뉴는 유지한다 | `AppShell.test.tsx` |
| AC-DS-05 | `IconButton`은 `aria-label`을 갖고 툴팁은 화면 읽기 프로그램에 중복으로 읽히지 않는다 | `ui-components.test.tsx` |
| AC-DS-06 | `Tabs`: 화살표·Home·End로 탭을 옮기고 선택된 탭만 Tab 순서에 있다(roving tabindex) | `ui-components.test.tsx` |
| AC-DS-07 | 기존 화면 테스트·빌드가 그대로 통과한다(회귀 없음) | `npm test`, `npm run build` |

## 5. 검증 기록

환경: Claude Code 클라우드(Node 22, Chromium 헤드리스). GitHub Actions CI는 PR에서 확인한다.

| 항목 | 명령 | 결과 |
| --- | --- | --- |
| 단위 테스트 | `npm test` | 41 files / 308 tests 통과 (AC-DS-01~06 신규 포함) |
| 타입 검사 | `npx tsc --noEmit` | 오류 없음 |
| ESLint | `npx eslint` | 오류 없음 |
| 빌드 | `npm run build` | 성공. 글꼴 조각 92개가 `/_next/static/media/`로 출력, 툴팁 포커스 규칙(`:has(:focus-visible)`) CSS 생성 확인 |
| 실제 앱 | standalone 서버 + local Backend, `/` 1440×900 | 글꼴 조각 8개만 로드(동적 서브셋 동작), 가로 넘침 없음, 콘솔 오류 없음 |

- 발견: 기존 PDF 비교 강조의 수정 배경색이 정의되지 않은 토큰(`warning-500`)을 써서 그려지지 않았다. 토큰을 추가해 바로잡았다.
