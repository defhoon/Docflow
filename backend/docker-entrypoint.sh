#!/bin/sh
# 영속 볼륨(/data) 소유권을 맞춘 뒤 비root 사용자(docflow)로 Backend를 실행한다 (#27).
set -eu
STORAGE_DIR="${DOCFLOW_STORAGE_DIR:-/data/storage}"
if [ "$(id -u)" = "0" ]; then
    mkdir -p "$STORAGE_DIR"
    # 소유자가 다른 항목만 고친다(대용량 볼륨에서 매번 전체 chown 하지 않음).
    find /data -xdev ! -user docflow -exec chown docflow:docflow {} +
    exec setpriv --reuid=docflow --regid=docflow --init-groups java -jar /app/app.jar "$@"
fi
exec java -jar /app/app.jar "$@"
