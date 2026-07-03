# Build script for Windows (PowerShell)
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location (Split-Path -Parent $scriptDir)
if (-not (Test-Path build)) { New-Item -ItemType Directory -Path build | Out-Null }
Set-Location build
cmake .. -DCMAKE_BUILD_TYPE=Release
cmake --build . --config Release
Write-Host "Build complete"
