# ADR-0004: Java 21 → Java 25 LTS 업그레이드

- 상태: **Accepted**
- 날짜: 2026-10-22
- 대체 대상: [ADR-0001](./0001-technology-stack.md)의 Java 버전 결정(Java 21). ADR-0001의 다른 결정은 유지한다.
- 관련 문서: [`spec.md`](../../spec.md) §11.1, §15.2, §17

## 배경

6주차 초기화에서는 클라우드 개발 환경에 설치된 JDK로 바로 검증하기 위해 Java 21 LTS를 채택하였다(ADR-0001). 이때 Java 25는 "Toolchain 다운로드가 필요하다"는 이유로 대안으로만 남겨 두었다.

7주차 핵심 기능 구현을 시작하기 전에, 비즈니스 코드가 없는 지금 최신 LTS인 Java 25로 전환한다. 코드가 쌓인 뒤에 전환하면 영향 범위가 커지기 때문이다.

## 검토한 대안

| 대안 | 장점 | 단점 |
| --- | --- | --- |
| Java 21 유지 | 변경 없음. 클라우드 환경에서 바로 검증 가능 | 기능 개발 이후 전환 시 비용 증가. 지원 기간이 25보다 짧음 |
| **Java 25로 전환 (채택)** | 최신 LTS. 코드가 거의 없는 지금 전환 비용이 최소 | Claude Code 클라우드에서 JDK 25를 쓸 수 없어 클라우드 검증 불가 |

JDK를 준비하는 방식도 함께 검토하였다.

| 방식 | 결정 |
| --- | --- |
| **A. 개발자가 JDK 25를 직접 설치 (채택)** | 의존성을 추가하지 않는다. CI는 `actions/setup-java`가 설치한다. |
| B. `foojay-resolver-convention`으로 Gradle이 자동 다운로드 | 채택하지 않음. 빌드 중 외부 다운로드가 생기고, 클라우드에서는 foojay가 차단되어 효과가 없다. |

## 결정

- Gradle Java Toolchain: `JavaLanguageVersion.of(21)` → `JavaLanguageVersion.of(25)` (`backend/build.gradle`)
- GitHub Actions: `actions/setup-java@v5`, `distribution: temurin`, `java-version: "25"`
- 그 외 버전은 유지한다: Spring Boot 4.1.1, Gradle Wrapper 9.8.1, io.spring.dependency-management 1.1.7, Frontend 전체.
- Windows 체크아웃에서 줄바꿈 문제가 생기지 않도록 `.gitattributes`를 추가한다.
  - `backend/gradlew`는 `eol=lf`
  - `*.bat`은 `eol=crlf`
  - `*.jar`는 `binary`

## 호환성 근거

| 구성 요소 | 버전 | Java 25 |
| --- | --- | --- |
| Gradle | 9.8.1 | Gradle 9.1.0부터 Java 25로 실행·Toolchain 지원 |
| Spring Boot / Spring Framework | 4.1.1 / 7.0.9 | Java 17 이상, Java 25 지원 |
| Hibernate ORM / Flyway / PostgreSQL JDBC | 7.4.5 / 12.4.0 / 42.7.13 | Java 25 지원 버전 |

위 표는 각 프로젝트의 공식 호환성 정보 기준이다. 실제 동작은 아래 "검증 방법"의 결과로 확인한다.

## 영향

- 애플리케이션 코드, 테스트 코드, `application.yml`, Flyway migration, 기능 SPEC은 변경하지 않는다.
- 컴파일 결과물(class 파일, `bootJar`)은 Java 25 런타임에서만 실행된다.
- 모든 개발 환경과 CI에 JDK 25가 필요하다. JDK 25가 없으면 Gradle이 `Cannot find a Java installation ... languageVersion=25` 오류로 중단한다.
- Claude Code 클라우드 환경에는 JDK 21만 있다. JDK 25 다운로드 경로(Adoptium API, foojay, GitHub 릴리스)도 네트워크 정책으로 차단된다(2026-10-22 확인). 따라서 클라우드에서는 Backend 빌드·테스트를 실행할 수 없고, **Java 25 검증은 GitHub Actions와 로컬에서 수행한다.**
- Java 24+에서는 일부 라이브러리가 `sun.misc.Unsafe`를 쓰거나(JEP 498), 실행 중 에이전트를 붙일 때(JEP 451) 경고가 출력될 수 있다. 테스트 실패 원인은 아니며, 경고를 숨기는 설정은 추가하지 않는다.
- Java 21로 수행한 6주차 기록(ADR-0001 본문, `spec.md` §15.1, §17 2026-10-14)은 수정하지 않고 보존한다.

## 검증 방법

| 환경 | 방법 |
| --- | --- |
| GitHub Actions | Backend job 로그에서 JDK 25.0.x 설치 확인, `./gradlew build` 통과(테스트 5건). Frontend job 통과 |
| Windows 로컬 | `java -version` 25.0.x → `docker compose up -d` → `.\gradlew.bat clean build` → `.\gradlew.bat bootRun` → `/actuator/health` UP → `npm run dev` 화면 확인 |
| Claude Code 클라우드 | JDK 25가 없어 Backend 실행 불가. diff 범위 검토와 Frontend 검증만 수행 |

결과는 `spec.md` §15.2에 기록한다.
