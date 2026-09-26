# Copyright 2026 PollNull

$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$sdkRoot = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adbPath = Join-Path $sdkRoot 'platform-tools\adb.exe'
$apkPath = Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'

if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $jdkRoot = Join-Path $env:LOCALAPPDATA 'Programs\Temurin17'
    $jdk = Get-ChildItem -LiteralPath $jdkRoot -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -First 1
    if (-not $jdk) { throw 'JDK 17 no encontrado. Define JAVA_HOME o instala Eclipse Temurin 17.' }
    $env:JAVA_HOME = $jdk.FullName
}

if (-not (Test-Path (Join-Path $sdkRoot 'platforms\android-35\android.jar'))) {
    throw "Falta Android SDK Platform 35 en $sdkRoot. Instálala desde Android Studio > Tools > SDK Manager."
}
if (-not (Test-Path (Join-Path $sdkRoot 'build-tools\35.0.0\aapt.exe'))) {
    throw "Faltan Android SDK Build Tools 35.0.0 en $sdkRoot. Instálalas desde Android Studio > Tools > SDK Manager."
}
if (-not (Test-Path (Join-Path $sdkRoot 'ndk\27.2.12479018\source.properties'))) {
    throw "Falta Android NDK 27.2.12479018 en $sdkRoot. Instálalo desde Android Studio > Tools > SDK Manager."
}
if (-not (Test-Path (Join-Path $sdkRoot 'cmake\3.22.1\bin\cmake.exe'))) {
    throw "Falta CMake 3.22.1 en $sdkRoot. Instálalo desde Android Studio > Tools > SDK Manager."
}
if (-not (Test-Path $adbPath)) {
    $adbPath = Join-Path $projectRoot 'platform-tools-adb\adb.exe'
}
if (-not (Test-Path $adbPath)) {
    throw "No se encontró ADB. Instala Android SDK Platform-Tools o define ANDROID_SDK_ROOT."
}

$env:ANDROID_SDK_ROOT = $sdkRoot
$env:ANDROID_HOME = $sdkRoot
Push-Location $projectRoot
try {
    & (Join-Path $projectRoot 'gradlew.bat') --no-daemon assembleDebug
    if ($LASTEXITCODE -ne 0) { throw "Gradle terminó con código $LASTEXITCODE." }
    if (-not (Test-Path $apkPath)) { throw "No se generó el APK esperado: $apkPath" }
    & $adbPath install -r $apkPath
    if ($LASTEXITCODE -ne 0) { throw "ADB terminó con código $LASTEXITCODE." }
    Write-Host "Instalada correctamente: $apkPath"
}
finally {
    Pop-Location
}
