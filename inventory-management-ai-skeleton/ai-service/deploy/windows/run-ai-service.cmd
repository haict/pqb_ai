@echo off
rem Chay AI Service (D-64) cho Task Scheduler. Xem docs/10-deployment.md muc 5.
rem Tham so: %1 = file bien moi truong (ai-service.env), %2 = thu muc log.
rem Thu muc cai dat = hai cap tren thu muc chua file nay (ai-service\deploy\windows -> ai-service).
setlocal
if "%~1"=="" (echo Thieu tham so file bien moi truong & exit /b 2)
if "%~2"=="" (echo Thieu tham so thu muc log & exit /b 2)
set "AI_SERVICE_ENV_FILE=%~1"
set "LOG_DIR=%~2"
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"
cd /d "%~dp0..\.."
".venv\Scripts\python.exe" -m app >> "%LOG_DIR%\ai-service.log" 2>&1
exit /b %ERRORLEVEL%
