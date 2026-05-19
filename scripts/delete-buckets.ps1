param(
    [long[]]$BucketId,
    [string[]]$BucketName
)

$ErrorActionPreference = "Stop"

if ((-not $BucketId -or $BucketId.Count -eq 0) -and (-not $BucketName -or $BucketName.Count -eq 0)) {
    throw "Provide -BucketId and/or -BucketName. Example: .\scripts\delete-buckets.ps1 -BucketId 3,5 or .\scripts\delete-buckets.ps1 -BucketName Social,Games"
}

$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    $adb = "adb"
}

$releaseAction = "com.indrajeet.appblocker.action.DELETE_BUCKETS"
$releaseReceiver = "com.indrajeet.appblocker/.admin.LaptopReleaseReceiver"
$releaseToken = $env:APPBLOCKER_RELEASE_TOKEN
$delimiter = "|||"
if ([string]::IsNullOrWhiteSpace($releaseToken)) {
    $releaseToken = "CHANGE_ME_APPBLOCKER_RELEASE_TOKEN"
    Write-Host "APPBLOCKER_RELEASE_TOKEN not set. Using default token value."
}

$broadcastArgs = @(
    "shell", "am", "broadcast",
    "-a", $releaseAction,
    "-n", $releaseReceiver,
    "--es", "token", $releaseToken
)

if ($BucketId -and $BucketId.Count -gt 0) {
    $bucketIdsValue = (($BucketId | Where-Object { $_ -gt 0 } | Select-Object -Unique) -join $delimiter)
    if (-not [string]::IsNullOrWhiteSpace($bucketIdsValue)) {
        $broadcastArgs += @("--es", "bucket_ids", $bucketIdsValue)
    }
}

if ($BucketName -and $BucketName.Count -gt 0) {
    $bucketNamesValue = (($BucketName | ForEach-Object { $_.Trim() } | Where-Object { $_ } | Select-Object -Unique) -join $delimiter)
    if (-not [string]::IsNullOrWhiteSpace($bucketNamesValue)) {
        $broadcastArgs += @("--es", "bucket_names", $bucketNamesValue)
    }
}

$output = & $adb @broadcastArgs 2>&1
$text = ($output | Out-String).Trim()
Write-Host $text

if ($text -match "result=(\d+)") {
    $resultCode = [int]$Matches[1]
    if ($resultCode -ne 0) {
        throw "Bucket deletion failed. See broadcast output above."
    }
}
