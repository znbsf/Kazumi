param(
    [Parameter(Mandatory = $true)][string]$Name,
    [int[]]$Keys = @(),
    [ValidateRange(0, 10000)][int]$SettleMilliseconds = 600,
    [string]$Serial = 'emulator-5562',
    [string]$AvdName = 'Kazumi_Phase1_A_API36',
    [string]$AdbPath = (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe')
)
$ErrorActionPreference = 'Stop'
$adb = $AdbPath
$actualAvd = & $adb -s $Serial emu avd name
if ($LASTEXITCODE -ne 0 -or $actualAvd[0].Trim() -ne $AvdName) {
    throw "Refusing capture: $Serial is not the assigned AVD $AvdName"
}
if ($Name -notmatch '^[a-zA-Z0-9_-]+$') { throw 'Use a plain capture name' }
$outputDir = Join-Path $PSScriptRoot '..\artifacts\phase1-hardening'
New-Item -ItemType Directory -Force $outputDir | Out-Null
foreach ($key in $Keys) {
    & $adb -s $Serial shell input keyevent $key
    if ($LASTEXITCODE -ne 0) { throw "Key injection failed: $key" }
    Start-Sleep -Milliseconds 450
}
Start-Sleep -Milliseconds $SettleMilliseconds
& $adb -s $Serial shell screencap -p /sdcard/a-hardening-capture.png
if ($LASTEXITCODE -ne 0) { throw 'Screenshot failed' }
& $adb -s $Serial pull /sdcard/a-hardening-capture.png (Join-Path $outputDir "$Name.png")
if ($LASTEXITCODE -ne 0) { throw 'Screenshot transfer failed' }
[ordered]@{
    capturedAt = (Get-Date).ToString('o')
    serial = $Serial
    avd = $AvdName
    keys = $Keys
    settleMilliseconds = $SettleMilliseconds
    displaySize = (& $adb -s $Serial shell wm size) -join "`n"
    density = (& $adb -s $Serial shell wm density) -join "`n"
    fontScale = (& $adb -s $Serial shell settings get system font_scale) -join "`n"
    nightMode = (& $adb -s $Serial shell cmd uimode night) -join "`n"
    package = (& $adb -s $Serial shell dumpsys package com.znbsf.kazumi.tv |
        Select-String 'versionCode=|versionName=') -join "`n"
} | ConvertTo-Json | Set-Content (Join-Path $outputDir "$Name.json")
