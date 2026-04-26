$ErrorActionPreference = "Stop"

Set-Location $PSScriptRoot\..
.\gradlew.bat assembleDebug
