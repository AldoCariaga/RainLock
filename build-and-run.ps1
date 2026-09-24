$ErrorActionPreference = 'Stop'

# Needs JAVA_HOME (JDK 17) and ANDROID_HOME set, and a phone on USB with USB debugging on

$pkg = 'com.aldo.rainlock'
$adb = if ($env:ANDROID_HOME) { Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe' } else { 'adb' }

# Release-signed once the release key is set up, so updates install over the release build
$variant = if (Test-Path keystore.properties) { 'release' } else { 'debug' }
.\gradlew.bat "assemble$variant" --console=plain
if ($LASTEXITCODE) { exit $LASTEXITCODE }
& $adb install -r "app\build\outputs\apk\$variant\app-$variant.apk"
# Lets the app switch its accessibility service and Circle to Search while locked. Survives updates; lost on uninstall.
& $adb shell pm grant $pkg android.permission.WRITE_SECURE_SETTINGS
& $adb shell am start -n "$pkg/.MainActivity"
