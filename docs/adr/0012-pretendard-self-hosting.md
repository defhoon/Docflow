# ADR-0012: Pretendard 글꼴 자체 호스팅 (동적 서브셋 WOFF2)

- 상태: 승인 (2026-11-25 사용자 승인: "Pretendard 자체 호스팅, WOFF2 및 적절한 서브셋 활용, 라이선스 명시 및 폰트 로딩 성능 고려")
- Issue: #68 (SPEC-15)

## 결정

1. Pretendard 1.3.9(SIL OFL 1.1)의 **가변 글꼴 동적 서브셋**(`PretendardVariable.subset.{0..91}.woff2`, 92개 조각, 합계 약 3.1MB)을 `frontend/src/app/fonts/pretendard/`에 복사해 커밋한다. npm 의존성으로 추가하지 않는다.
2. 각 조각은 `unicode-range`로 나뉘어 있어 브라우저는 **화면에 실제로 쓰인 글자가 속한 조각만** 내려받는다(한글 11,172자 전체 지원). 굵기는 가변 축(45–920) 하나로 모든 굵기를 제공한다.
3. `@font-face`는 `font-display: swap`이며, 루트 레이아웃이 CSS를 import한다. Next.js 번들러가 파일을 `/_next/static/media/`에 해시 이름으로 내보내므로 장기 캐시(immutable)가 적용된다. 외부 CDN·Google Fonts를 쓰지 않는다.
4. 대체 글꼴 스택(`Apple SD Gothic Neo`, `Malgun Gothic`, system-ui …)은 그대로 둔다. 글꼴을 받기 전·실패 시 시스템 글꼴로 표시한다.
5. 라이선스 전문은 `frontend/src/app/fonts/pretendard/OFL-LICENSE.txt`에 둔다. 글꼴을 수정하지 않았으므로 Reserved Font Name 조건을 위반하지 않는다(`@font-face`의 `format` 표기만 `woff2`로 정리).

## 고려한 대안

| 대안 | 채택하지 않은 이유 |
| --- | --- |
| `pretendard` npm 패키지 의존성 | 사용자 지침(불필요한 의존성 추가 금지). 필요한 파일은 글꼴뿐이다 |
| 정적 서브셋(KS X 1001 2,350자) 굵기별 4개 + `next/font/local` | 첫 방문 시 굵기마다 약 270KB 전체를 받음(약 1.1MB). 2,350자 밖 음절은 시스템 글꼴로 섞여 보임 |
| 가변 글꼴 전체 1개(약 2MB) | 첫 방문 전송량이 가장 큼 |
| CDN(jsDelivr) | 외부 출처 의존·개인정보(요청 로그)·CSP 관리 부담 |

## 영향

- 저장소 크기 약 3.1MB 증가(바이너리 92개). 빌드 산출물에 같은 파일이 포함된다.
- `next/font`의 자동 preload·size-adjust는 쓰지 않는다(동적 서브셋은 미리 알 수 없는 조각이므로). 첫 표시 때 글꼴 교체(FOUT)가 짧게 보일 수 있다.
