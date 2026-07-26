@echo off
REM
REM Copyright 2026 the Small CRM authors.
REM
REM Licensed under the Apache License, Version 2.0 (the "License");
REM you may not use this file except in compliance with the License.
REM You may obtain a copy of the License at
REM
REM     http://www.apache.org/licenses/LICENSE-2.0
REM
REM Unless required by applicable law or agreed to in writing, software
REM distributed under the License is distributed on an "AS IS" BASIS,
REM WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
REM See the License for the specific language governing permissions and
REM limitations under the License.
REM
REM Starts Small CRM. Everything it needs is in this folder.

setlocal

REM Everything is resolved from this script's own folder, so it works whether it is
REM double-clicked or run from a command prompt somewhere else. %~dp0 keeps its
REM trailing backslash.
set "HERE=%~dp0"
cd /d "%HERE%"

if "%SMALLCRM_PORT%"=="" set "SMALLCRM_PORT=8080"
set "URL=http://localhost:%SMALLCRM_PORT%"

if "%SMALLCRM_DATA_DIR%"=="" set "SMALLCRM_DATA_DIR=%HERE%data"
if "%SMALLCRM_BACKUP_DIR%"=="" set "SMALLCRM_BACKUP_DIR=%HERE%backup"
if "%SMALLCRM_LOG_FILE%"=="" set "SMALLCRM_LOG_FILE=%HERE%logs\small-crm.log"
set "QUARKUS_HTTP_PORT=%SMALLCRM_PORT%"

if not exist "%HERE%runtime\bin\java.exe" (
  echo The runtime folder is missing or damaged. Unpack the whole archive again.
  echo.
  pause
  exit /b 1
)

echo.
echo Small CRM is starting. It will open at %URL%
echo Leave this window open while you use it; close it to stop.
echo.

REM Waits for the application to answer before opening the browser, rather than
REM landing immediately on a connection error.
if "%SMALLCRM_NO_BROWSER%"=="" (
  start "" /b powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "for ($i=0; $i -lt 120; $i++) { try { Invoke-WebRequest -UseBasicParsing -Uri '%URL%/q/health/ready' -TimeoutSec 2 | Out-Null; Start-Process '%URL%'; break } catch { Start-Sleep -Seconds 1 } }"
)

"%HERE%runtime\bin\java.exe" -jar "%HERE%app\quarkus-run.jar" %*

REM Keeps the window up if the application stopped straight away, so the reason
REM stays readable instead of the window vanishing.
if errorlevel 1 (
  echo.
  echo Small CRM stopped unexpectedly. The message above says why.
  pause
)

endlocal
