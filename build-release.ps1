$ErrorActionPreference = 'Stop'

# Needs JAVA_HOME (JDK 17) and ANDROID_HOME set

if (-not (Test-Path keystore.properties)) {
    throw 'keystore.properties is missing. See "Release builds" in README.md.'
}

.\gradlew.bat assembleRelease --console=plain
if ($LASTEXITCODE) { exit $LASTEXITCODE }

# Check it's signed with the release key, not left unsigned
$apksigner = Get-ChildItem "$env:ANDROID_HOME\build-tools\*\apksigner.bat" | Sort-Object FullName | Select-Object -Last 1
& $apksigner.FullName verify --print-certs app\build\outputs\apk\release\app-release.apk
if ($LASTEXITCODE) { exit $LASTEXITCODE }

$version = (Select-String -Path app\build.gradle -Pattern 'versionName "(.+)"').Matches[0].Groups[1].Value
New-Item -ItemType Directory -Force dist | Out-Null
$out = "dist\RainLock-$version.apk"
Copy-Item app\build\outputs\apk\release\app-release.apk $out -Force
Write-Host "Built $((Resolve-Path $out).Path)"
