@echo off
echo ========================================
echo Live Video Streaming Solution - Tests
echo ========================================

echo.
echo Cleaning previous test results...
call mvn clean

echo.
echo Running service layer tests...
call mvn test

echo.
echo ========================================
echo Test Results Summary
echo ========================================
echo.
echo Tests completed! Check the output above for results.
echo.
echo Test Coverage:
echo - Unit Tests: Service layer functionality (StreamServiceTest.java)
echo - FFmpeg Service: Mocked and tested
echo - Total Test Methods: 25+ test methods
echo.
pause
