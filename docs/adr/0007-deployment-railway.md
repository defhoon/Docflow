# ADR-0007: 공개 배포 구성 — Railway 단일 플랫폼

- 상태: 승인(구성 방향, 2026-11-10 사용자 승인) / 실제 리소스 생성·공개는 별도 승인 대기
- Issue: #27

## 결정

1. Railway 한 프로젝트에 4개 리소스: Frontend(Next.js standalone, 공개 HTTPS 도메인), Backend(Spring Boot, **공개 도메인 없음**, 비공개 네트워크 전용), PostgreSQL 16(비공개, TCP Proxy 비활성), Backend 영속 볼륨(`/data`).
2. 브라우저는 Frontend 도메인만 호출한다. `/api/*`는 Next.js rewrite가 비공개 네트워크(`http://<backend>.railway.internal:8080`)로 프록시한다. 같은 출처이므로 기존 세션 쿠키·`csrf.spa()` 구조와 CORS 미설정을 그대로 유지한다.
3. Backend는 `SPRING_PROFILES_ACTIVE=prod`(`application-prod.yml`): Secure 쿠키, `.env` 미사용, 저장 경로 `/data/storage`, Office→PDF 변환 비활성화(`office-enabled=false`, `require-isolation=true`), graceful shutdown.
4. 이미지: `backend/Dockerfile`(Temurin 25, 비root 실행, entrypoint가 볼륨 소유권만 정리, `pg_dump` 포함, LibreOffice 미포함), `frontend/Dockerfile`(Node 22 alpine, standalone, `node` 사용자, `BACKEND_URL` 빌드 인자 필수).
5. 최초 관리자는 사용자 0명일 때 로그에 1회 출력되는 일회용 관리자 초대코드로 가입한다(SPEC-AUTH 확장 E11). 고정 계정·비밀번호 없음.

## 고려한 대안

| 대안 | 채택하지 않은 이유 |
| --- | --- |
| Vercel(Frontend) + Railway(Backend) | 외부 rewrite의 20MiB 업로드 통과 여부 미검증(요청 본문 한도), 크로스 오리진 시 쿠키·CSRF 재설계 필요 |
| Cloudflare R2 파일 저장 | `FileStorage` 새 구현과 SDK 의존성 필요. 24시간 범위에서는 볼륨이 단순. 백업 부담은 아래 절차로 보완 |
| Spring Session JDBC(재배포 후 세션 유지) | 새 의존성·테이블. 현재는 재배포 시 재로그인으로 수용(미결정, 필요 시 별도 승인) |
| LibreOffice 운영 활성화 | Railway 컨테이너에서 `unshare` 등 OS 격리 동작이 검증되지 않음. 사용자 정책상 비활성화 |

## 영향·제약

- 단일 Backend 인스턴스 전제: 메모리 세션, 메모리 시도 제한, 로컬 볼륨. 수평 확장 시 재설계 필요.
- 재배포·재시작 시 로그인 세션이 끊긴다(데이터는 유지, 클라우드에서 재시작 검증).
- Hobby 요금제 볼륨 상한 5GB(문서 파일 용량 상한). 초과 예상 시 Pro 또는 R2 검토.
- 볼륨은 백업이 아니다. `docs/deployment.md` §6의 DB·파일 백업·복구 절차를 공개 전 1회 실행해 검증한다.
