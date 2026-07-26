@echo off
rem Copyright 2026 the Small CRM authors.
rem
rem Licensed under the Apache License, Version 2.0 (the "License");
rem you may not use this file except in compliance with the License.
rem You may obtain a copy of the License at
rem
rem     http://www.apache.org/licenses/LICENSE-2.0
rem
rem Unless required by applicable law or agreed to in writing, software
rem distributed under the License is distributed on an "AS IS" BASIS,
rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
rem See the License for the specific language governing permissions and
rem limitations under the License.
rem
rem Runs the application that was just built here.
rem
rem   build.cmd && start.cmd
rem   set SMALLCRM_PORT=9000 && start.cmd
rem
rem The Java it uses is the one build.cmd fetched, so this works on a machine that has no Java
rem installed and needs no unpacking of a distribution archive first. A JAVA_HOME or a java on
rem the PATH is used if there is no fetched one, so this also works after a plain mvn package.
rem
rem The counterpart of packaging\templates\start.cmd, which goes inside the distributions and
rem starts the copy of Java that travels with them.

setlocal

rem %~dp0 keeps its trailing backslash.
set "HERE=%~dp0"
cd /d "%HERE%"

set "JAR=%HERE%target\quarkus-app\quarkus-run.jar"

if "%SMALLCRM_PORT%"=="" set "SMALLCRM_PORT=8080"
set "URL=http://localhost:%SMALLCRM_PORT%"
set "QUARKUS_HTTP_PORT=%SMALLCRM_PORT%"

rem No SMALLCRM_DATA_DIR or SMALLCRM_BACKUP_DIR on purpose: the defaults already put data\,
rem backup\ and logs\ beside the project, the same as an ordinary run would.

if not exist "%JAR%" (
  echo Nothing built yet: %JAR% is not there.
  echo Run build.cmd first, which fetches what it needs and builds it.
  echo.
  pause
  exit /b 1
)

rem The JDK build.cmd fetched comes first, so a Java that happens to be installed cannot change
rem what this runs.
set "JAVA_CMD="
for /d %%d in ("%HERE%.build-tools\jdk\*") do (
  if exist "%%d\bin\java.exe" set "JAVA_CMD=%%d\bin\java.exe"
)

if not defined JAVA_CMD (
  if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
  )
)

if not defined JAVA_CMD (
  where java >nul 2>&1 && set "JAVA_CMD=java"
)

if not defined JAVA_CMD (
  echo No Java found: none fetched in .build-tools\, no JAVA_HOME, none on the PATH.
  echo Run build.cmd, which fetches one.
  echo.
  pause
  exit /b 1
)

echo.
echo Small CRM is starting. It will open at %URL%
echo Leave this window open while you use it; close it to stop.
echo.

rem Waits for the application to answer before opening the browser, rather than landing
rem immediately on a connection error.
if "%SMALLCRM_NO_BROWSER%"=="" (
  start "" /b powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "for ($i=0; $i -lt 120; $i++) { try { Invoke-WebRequest -UseBasicParsing -Uri '%URL%/q/health/ready' -TimeoutSec 2 | Out-Null; Start-Process '%URL%'; break } catch { Start-Sleep -Seconds 1 } }"
)

"%JAVA_CMD%" -jar "%JAR%" %*

rem Keeps the window up if the application stopped straight away, so the reason stays readable
rem instead of the window vanishing.
if errorlevel 1 (
  echo.
  echo Small CRM stopped unexpectedly. The message above says why.
  pause
)

endlocal
