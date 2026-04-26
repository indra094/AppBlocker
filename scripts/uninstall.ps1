$ErrorActionPreference = "Stop"

$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    $adb = "adb"
}

$component = "com.indrajeet.appblocker/com.indrajeet.appblocker.admin.BlockDeviceAdminReceiver"

try {
    & $adb shell dpm remove-active-admin $component
} catch {
    Write-Host "Device admin removal step did not complete cleanly. Continuing to uninstall attempt."
}

& $adb uninstall com.indrajeet.appblocker

