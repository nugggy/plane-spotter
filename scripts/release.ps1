<#
Publishes a new version of Plane Spotter to the update channel (GitHub Releases).
Every copy of the app checks the latest release when it opens and offers the update.

Usage (from the project folder):
  .\scripts\release.ps1 -Version 1.1.0 -Notes "Added 10 new aircraft"

Needs: keystore.properties and keystore\plane-spotter-release.jks (the same key every time),
the GitHub CLI logged in as nugggy, and Node.js.
#>
param(
    [Parameter(Mandatory = $true)][string]$Version,
    [Parameter(Mandatory = $true)][string]$Notes
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not (Test-Path 'keystore.properties')) { throw 'keystore.properties is missing. Releases must be signed with the original key.' }
if ($Version -notmatch '^\d+\.\d+\.\d+$') { throw 'Version must look like 1.2.0' }

# Bump the version in gradle.properties.
$props = Get-Content gradle.properties
$code = [int](($props | Where-Object { $_ -match '^APP_VERSION_CODE=' }) -replace 'APP_VERSION_CODE=', '') + 1
$props = $props -replace '^APP_VERSION_CODE=.*', "APP_VERSION_CODE=$code" -replace '^APP_VERSION_NAME=.*', "APP_VERSION_NAME=$Version"
Set-Content gradle.properties $props -Encoding utf8

# Rebuild data and the signed APK.
node data/build_data.js
if ($LASTEXITCODE -ne 0) { throw 'Data build reported problems.' }
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat assembleRelease --console=plain -q
if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed.' }

New-Item -ItemType Directory -Force release | Out-Null
$apk = "release\PlaneSpotter-v$Version.apk"
Copy-Item app\build\outputs\apk\release\app-release.apk $apk -Force

git add -A
git commit -m "Release v$Version"
git tag "v$Version"
git push origin HEAD --tags

gh release create "v$Version" $apk --title "Plane Spotter v$Version" --notes $Notes --latest
Write-Host "Released v$Version (version code $code). Phones will offer it next time the app opens."
