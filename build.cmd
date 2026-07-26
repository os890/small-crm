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
rem Builds the Small CRM packages on a Windows machine with nothing installed.
rem
rem   build.cmd                 all three platforms
rem   build.cmd windows-x64     just one
rem
rem No Java, no Maven, no Node, no pnpm needed beforehand. bootstrap-node.ps1 fetches a Node,
rem packaging\bootstrap-build.mjs fetches the rest, and everything lands in .build-tools\ inside
rem this folder. PowerShell and tar do the unpacking; Windows 10 and later have both.

setlocal
set "HERE=%~dp0"
set "NODE=%HERE%.build-tools\node\node.exe"

if not exist "%NODE%" (
  powershell -NoProfile -ExecutionPolicy Bypass -File "%HERE%packaging\bootstrap-node.ps1"
  if errorlevel 1 exit /b 1
)

"%NODE%" "%HERE%packaging\bootstrap-build.mjs" %*
exit /b %errorlevel%
