# Starts nginx-rtmp in Docker, builds the app, and runs it on the host so it can reach the webcam.
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

docker compose up -d nginx-rtmp
.\mvnw.cmd -q -DskipTests package
$jar = Get-ChildItem target\live-video-streaming-*.jar | Select-Object -First 1
java -jar $jar.FullName @args
