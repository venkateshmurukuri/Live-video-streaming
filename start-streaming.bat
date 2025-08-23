@echo off
echo ========================================
echo Live Video Streaming Solution
echo ========================================

echo.
echo Starting NGINX RTMP Server...
docker-compose up -d

echo.
echo Waiting for NGINX to start...
timeout /t 5 /nobreak >nul

echo.
echo Building the application...
call mvn clean package

echo.
echo Starting the application...
java -jar target/live-video-streaming-0.0.1-SNAPSHOT.war

echo.
echo ========================================
echo Application started successfully!
echo ========================================
echo.
echo Access the web interface at: http://localhost:8080
echo NGINX RTMP server running on: rtmp://localhost:1935
echo HLS content available at: http://localhost:8082
echo.
pause
