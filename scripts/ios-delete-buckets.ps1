param(
    [Parameter(Mandatory = $true)]
    [string]$FilePath,
    [Guid[]]$BucketId,
    [string[]]$BucketName
)

$ErrorActionPreference = "Stop"

if ((-not $BucketId -or $BucketId.Count -eq 0) -and (-not $BucketName -or $BucketName.Count -eq 0)) {
    throw "Provide -BucketId and/or -BucketName. Example: .\scripts\ios-delete-buckets.ps1 -FilePath .\AppBlocker-buckets.json -BucketName Social,Games"
}

if (-not (Test-Path -LiteralPath $FilePath)) {
    throw "Rule file not found: $FilePath"
}

$raw = Get-Content -LiteralPath $FilePath -Raw
$buckets = if ([string]::IsNullOrWhiteSpace($raw)) {
    @()
} else {
    @(ConvertFrom-Json -InputObject $raw)
}

$targetIds = @{}
foreach ($id in (@($BucketId) | Select-Object -Unique)) {
    if ($null -ne $id) {
        $targetIds[$id.ToString().ToLowerInvariant()] = $true
    }
}

$targetNames = @{}
foreach ($name in @($BucketName)) {
    if (-not [string]::IsNullOrWhiteSpace($name)) {
        $targetNames[$name.Trim()] = $true
    }
}

$remainingBuckets = New-Object System.Collections.Generic.List[object]
$deletedBuckets = New-Object System.Collections.Generic.List[string]

foreach ($bucket in $buckets) {
    $currentBucketId = [string]$bucket.id
    $currentBucketName = [string]$bucket.name
    $matchesId = $currentBucketId -and $targetIds.ContainsKey($currentBucketId.ToLowerInvariant())
    $matchesName = $currentBucketName -and $targetNames.ContainsKey($currentBucketName)

    if ($matchesId -or $matchesName) {
        $deletedBuckets.Add("$currentBucketName ($currentBucketId)") | Out-Null
        continue
    }

    $remainingBuckets.Add($bucket) | Out-Null
}

ConvertTo-Json -InputObject @($remainingBuckets.ToArray()) -Depth 100 |
    Set-Content -LiteralPath $FilePath -Encoding utf8

if ($deletedBuckets.Count -eq 0) {
    Write-Host "Deleted 0 buckets."
} else {
    Write-Host "Deleted $($deletedBuckets.Count) bucket(s): $($deletedBuckets -join ', ')"
}
