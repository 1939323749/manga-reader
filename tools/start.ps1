# manga-reader: stop the running server, download the newest build, start it.
# Usage (PowerShell):  irm https://raw.githubusercontent.com/1939323749/manga-reader/main/tools/start.ps1 | iex
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$dir = Join-Path $HOME '.manga-reader'
$jar = Join-Path $dir 'manga-reader.jar'
$url = 'https://github.com/1939323749/manga-reader/releases/download/latest/manga-reader.jar'
New-Item -ItemType Directory -Force -Path $dir | Out-Null

Write-Host 'Stopping the running server (if any)...'
Get-NetTCPConnection -LocalPort 4567 -State Listen -ErrorAction SilentlyContinue |
    ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }
Start-Sleep -Seconds 1

Write-Host 'Downloading the latest build (~145 MB)...'
try {
    Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile "$jar.download"
    Move-Item -Force "$jar.download" $jar
    Write-Host 'Downloaded.'
} catch {
    Write-Host "Download failed: $($_.Exception.Message)"
    if (-not (Test-Path $jar)) { Write-Host 'No previous build to fall back to.'; return }
    Write-Host 'Starting the previous build instead.'
}

Write-Host 'Starting manga-reader at http://localhost:4567 (close this window to stop it)'
Start-Process 'http://localhost:4567'
java -jar $jar
