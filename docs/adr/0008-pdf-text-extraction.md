# ADR-0008: PDF 텍스트·좌표 추출 — Apache PDFBox 운영 도입

- 상태: 승인 (2026-11-24 사용자 승인: "Apache PDFBox를 운영 의존성으로 도입하는 것을 승인")
- Issue: #53 (SPEC-10)
- 관련: ADR-0006 결정 5 개정

## 결정

1. `org.apache.pdfbox:pdfbox:3.0.5`(Apache-2.0)를 `testImplementation`에서 `implementation`으로 옮긴다. 버전은 기존 테스트 의존성과 같다(새 버전 도입 아님).
2. 추출은 Backend(Spring Boot) 안에서 동기 서비스로 실행하고, 결과는 PostgreSQL `document_version_texts`에 버전당 한 행(gzip JSON)으로 저장한다. 별도 프로세스·큐·캐시 서버를 두지 않는다.
3. 단어 좌표는 PDF 사용자 공간(CropBox 기준, 회전 전)으로 저장하고, 회전·확대는 표시 단계에서 적용한다.

## 고려한 대안

| 대안 | 채택하지 않은 이유 |
| --- | --- |
| poppler `pdftotext -bbox` | Windows 기본 환경·Docker 이미지에 추가 설치 필요, 프로세스 실행·격리 부담 |
| Python(PyMuPDF) 별도 서비스 | 새 런타임·서비스·통신, 사용자 요청(별도 인프라 지양)과 충돌. PyMuPDF는 AGPL |
| 파일 저장소(`storage/extractions`)에 결과 저장 | DB 백업과 분리되어 일관성 관리가 늘어남. 결과 크기(압축 수백 KB)가 DB에 두기에 충분히 작음 |
| 업로드 시 즉시 추출 | 업로드 지연·실패 위험. 사용 시점 지연 추출로 업로드 경로를 바꾸지 않음 |

## 영향

- Backend 실행 파일 크기 증가(PDFBox·fontbox·commons-logging 약 3.5MB).
- 신뢰할 수 없는 PDF를 운영 프로세스에서 해석한다. 페이지·단어·시간 상한, 임시 파일 버퍼, 동시 실행 2개 제한, 예외를 상태로 바꾸는 처리로 영향을 제한한다. 해석기 취약점 대응은 PDFBox 보안 업데이트를 따른다.
- DB 백업 크기가 추출 결과만큼 늘어난다. 결과는 원본에서 다시 만들 수 있다.
