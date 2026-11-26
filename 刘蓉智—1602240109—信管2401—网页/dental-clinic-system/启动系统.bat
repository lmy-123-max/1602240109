@echo off
title Dental Clinic Management System
cd /d "%~dp0"

rem ---- Find a Java 17+ runtime ----
set "JAVA_EXE="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if not defined JAVA_EXE (
    for /f "delims=" %%i in ('where java 2^>nul') do (
        if not defined JAVA_EXE set "JAVA_EXE=%%i"
    )
)
if not defined JAVA_EXE if exist "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.2\jbr\bin\java.exe" set "JAVA_EXE=C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.2\jbr\bin\java.exe"

if not defined JAVA_EXE (
    echo [ERROR] Java 17+ not found. Please install JDK 17 or later.
    pause
    exit /b 1
)

echo ==================================================
echo   Dental Clinic Management System
echo   http://localhost:8080/index.html   (front desk)
echo   http://localhost:8080/admin.html   (admin console)
echo   Login: patient01 / doctor01 / store101 / hq001
echo   Password: 123456
echo   Press Ctrl+C to stop
echo ==================================================
echo   Starting server, please wait 10-20 seconds...
echo.

rem Open browser automatically after server is up
start "" cmd /c "timeout /t 12 /nobreak >nul & start "" http://localhost:8080/index.html"

"%JAVA_EXE%" -jar target\dental-clinic-system-1.0.0.jar --server.port=8080

echo.
echo Server stopped.
pause
