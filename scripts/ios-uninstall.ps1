param(
    [string]$DeviceId,
    [string]$BundleIdentifier = "com.indrajeet.appblocker.ios"
)

$ErrorActionPreference = "Stop"

if (-not [System.Runtime.InteropServices.RuntimeInformation]::IsOSPlatform([System.Runtime.InteropServices.OSPlatform]::OSX)) {
    throw "scripts/ios-uninstall.ps1 must run on macOS with PowerShell 7+ and Xcode installed."
}

if ($DeviceId) {
    & xcrun devicectl device uninstall app --device $DeviceId $BundleIdentifier | Out-Host
    Write-Host "Uninstalled AppBlocker from iOS device $DeviceId."
    return
}

& xcrun simctl uninstall booted $BundleIdentifier | Out-Host
Write-Host "Uninstalled AppBlocker from the booted iOS Simulator."
