param(
    [switch]$KeepBuckets
)

$ErrorActionPreference = "Stop"

$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    $adb = "adb"
}

$package = "com.indrajeet.appblocker"
$component = "com.indrajeet.appblocker/com.indrajeet.appblocker.admin.BlockDeviceAdminReceiver"
$releaseAction = "com.indrajeet.appblocker.action.RELEASE_FOR_UNINSTALL"
$releaseReceiver = "com.indrajeet.appblocker/.admin.LaptopReleaseReceiver"
$releaseToken = $env:APPBLOCKER_RELEASE_TOKEN
if ([string]::IsNullOrWhiteSpace($releaseToken)) {
    $releaseToken = "CHANGE_ME_APPBLOCKER_RELEASE_TOKEN"
    Write-Host "APPBLOCKER_RELEASE_TOKEN not set. Using default token value."
}

try {
    & $adb shell am broadcast `
        -a $releaseAction `
        -n $releaseReceiver `
        --es token $releaseToken | Out-Host
    Start-Sleep -Seconds 2
} catch {
    Write-Host "Release broadcast step did not complete cleanly."
}

try {
    & $adb shell dpm remove-active-admin --user 0 $component | Out-Host
} catch {
    Write-Host "remove-active-admin did not complete cleanly; continuing to uninstall attempt."
}

$uninstallArgs = @()
if ($KeepBuckets) {
    Write-Host "Keeping app data (including blocked buckets/config) on device."
    # Some Android builds require explicit shell package command for -k.
    $uninstallArgs = @("shell", "cmd", "package", "uninstall", "-k", "--user", "0", $package)
} else {
    Write-Host "Removing app data and blocked buckets/config from device."
    $uninstallArgs = @("uninstall", $package)
}

$uninstallOutput = & $adb @uninstallArgs 2>&1
$uninstallText = ($uninstallOutput | Out-String)
Write-Host $uninstallText.Trim()

if ($uninstallText -notmatch "(?im)^Success\b") {
    throw "Uninstall failed. Ensure latest APK is installed, APPBLOCKER_RELEASE_TOKEN matches the app build, and USB debugging is authorized."
}
