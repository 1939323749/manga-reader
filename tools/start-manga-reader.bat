@echo off
rem Stops a running manga-reader, downloads the newest build, and starts it.
rem Double-click to update + start. Downloads go through the system proxy.
setlocal
cd /d "%~dp0"
set JAR=manga-reader.jar
set URL=https://github.com/1939323749/manga-reader/releases/download/latest/manga-reader.jar

echo Stopping running server on port 4567 (if any)...
powershell -NoProfile -Command "Get-NetTCPConnection -LocalPort 4567 -State Listen -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }"
timeout /t 1 /nobreak > nul

echo Downloading latest build...
powershell -NoProfile -Command "$ProgressPreference='SilentlyContinue'; try { Invoke-WebRequest -UseBasicParsing -Uri '%URL%' -OutFile '%JAR%.download'; Move-Item -Force '%JAR%.download' '%JAR%'; Write-Host 'Downloaded.' } catch { Write-Host ('Download failed, using existing jar: ' + $_.Exception.Message) }"

if not exist "%JAR%" (
  echo No jar available. Check your network/proxy and try again.
  pause
  exit /b 1
)

echo Starting manga-reader on http://localhost:4567 ...
title manga-reader
java -jar "%JAR%"
pause
