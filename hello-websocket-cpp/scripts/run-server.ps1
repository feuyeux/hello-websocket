# Run server on Windows (PowerShell)
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location (Split-Path -Parent $scriptDir)
& ".\build\Release\ws_server.exe"
