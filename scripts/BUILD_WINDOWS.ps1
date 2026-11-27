$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $root
try {
    [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12
    $javaHome=$env:JAVA_HOME
    if (-not $javaHome -and (Test-Path 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe')) {
        $javaHome='C:\Program Files\Android\Android Studio\jbr'
        $env:JAVA_HOME=$javaHome
    }
    if (-not (Get-Command java -ErrorAction SilentlyContinue) -and -not $javaHome) {
        throw 'Java 17 ausente: instale Android Studio (inclui JBR Java) ou JDK 17.'
    }
    if ($javaHome) { $env:PATH=(Join-Path $javaHome 'bin')+';'+$env:PATH }
    $sdk=$env:ANDROID_HOME
    if (-not $sdk) {$sdk=$env:ANDROID_SDK_ROOT}
    if (-not $sdk) {$sdk=Join-Path $env:LOCALAPPDATA 'Android\Sdk'}
    if (-not (Test-Path (Join-Path $sdk 'platforms\android-35\android.jar'))) {
        $preparedSdk=Join-Path $env:LOCALAPPDATA 'Mali2AndroidTools\sdk'
        if (Test-Path (Join-Path $preparedSdk 'platforms\android-35\android.jar')) { $sdk=$preparedSdk }
    }
    if (-not (Test-Path (Join-Path $sdk 'platforms\android-35\android.jar'))) {
        throw "Android SDK API 35 nao encontrado em $sdk. Abra Android Studio > SDK Manager e instale Android SDK Platform 35 e Build Tools 35."
    }
    $env:ANDROID_HOME=$sdk
    $env:ANDROID_SDK_ROOT=$sdk
    & (Join-Path $root 'scripts\preparar_sdk_jieli.ps1') -ProjectRoot $root
    if ($LASTEXITCODE -and $LASTEXITCODE -ne 0) { throw 'Falha baixando SDK oficial JieLi' }
    $gradle=(Get-Command gradle -ErrorAction SilentlyContinue)
    if($gradle) {$gradleExe=$gradle.Source}
    else {
        $cache=Join-Path $root '.toolcache'
        $gradleDir=Join-Path $cache 'gradle-8.9'
        $gradleExe=Join-Path $gradleDir 'bin\gradle.bat'
        $preparedGradle=Join-Path $env:LOCALAPPDATA 'Mali2AndroidTools\gradle-8.9\bin\gradle.bat'
        if (Test-Path $preparedGradle) { $gradleExe=$preparedGradle }
        if (-not (Test-Path $gradleExe)) {
            New-Item -ItemType Directory -Force -Path $cache | Out-Null
            $zip=Join-Path $cache 'gradle-8.9-bin.zip'
            if(-not (Test-Path $zip)) {
                Write-Host '[BUILD] Baixando Gradle oficial 8.9 (uma vez)...'
                Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-8.9-bin.zip' -UseBasicParsing -OutFile $zip -TimeoutSec 600
            }
            Expand-Archive -LiteralPath $zip -DestinationPath $cache -Force
        }
    }
    Write-Host ('[BUILD] Android SDK: '+$sdk)
    Write-Host '[BUILD] Compilando APK DEBUG instalavel...'
    & $gradleExe --no-daemon --stacktrace :app:assembleDebug
    if ($LASTEXITCODE -ne 0) { throw "Gradle falhou com codigo $LASTEXITCODE; consulte o erro acima" }
    $built=Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
    if (-not (Test-Path $built)) {throw 'Gradle concluiu, mas APK nao apareceu no caminho previsto'}
    $out=Join-Path $root 'Mali2_Inspector_Android_v1_1_5_debug.apk'
    Copy-Item -LiteralPath $built -Destination $out -Force
    $hash=(Get-FileHash -LiteralPath $out -Algorithm SHA256).Hash
    Write-Host "[SUCESSO] APK compilado em: $out" -ForegroundColor Green
    Write-Host "[SHA256] $hash"
    Write-Host 'Instale pelo cabo com: adb install -r "Mali2_Inspector_Android_v1_1_5_debug.apk"'
} finally { Pop-Location }
