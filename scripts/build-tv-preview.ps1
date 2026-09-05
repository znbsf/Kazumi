param(
    [string]$Flutter = 'flutter',
    [int]$BuildNumber = 203004
)
$ErrorActionPreference = 'Stop'
$repoPath = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $repoPath
try {
    $fixturePath = Join-Path $repoPath 'test/fixtures/danmaku/tv_preview.json'
    $fixture = [Convert]::ToBase64String([IO.File]::ReadAllBytes($fixturePath))
    & $Flutter build apk --release --flavor tv --build-number $BuildNumber `
        --dart-define=KAZUMI_TV_DEMO_DANMAKU=true `
        "--dart-define=KAZUMI_TV_DEMO_FIXTURE=$fixture"
    if ($LASTEXITCODE -ne 0) { throw 'TV preview APK build failed' }
} finally {
    Pop-Location
}
