#!/usr/bin/env bash
# Starts nginx-rtmp in Docker, builds the app, and runs it on the host so it can reach the webcam.
set -euo pipefail
cd "$(dirname "$0")"

docker compose up -d nginx-rtmp
./mvnw -q -DskipTests package
exec java -jar target/live-video-streaming-*.jar "$@"
