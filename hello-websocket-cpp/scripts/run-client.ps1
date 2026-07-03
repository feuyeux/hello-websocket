# Run client on Windows (PowerShell)
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location (Split-Path -Parent $scriptDir)
& ".\build\Release\ws_client.exe"
