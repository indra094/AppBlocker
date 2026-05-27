param(
    [string]$Destination = "generic/platform=iOS Simulator",
    [string]$DerivedDataPath = "ios/build/DerivedData",
    [switch]$SkipGenerate,
    [switch]$AllowProvisioningUpdates
)

$ErrorActionPreference = "Stop"

if (-not [System.Runtime.InteropServices.RuntimeInformation]::IsOSPlatform([System.Runtime.InteropServices.OSPlatform]::OSX)) {
    throw "scripts/ios-build-debug.ps1 must run on macOS with PowerShell 7+, Xcode, and XcodeGen installed."
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$iosRoot = Join-Path $repoRoot "ios"
$resolvedDerivedDataPath = Join-Path $repoRoot $DerivedDataPath

if (-not (Get-Command xcodebuild -ErrorAction SilentlyContinue)) {
    throw "xcodebuild not found. Install Xcode and make it the active developer directory."
}

if (-not (Get-Command xcodegen -ErrorAction SilentlyContinue)) {
    throw "xcodegen not found. Install it first, for example: brew install xcodegen"
}

Push-Location $iosRoot
try {
    if (-not $SkipGenerate) {
        & xcodegen generate | Out-Host
    }

    $arguments = @(
        "-project", "AppBlocker.xcodeproj",
        "-scheme", "AppBlocker",
        "-configuration", "Debug",
        "-destination", $Destination,
        "-derivedDataPath", $resolvedDerivedDataPath,
        "build"
    )

    if ($AllowProvisioningUpdates) {
        $arguments = @("-allowProvisioningUpdates") + $arguments
    }

    & xcodebuild @arguments | Out-Host
} finally {
    Pop-Location
}

$appBundle = Get-ChildItem -Path $resolvedDerivedDataPath -Recurse -Directory -Filter "AppBlocker.app" |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if (-not $appBundle) {
    throw "Build completed without a discoverable AppBlocker.app bundle under $resolvedDerivedDataPath"
}

Write-Host "Built iOS app bundle:"
Write-Host $appBundle.FullName
