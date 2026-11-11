# ADR-0006: 문서 미리보기 변환 엔진 (LibreOffice 프로세스 실행)

- 상태: **Accepted** (설계 확정 2026-11-04. 구현은 Issue #21에서 진행)
- 날짜: 2026-11-04
- 관련 문서: [`specs/05-document-version-download.md`](../../specs/05-document-version-download.md) (SPEC-05) §7.3, §8, §10, Issue [#21](https://github.com/defhoon/Docflow/issues/21), [ADR-0001](./0001-technology-stack.md) Q2(로컬 파일 저장소), [ADR-0003](./0003-development-environment.md)

## 배경

SPEC-05는 PDF·DOCX·XLSX·PPTX 네 형식 모두의 브라우저 미리보기를 요구한다(사용자 결정 2026-11-04). PDF는 브라우저 내장 PDF 렌더러로 보여 줄 수 있지만, Office 형식은 브라우저가 렌더링하지 못한다. Office 파일을 서버에서 PDF로 변환해 같은 PDF 뷰어를 재사용하기로 했고, 변환 엔진과 실행 방식을 정해야 한다.

제약:

- 실행 환경은 Claude Code 클라우드(Linux, 검증), GitHub Actions(`ubuntu-latest`), Windows 로컬(개발·필수 검증), 아직 정의되지 않은 배포 환경이다.
- 클라우드 개발 환경에는 Docker daemon이 없다(2026-11-04 확인, `docker ps` 실패). 이 환경에서는 컨테이너를 실행할 수 없다.
- 업로드 파일은 신뢰할 수 없는 입력이다. [기존] 형식 검증은 안전성을 보증하지 않는다(SPEC-03 R8).
- `CLAUDE.md` §3: 필요성이 확인되지 않은 인프라는 도입하지 않는다. 새 의존성은 이유·대안·영향을 기록한다.

## 검토한 대안

| 대안 | 장점 | 단점 |
| --- | --- | --- |
| **LibreOffice 프로세스 직접 실행 (채택)** | 네 실행 환경 모두에서 같은 엔진 실행 가능(클라우드에서 실제 변환 자동 검증 가능). 새 Java·npm 의존성 없음. 변환 품질이 실용 수준 | 시스템 의존성(LibreOffice 설치) 추가. 앱과 같은 호스트에서 신뢰할 수 없는 문서를 해석. 변환마다 프로세스 시작 비용(약 2초) |
| Gotenberg 컨테이너(LibreOffice 내장 HTTP 서비스) | 프로세스·파일시스템 격리, 외부 네트워크 없는 내부 네트워크와 메모리 제한 구성이 쉬움 | 클라우드 개발 환경에서 실행 불가(Docker 없음) → 이번 SPEC의 Office 미리보기 자동 검증·E2E를 클라우드에서 할 수 없음. 수 GB 이미지, 별도 서비스 운영 |
| JODConverter + 로컬 LibreOffice | 프로세스 풀 재사용으로 시작 비용 절감 | 새 Java 의존성, Spring Boot 4 호환성 미확인. 격리 수준은 직접 실행과 같음 |
| 브라우저 JS 렌더러(docx-preview, SheetJS 등) | 서버 엔진 불필요 | 신뢰할 수 없는 문서를 앱 DOM에 렌더링(XSS 위험), 형식별 라이브러리 3종 이상, PPTX 품질 낮음 |
| 외부 변환 SaaS·Office Online 뷰어 | 설치 불필요 | 비공개 문서를 외부로 전송, 공개 URL 필요. 범위 밖 |

### 실측 (2026-11-04, 클라우드, LibreOffice 24.2.7, 변환마다 프로세스 시작)

| 입력 | 결과 |
| --- | --- |
| DOCX 37KB | 4.4초 (네트워크 namespace·환경 변수 정리·독립 프로필 적용 시 3.0초), PDF 1페이지 |
| XLSX 540KB (5시트) | 6.4초, PDF 2.0MB |
| PPTX 103KB (30슬라이드) | 2.2초 |
| DOCX 8.8MB (텍스트 위주) | 120초 안에 끝나지 않음 |

일반 문서는 수 초 안에 변환되며, 일부 대용량 문서는 제한 시간 안에 변환되지 않는다. 미리보기 실패 안내와 원본 다운로드로 대체한다.

## 결정

1. Office(DOCX·XLSX·PPTX) → PDF 변환은 **LibreOffice(`soffice`)를 Backend가 자식 프로세스로 직접 실행**한다. 엔진은 `DocumentPreviewConverter` 인터페이스 뒤에 두어, 배포 환경이 정해지면 컨테이너 방식으로 교체할 수 있게 한다. PDF 원본은 변환하지 않는다.
2. **실행 규칙**
   - 셸을 거치지 않고 인수 배열(`ProcessBuilder(List<String>)`)로 실행한다. 원본 파일명은 인수에 넣지 않고 서버가 정한 이름(`input.<ext>`)을 쓴다.
   - 변환마다 독립 작업 디렉터리(입력·출력·로그)와 독립 사용자 프로필(`-env:UserInstallation=<작업 디렉터리 URI>`)을 쓴다. 프로필에는 매크로 실행 비활성화·외부 링크 갱신 비활성화 설정을 미리 넣는다.
   - 자식 프로세스 환경 변수는 비우고 실행에 필요한 최소 값(PATH, 작업 디렉터리를 가리키는 HOME·TMP 등, Windows 필수 시스템 변수)만 설정한다. DB 비밀번호 등 Backend 환경 변수를 물려주지 않는다.
   - 시간 제한 60초. 초과하거나 Backend가 종료되면 프로세스와 모든 하위 프로세스(Windows `soffice.exe` → `soffice.bin` 포함)를 강제 종료한다.
   - 동시 변환 1개, 대기열 10개, 결과 PDF 100MiB 상한. 결과는 `%PDF-` 헤더·크기 확인 후 캐시로 원자적으로 이동한다.
   - 작업 디렉터리는 성공·실패와 관계없이 삭제하고, Backend 시작 시 남은 작업 디렉터리를 삭제한다.
3. **최소 권한과 네트워크 제한**
   - Backend와 변환 프로세스는 관리자·root가 아닌 전용 사용자로 실행하는 것을 배포 요건으로 한다. 변환 프로세스는 저장 루트의 작업 디렉터리 외에 쓰기 권한이 필요 없다.
   - 실행 명령 앞에 붙일 OS 격리 래퍼를 설정으로 지정할 수 있게 한다(`docflow.preview.launcher`, 인수 배열). Linux에서는 네트워크 namespace(`unshare -rn` 등)로 외부 네트워크를 차단한다. 클라우드에서 `unshare -rn` 안의 변환 성공과 네트워크 차단을 확인했다(2026-11-04). CI(`ubuntu-latest`)에서 비특권 사용자 namespace 사용 가능 여부는 구현 시 확인하고, 가능하면 CI에서 래퍼를 켜고 검증한다.
   - 래퍼를 쓸 수 없는 환경(Windows, 사용자 namespace가 막힌 Linux)은 LibreOffice 설정(매크로·링크 갱신 비활성화)과 작업 디렉터리 격리만 적용된다. **잔여 위험:** 이 환경에서는 LibreOffice 취약점이나 설정으로 막지 못한 링크·원격 리소스 요청이 Backend와 같은 사용자 권한·네트워크로 실행될 수 있다. 운영 환경에서는 래퍼 적용 또는 컨테이너 방식 교체를 권장한다.
4. **설치와 설정**
   - 엔진 경로: `DOCFLOW_PREVIEW_SOFFICE_PATH`(없으면 PATH의 `soffice`, Windows는 기본 설치 경로 후보 확인). 엔진을 찾지 못하면 Office 미리보기는 `503 DOCUMENT_PREVIEW_UNAVAILABLE`이며 원본 다운로드와 PDF 미리보기는 동작한다.
   - **Windows 로컬:** LibreOffice 설치가 필수다(SPEC-05 AC에 실제 변환 테스트 포함). 한글 글꼴은 Windows 기본 글꼴을 쓴다.
   - **GitHub Actions:** Backend Job에 LibreOffice(Writer·Calc·Impress)와 한글 글꼴(`fonts-noto-cjk`) 설치 단계를 추가한다. 실제 변환 테스트는 CI에서 필수로 실행한다(건너뛰지 않음).
   - **배포:** 실행 이미지·서버에 LibreOffice와 한글 글꼴을 설치하고 전용 비특권 사용자로 실행한다. 배포 환경은 아직 정의되지 않았다.
5. **테스트 의존성 (구현 착수 시 승인 대상):** 변환 결과 PDF의 유효성(페이지 수, 포함 텍스트)을 Windows 포함 모든 환경에서 같은 방식으로 검증하기 위해 `testImplementation` 범위로 Apache PDFBox(Apache-2.0) 도입을 제안한다. 운영 코드에는 넣지 않는다. 대안인 `pdfinfo`·`pdftotext`(poppler)는 클라우드에는 있으나 Windows 기본 환경에 없다. 대안인 헤더·문자열 검사만으로는 압축된 PDF의 페이지·텍스트를 확인할 수 없다.

## 결과

- 장점: 네 형식 미리보기를 하나의 PDF 뷰어로 제공한다. 모든 실행 환경에서 실제 변환을 자동 검증할 수 있다. 새 운영 Java·npm 의존성이 없다.
- 단점: LibreOffice 설치가 개발·CI·배포의 전제 조건이 된다. CI Backend Job 시간이 늘어난다. 변환 품질은 글꼴과 문서 복잡도에 따라 달라지며, 미리보기는 참고용이고 원본 다운로드가 기준이다.
- 위험: §결정 3의 잔여 위험. 단일 인스턴스 가정(작업·실패 상태는 메모리). 대용량 문서 변환 실패.
- 재검토 조건: 배포 환경 결정, 다중 인스턴스 운영, 변환 실패율·시간이 사용성에 문제가 될 때, LibreOffice 보안 공지.

## 구현 결과 (2026-11-04, Issue #21)

- 구현: `LibreOfficePreviewConverter`(인수 배열, 작업별 디렉터리·프로필, 매크로·링크 갱신 비활성화 프로필 설정, 최소 환경 변수, 래퍼 앞붙임, 시간 초과 시 프로세스 트리 종료), `DocumentPreviewService`(single-flight, 동시 1·대기열 10, 결과 검증, 실패 기록, 시작 시 작업 디렉터리 정리), `PreviewCache`(용량 상한 LRU·미사용 정리·사용 중 보호).
- 격리 래퍼 동작: `docflow.preview.launcher`가 비어 있으면 격리 없이 실행하고 WARN 로그를 남긴다. 지정했는데 자체 점검(`--version`)이 실패하면 ERROR 로그를 남기고 Office 미리보기를 끈다(503). 격리 없이 대체 실행하지 않는다.
- CI: `ubuntu-latest`는 AppArmor가 비특권 사용자 namespace를 막는다. 워크플로에서 `kernel.apparmor_restrict_unprivileged_userns=0`으로 바꾼 뒤 `unshare -rn`을 확인하고 `DOCFLOW_PREVIEW_LAUNCHER=unshare,-rn`으로 실제 변환 테스트를 실행한다. 사용할 수 없으면 워크플로 경고를 남긴다.
- 테스트 의존성: Apache PDFBox 3.0.5 `testImplementation`(승인 2026-11-04). 운영 의존성에는 없다.
- 검증 결과와 한계는 SPEC-05 §18.
