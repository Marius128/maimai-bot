param(
    [int]$Port = 8082
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$executable = Join-Path $projectRoot ".runtime\maimai-adapter\maimai.py-windows-amd64.exe"

if (-not (Test-Path -LiteralPath $executable -PathType Leaf)) {
    throw "maimai.py adapter is not installed. Run scripts\install-maimai-adapter.ps1 first."
}

Write-Host "Starting maimai.py adapter at http://127.0.0.1:$Port"
Write-Host "Keep this window open while KokaBot is running."
& $executable --host 127.0.0.1 --port $Port 2>&1 |
    Where-Object { $_ -notmatch '(credentials=|identifiers\?code=)' }
