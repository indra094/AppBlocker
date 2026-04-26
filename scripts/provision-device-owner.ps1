$ErrorActionPreference = "Stop"

$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    $adb = "adb"
}

$component = "com.indrajeet.appblocker/com.indrajeet.appblocker.admin.BlockDeviceAdminReceiver"

& $adb install -r ".\app\build\outputs\apk\debug\app-debug.apk"
& $adb shell dpm set-device-owner $component
