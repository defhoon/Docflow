# DocFlow Backend 운영 이미지 (#27, ADR-0007). 빌드 컨텍스트: backend/
# Office→PDF 변환은 운영에서 비활성화(기본)하므로 LibreOffice를 설치하지 않는다.
# 베이스 OS를 Ubuntu 24.04(noble)로 고정한다(#45). 태그 없이 쓰면 OS가 바뀌며 PostgreSQL 클라이언트 주 버전도 바뀐다
# (26.04 기반에서 pg_dump/pg_restore 18.6 → PostgreSQL 16 복원 실패). noble 태그도 패키지 갱신은 계속되므로
# 클라이언트 주 버전은 아래에서 패키지 이름으로 고정하고, CI 스모크 테스트가 실제 버전과 복원을 확인한다.
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon --version
COPY src src
RUN ./gradlew --no-daemon bootJar -x test && cp build/libs/*-SNAPSHOT.jar /workspace/app.jar

FROM eclipse-temurin:25-jre-noble
# postgresql-client-16: 컨테이너 안 pg_dump/pg_restore 백업·복원용(docs/deployment.md §7). 서버 주 버전(16)과 맞춘다.
RUN apt-get update \
    && apt-get install -y --no-install-recommends postgresql-client-16 \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system docflow && useradd --system --gid docflow --home-dir /app docflow \
    && mkdir -p /app /data/storage && chown docflow:docflow /data /data/storage
WORKDIR /app
COPY --from=build /workspace/app.jar /app/app.jar
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod 0755 /app/docker-entrypoint.sh
ENV SPRING_PROFILES_ACTIVE=prod \
    DOCFLOW_STORAGE_DIR=/data/storage \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
# 볼륨이 root 소유로 마운트될 수 있어 entrypoint가 소유권만 맞춘 뒤 비root 사용자로 실행한다.
ENTRYPOINT ["/app/docker-entrypoint.sh"]
