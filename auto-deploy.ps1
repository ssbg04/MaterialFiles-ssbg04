# Auto-deploy script: watches code changes AND device connections, then auto-installs & launches.
$ErrorActionPreference = "Stop"

if (-not $env:ANDROID_HOME -and (Test-Path "$env:LOCALAPPDATA\Android\Sdk")) {
    $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
}
if ($env:ANDROID_HOME) {
    $platformTools = Join-Path $env:ANDROID_HOME "platform-tools"
    if ((Test-Path $platformTools) -and ($env:PATH -notlike "*$platformTools*")) {
        $env:PATH = "$platformTools;$env:PATH"
    }
}

$gradlew = Join-Path $PSScriptRoot "gradlew.bat"
$appPackage = "me.zhanghai.android.files"
$mainActivity = "$appPackage/.filelist.FileListActivity"

function Deploy-App {
    $state = & adb get-state 2>$null
    if ($state -ne "device") {
        Write-Host "Waiting for device..." -ForegroundColor Yellow
        & adb wait-for-device
    }
    Write-Host "[$(Get-Date -Format 'HH:mm:ss')] Code change detected! Rebuilding & installing..." -ForegroundColor Cyan
    & $gradlew installDebug
    if ($LASTEXITCODE -eq 0) {
        Write-Host "[$(Get-Date -Format 'HH:mm:ss')] Launching app..." -ForegroundColor Green
        & adb shell am start -n $mainActivity | Out-Null
    } else {
        Write-Host "Build failed." -ForegroundColor Red
    }
}

Write-Host "=============================================" -ForegroundColor Cyan
Write-Host " Live Auto-Deploy Active" -ForegroundColor Cyan
Write-Host " Save any Kotlin/XML file -> Auto-reinstalls" -ForegroundColor Cyan
Write-Host " Press Ctrl+C to stop" -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan

# Initial install on start
Deploy-App

# Setup FileSystemWatcher on app/src/main
$watcher = New-Object System.IO.FileSystemWatcher
$watcher.Path = Join-Path $PSScriptRoot "app\src\main"
$watcher.IncludeSubdirectories = $true
$watcher.EnableRaisingEvents = $true
$watcher.NotifyFilter = [System.IO.NotifyFilters]'FileName, LastWrite'

$lastTrigger = [DateTime]::MinValue
$action = {
    $path = $Event.SourceEventArgs.FullPath
    if ($path -match '\.(kt|xml|gradle)$') {
        $now = [DateTime]::Now
        if (($now - $lastTrigger).TotalSeconds -ge 3) {
            $global:lastTrigger = $now
            Deploy-App
        }
    }
}

Register-ObjectEvent $watcher "Changed" -Action $action | Out-Null
Register-ObjectEvent $watcher "Created" -Action $action | Out-Null

try {
    while ($true) { Start-Sleep -Seconds 1 }
} finally {
    $watcher.EnableRaisingEvents = $false
    $watcher.Dispose()
    Get-EventSubscriber | Unregister-Event
}
