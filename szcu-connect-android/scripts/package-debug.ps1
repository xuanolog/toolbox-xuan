param([string]$Label = '0.1.0')
$ErrorActionPreference = 'Stop'
if ($Label -notmatch '^[0-9A-Za-z.-]+$') { throw 'Invalid release label' }
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    $dirty = git status --porcelain
    if ($LASTEXITCODE -ne 0 -or $dirty) { throw 'Commit reviewed source changes before packaging.' }
    & .\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Build or verification failed.' }
    $releaseDir = Join-Path $projectRoot 'releases'
    New-Item -ItemType Directory -Force $releaseDir | Out-Null
    $apkPath = Join-Path $releaseDir "SZCU-Connect-$Label-debug.apk"
    Copy-Item -LiteralPath 'app/build/outputs/apk/debug/app-debug.apk' -Destination $apkPath
    $revision = (git rev-parse HEAD).Trim()
    $sha = (Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash.ToLowerInvariant()
    $metadata = [ordered]@{
        version = $Label; variant = 'debug'; sourceCommit = $revision
        apk = (Split-Path -Leaf $apkPath); sha256 = $sha
        builtAtUtc = [DateTime]::UtcNow.ToString('o')
        acceptance = 'See docs/TESTING.md; building does not imply real-account acceptance.'
    }
    $metadata | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $releaseDir 'release-manifest.json') -Encoding utf8
    "$sha  $(Split-Path -Leaf $apkPath)" | Set-Content -LiteralPath (Join-Path $releaseDir 'SHA256SUMS.txt') -Encoding ascii
    Write-Output "APK: $apkPath"
    Write-Output "Source: $revision"
    Write-Output "SHA256: $sha"
} finally { Pop-Location }
