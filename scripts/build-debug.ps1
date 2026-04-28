$ErrorActionPreference = "Stop"

Set-Location $PSScriptRoot\..
.\gradlew.bat --no-daemon assembleDebug
