# Auto-deploy script: waits for device connection, builds, installs, and launches app.
$ErrorActionPreference = "Stop"

# Ensure Android SDK is on PATH
if (-not $env:ANDROID_HOME -and (Test-Path "$env:LOCALAPPDATA\Android\Sdk")) {
    $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
}
if ($env:ANDROID_HOME) {
    $platformTools = Join-Path $env:ANDROID_HOME "platform-tools"
    if ((Test-Path $platformTools) -and ($env:PATH -notlike "*$platformTools*")) {
        $env:PATH = "$platformTools;$env:PATH"
    }
}

$adb = Get-Command adb -ErrorAction SilentlyContinue
if (-not $adb) {
    Write-Host "Error: adb not found. Ensure Android SDK platform-tools installed." -ForegroundColor Red
    exit 1
}

$gradlew = Join-Path $PSScriptRoot "gradlew.bat"
$appPackage = "me.zhanghai.android.files"
$mainActivity = "$appPackage/.filelist.FileListActivity"

Write-Host "=============================================" -ForegroundColor Cyan
Write-Host " Auto Deploy Watcher Active" -ForegroundColor Cyan
Write-Host " Plug in USB or connect Wi-Fi ADB anytime" -ForegroundColor Cyan
Write-Host " Press Ctrl+C to stop" -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan

while ($true) {
    Write-Host "`nWaiting for Android device..." -ForegroundColor Yellow
    & adb wait-for-device

    $deviceInfo = & adb devices -l | Select-String "device " | Select-Object -First 1
    Write-Host "Device connected: $($deviceInfo.Line.Trim())" -ForegroundColor Green
    Write-Host "Building and installing APK..." -ForegroundColor Cyan

    & $gradlew installDebug
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Launching $appPackage..." -ForegroundColor Green
        & adb shell am start -n $mainActivity
    } else {
        Write-Host "Build failed with exit code $LASTEXITCODE." -ForegroundColor Red
    }

    Write-Host "App ready. Waiting for disconnect..." -ForegroundColor DarkGray
    while ((& adb get-state 2>$null) -eq "device") {
        Start-Sleep -Seconds 2
    }
    Write-Host "Device disconnected." -ForegroundColor DarkYellow
}
