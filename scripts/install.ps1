$ErrorActionPreference = "Stop"

$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    $adb = "adb"
}

& $adb install -r ".\app\build\outputs\apk\debug\app-debug.apk"

Write-Host "APK installed. For supervised uninstall protection, run scripts\\provision-device-owner.ps1 on an eligible device."

