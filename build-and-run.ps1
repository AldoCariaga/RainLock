$ErrorActionPreference = 'Stop'

# Needs JAVA_HOME (JDK 17) and ANDROID_HOME set, and a phone on USB with USB debugging on

$pkg = 'com.aldo.rainlock'
$adb = if ($env:ANDROID_HOME) { Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe' } else { 'adb' }

.\gradlew.bat assembleDebug --console=plain
& $adb install -r app\build\outputs\apk\debug\app-debug.apk
# Lets the app switch Circle to Search off while locked. Survives updates; lost on uninstall.
& $adb shell pm grant $pkg android.permission.WRITE_SECURE_SETTINGS
& $adb shell am start -n "$pkg/.MainActivity"
