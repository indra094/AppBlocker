param(
    [switch]$BootedSimulator,
    [string]$SimulatorName,
    [string]$DeviceId,
    [string]$BundleIdentifier = "com.indrajeet.appblocker.ios",
    [string]$DerivedDataPath = "ios/build/DerivedData",
    [switch]$SkipBuild,
    [switch]$AllowProvisioningUpdates
)

$ErrorActionPreference = "Stop"

if (-not [System.Runtime.InteropServices.RuntimeInformation]::IsOSPlatform([System.Runtime.InteropServices.OSPlatform]::OSX)) {
    throw "scripts/ios-install.ps1 must run on macOS with PowerShell 7+ and Xcode installed."
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$buildScript = Join-Path $PSScriptRoot "ios-build-debug.ps1"

$destination = if ($DeviceId) {
    "id=$DeviceId"
} elseif ($SimulatorName) {
    "platform=iOS Simulator,name=$SimulatorName"
} else {
    "generic/platform=iOS Simulator"
}

if (-not $SkipBuild) {
    $buildParams = @{
        DerivedDataPath = $DerivedDataPath
        Destination = $destination
    }
    if ($AllowProvisioningUpdates) {
        $buildParams.AllowProvisioningUpdates = $true
    }
    & $buildScript @buildParams | Out-Host
}

$resolvedDerivedDataPath = Join-Path $repoRoot $DerivedDataPath
$appBundle = Get-ChildItem -Path $resolvedDerivedDataPath -Recurse -Directory -Filter "AppBlocker.app" |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if (-not $appBundle) {
    throw "No AppBlocker.app bundle found under $resolvedDerivedDataPath. Run scripts/ios-build-debug.ps1 first."
}

if ($DeviceId) {
    & xcrun devicectl device install app --device $DeviceId $appBundle.FullName | Out-Host
    & xcrun devicectl device process launch --device $DeviceId $BundleIdentifier | Out-Host
    Write-Host "Installed and launched AppBlocker on iOS device $DeviceId."
    return
}

if ($SimulatorName) {
    & xcrun simctl boot $SimulatorName 2>$null | Out-Host
}

& xcrun simctl install booted $appBundle.FullName | Out-Host
& xcrun simctl launch booted $BundleIdentifier | Out-Host

Write-Host "Installed and launched AppBlocker on the booted iOS Simulator."
