# manga-reader: stop the running server, download the newest build, start it.
# Usage (PowerShell):  irm https://raw.githubusercontent.com/1939323749/manga-reader/main/tools/start.ps1 | iex
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$dir = Join-Path $HOME '.manga-reader'
$jar = Join-Path $dir 'manga-reader.jar'
$tmp = "$jar.download"
$url = 'https://github.com/1939323749/manga-reader/releases/download/latest/manga-reader.jar'
$api = 'https://api.github.com/repos/1939323749/manga-reader/releases/tags/latest'
New-Item -ItemType Directory -Force -Path $dir | Out-Null

# A jar is a zip: starts with "PK" and has the size GitHub reports
function Test-Jar($path, $expected) {
    if (-not (Test-Path $path)) { return $false }
    $len = (Get-Item $path).Length
    $fs = [IO.File]::OpenRead($path)
    try { $b0 = $fs.ReadByte(); $b1 = $fs.ReadByte() } finally { $fs.Close() }
    $okMagic = ($b0 -eq 0x50 -and $b1 -eq 0x4B)
    $okSize = (-not $expected) -or ($len -eq $expected)
    if (-not ($okMagic -and $okSize)) {
        Write-Host ("  got {0:N0} bytes (expected {1:N0}), zip header: {2}" -f $len, $expected, $okMagic)
        if (-not $okMagic -and $len -lt 4096) { Write-Host ('  content: ' + (Get-Content -Raw $path)) }
    }
    return ($okMagic -and $okSize)
}

Write-Host 'Stopping the running server (if any)...'
Get-NetTCPConnection -LocalPort 4567 -State Listen -ErrorAction SilentlyContinue |
    ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }
Start-Sleep -Seconds 1

$expected = $null
try {
    $rel = Invoke-RestMethod -UseBasicParsing -Uri $api
    $expected = ($rel.assets | Where-Object { $_.name -eq 'manga-reader.jar' }).size
} catch { Write-Host "Could not read release info: $($_.Exception.Message)" }

$ok = $false
Write-Host 'Downloading the latest build (~145 MB)...'

# 1) BITS: resumable, follows redirects, uses the system proxy
try {
    Remove-Item -Force $tmp -ErrorAction SilentlyContinue
    Start-BitsTransfer -Source $url -Destination $tmp -ErrorAction Stop
    $ok = Test-Jar $tmp $expected
} catch { Write-Host "  BITS download failed: $($_.Exception.Message)" }

# 2) Invoke-WebRequest with the system proxy
if (-not $ok) {
    Write-Host 'Retrying with Invoke-WebRequest...'
    try {
        Remove-Item -Force $tmp -ErrorAction SilentlyContinue
        $proxy = [Net.WebRequest]::GetSystemWebProxy().GetProxy([Uri]$url)
        if ($proxy.AbsoluteUri -ne $url) {
            Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $tmp -Proxy $proxy.AbsoluteUri
        } else {
            Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $tmp
        }
        $ok = Test-Jar $tmp $expected
    } catch { Write-Host "  Invoke-WebRequest failed: $($_.Exception.Message)" }
}

if ($ok) {
    Move-Item -Force $tmp $jar
    Write-Host 'Downloaded and verified.'
} else {
    Remove-Item -Force $tmp -ErrorAction SilentlyContinue
    if (Test-Jar $jar $null) {
        Write-Host 'Download failed; starting the previous build instead.'
    } else {
        Remove-Item -Force $jar -ErrorAction SilentlyContinue
        Write-Host 'Download failed and there is no previous build. Paste the messages above to Claude.'
        return
    }
}

Write-Host 'Starting manga-reader at http://localhost:4567 (close this window to stop it)'
Start-Process 'http://localhost:4567'
java -jar $jar
