# DocFlow Backend 운영 이미지 (#27, ADR-0007). 빌드 컨텍스트: backend/
# Office→PDF 변환은 운영에서 비활성화(기본)하므로 LibreOffice를 설치하지 않는다.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon --version
COPY src src
RUN ./gradlew --no-daemon bootJar -x test && cp build/libs/*-SNAPSHOT.jar /workspace/app.jar

FROM eclipse-temurin:25-jre
# postgresql-client: 컨테이너 안에서 pg_dump 백업용(docs/deployment.md). 서버 주 버전(16)과 맞춘다.
RUN apt-get update \
    && apt-get install -y --no-install-recommends postgresql-client \
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
