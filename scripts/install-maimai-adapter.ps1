param(
    [string]$Version = "v1.5.2rc2"
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$runtimeDirectory = Join-Path $projectRoot ".runtime\maimai-adapter"
$executable = Join-Path $runtimeDirectory "maimai.py-windows-amd64.exe"
$downloadUrl = "https://github.com/TrueRou/maimai.py/releases/download/$Version/maimai.py-windows-amd64.exe"

New-Item -ItemType Directory -Force -Path $runtimeDirectory | Out-Null
Write-Host "Downloading maimai.py $Version from the official GitHub release..."
Invoke-WebRequest -Uri $downloadUrl -OutFile $executable

$hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $executable).Hash
Write-Host "Installed: $executable"
Write-Host "SHA-256:  $hash"
Write-Host "Run scripts\start-maimai-adapter.ps1 before starting KokaBot."
