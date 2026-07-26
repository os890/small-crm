# Copyright 2026 the Small CRM authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Fetches the Node that build.cmd hands the rest of the build over to. The Windows counterpart
# of the first half of build.sh, in PowerShell because that is what Windows brings along.

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

# Node 22 is the current long-term release and the one the frontend is built against. Corepack
# comes with it, which is how pnpm arrives at the pinned version without installing anything.
$nodeMajor = 22
$dist = "https://nodejs.org/dist/latest-v$nodeMajor.x"

$project = Split-Path -Parent $PSScriptRoot
$tools = Join-Path $project ".build-tools"
$cache = Join-Path $tools "cache"
$nodeDir = Join-Path $tools "node"

if (Test-Path (Join-Path $nodeDir "node.exe")) {
  exit 0
}

if ($env:PROCESSOR_ARCHITECTURE -notin @("AMD64", "x86")) {
  throw "Unsupported architecture: $env:PROCESSOR_ARCHITECTURE. This builds on Windows x64."
}

Write-Host "Fetching Node $nodeMajor for windows-x64"

# One plain text file carries both the current version and its checksum, so there is no version
# pinned here to go stale.
$sums = (Invoke-WebRequest -Uri "$dist/SHASUMS256.txt" -UseBasicParsing).Content
$line = ($sums -split "`n" | Where-Object { $_ -match "-win-x64\.zip\s*$" } | Select-Object -First 1)
if (-not $line) {
  throw "nodejs.org lists no $nodeMajor.x build for win-x64."
}
$fields = $line.Trim() -split "\s+"
$expected = $fields[0]
$name = $fields[1]

New-Item -ItemType Directory -Force -Path $cache | Out-Null
$archive = Join-Path $cache $name

if (-not (Test-Path $archive) -or (Get-FileHash $archive -Algorithm SHA256).Hash -ne $expected) {
  Write-Host "  downloading $name"
  Invoke-WebRequest -Uri "$dist/$name" -OutFile "$archive.part" -UseBasicParsing
  Move-Item -Force "$archive.part" $archive
}

if ((Get-FileHash $archive -Algorithm SHA256).Hash -ne $expected) {
  Remove-Item $archive
  throw "$name does not match the checksum nodejs.org published; it was not used."
}

# Unpacked to a fixed folder rather than a versioned one, so a Node that moves on does not leave
# copies behind. The archive wraps everything in node-v<version>-win-x64, which is dropped here.
$staging = Join-Path $tools "node-staging"
Remove-Item -Recurse -Force $staging, $nodeDir -ErrorAction SilentlyContinue
Expand-Archive -Path $archive -DestinationPath $staging -Force
$unwrapped = Get-ChildItem -Path $staging -Directory | Select-Object -First 1
Move-Item $unwrapped.FullName $nodeDir
Remove-Item -Recurse -Force $staging

Write-Host "  $(& (Join-Path $nodeDir 'node.exe') --version)`n"
