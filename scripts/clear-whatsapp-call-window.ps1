param()

$ErrorActionPreference = "Stop"

$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    $adb = "adb"
}

$action = "com.indrajeet.appblocker.action.CLEAR_WHATSAPP_CALL_WINDOW"
$receiver = "com.indrajeet.appblocker/.admin.LaptopReleaseReceiver"

$releaseToken = $env:APPBLOCKER_RELEASE_TOKEN
if ([string]::IsNullOrWhiteSpace($releaseToken)) {
    $releaseToken = "CHANGE_ME_APPBLOCKER_RELEASE_TOKEN"
    Write-Host "APPBLOCKER_RELEASE_TOKEN not set. Using default token value."
}

$broadcastArgs = @(
    "shell", "am", "broadcast",
    "-a", $action,
    "-n", $receiver,
    "--es", "token", $releaseToken
)

$output = & $adb @broadcastArgs 2>&1
$text = ($output | Out-String).Trim()

Write-Host $text

if ($text -match "result=(\d+)") {
    $resultCode = [int]$Matches[1]
    if ($resultCode -ne 0) {
        throw "Failed to clear WhatsApp call window configuration. See broadcast output above."
    }
}

Write-Host "WhatsApp call window configuration cleared successfully."