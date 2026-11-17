<#
.SYNOPSIS
    Executa as suites de seguranca, regressao, integracao Java/Python e instalacao
    limpa do ZIP gerado. Suporta modo 'full-local' (GiteaRunner com amostra real)
    e modo 'cloud' (GitHub Actions sem fixture privada externa).
#>
param(
    [Parameter(Mandatory = $true)][string]$RepoRoot,
    [ValidateSet('cloud', 'full-local')][string]$TestMode = 'cloud',
    [Parameter(Mandatory = $true)][string]$ZipPath,
    [Parameter(Mandatory = $true)][string]$VerifierJarPath,
    [Parameter(Mandatory = $true)][string]$VerifierExePath,
    [Parameter(Mandatory = $true)][string]$PluginJarPath,
    [Parameter(Mandatory = $true)][string]$ExpectedPluginSha256,
    [Parameter(Mandatory = $true)][string]$ExpectedVerifierJarSha256,
    [Parameter(Mandatory = $true)][string]$ExpectedVerifierExeSha256,
    [string]$Java8Home = '',
    [string]$Java11Home = '',
    [string]$Java21Home = '',
    [Parameter(Mandatory = $true)][string]$IpedHome,
    [Parameter(Mandatory = $true)][string]$JavaFxLib
)

$ErrorActionPreference = 'Stop'

. (Join-Path $RepoRoot 'tests\resolve_test_env.ps1')

if ([string]::IsNullOrWhiteSpace($Java8Home)) {
    $Java8Home = Resolve-JdkHome -EnvNames @('JAVA8_HOME', 'JAVA_HOME') -MajorVersions @(8) -RequiredTool 'bin\javac.exe'
}
if ([string]::IsNullOrWhiteSpace($Java11Home)) {
    $Java11Home = Resolve-JdkHome -EnvNames @('JAVA11_HOME', 'JAVA_HOME') -MajorVersions @(11, 21) -RequiredTool 'bin\jar.exe'
}
if ([string]::IsNullOrWhiteSpace($Java21Home)) {
    $Java21Home = Resolve-JdkHome -Explicit $(if (Test-Path -LiteralPath 'C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot\bin\javac.exe') { 'C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot' } else { '' }) -EnvNames @('JAVA21_HOME', 'JAVA_HOME') -MajorVersions @(21) -RequiredTool 'bin\javac.exe'
}

Write-Host "=== Suites de Validacao ($TestMode) ==="
Write-Host "Java8=$Java8Home | Java21=$Java21Home | JavaFX=$JavaFxLib"

# 1. Validacao do ZIP extraido e binarios internos
$zipStageTest = Join-Path ([IO.Path]::GetTempPath()) ('ci-zip-chk-' + [Guid]::NewGuid().ToString('N'))
try {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [IO.Compression.ZipFile]::ExtractToDirectory($ZipPath, $zipStageTest)
    $instFolder = Join-Path $zipStageTest 'instalar_captura_chat_zap'
    foreach ($req in @(
        (Join-Path $zipStageTest 'LEIA-ME.txt'),
        (Join-Path $instFolder 'instalar_captura_chat_zap.bat'),
        (Join-Path $instFolder 'install-localization.ps1'),
        (Join-Path $instFolder 'validate-plugin-jar.ps1'),
        (Join-Path $instFolder 'iped-viewers-impl-4.3.1.jar'),
        (Join-Path $instFolder 'integridade-verifier.jar'),
        (Join-Path $instFolder 'integridade-verifier.exe'),
        (Join-Path $instFolder 'opencv_java4120.dll'),
        (Join-Path $instFolder 'dependencies\json-20260719.jar'),
        (Join-Path $instFolder 'dependencies\opencv-4120.jar')
    )) {
        if (!(Test-Path -LiteralPath $req -PathType Leaf)) { throw "Conteudo ausente no ZIP: $req" }
    }
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $instFolder 'validate-plugin-jar.ps1') -Jar (Join-Path $instFolder 'iped-viewers-impl-4.3.1.jar') -ExpectedSha256 $ExpectedPluginSha256
    if ($LASTEXITCODE -ne 0) { throw 'validate-plugin-jar no ZIP falhou' }
    $exeProc = Start-Process -FilePath (Join-Path $instFolder 'integridade-verifier.exe') -ArgumentList '--self-test' -Wait -PassThru -NoNewWindow
    if ($exeProc.ExitCode -ne 0) { throw "EXE extraido do ZIP falhou no --self-test ($($exeProc.ExitCode))" }
} finally {
    Remove-Item -LiteralPath $zipStageTest -Recurse -Force -ErrorAction SilentlyContinue
}

$env:IPED_HOME = $IpedHome
$env:JAVAFX_LIB = $JavaFxLib
$env:JAVA26_HOME = $Java21Home
$env:JAVA21_HOME = $Java21Home
$env:JAVA_HOME = $Java21Home
$env:JAVA8_HOME = $Java8Home
$env:JAVA11_HOME = $Java11Home
$env:TEST_VERIFIER_EXE = $VerifierExePath
$env:TEST_VERIFIER_JAR = $VerifierJarPath
$env:TEST_PLUGIN_JAR = $PluginJarPath

Push-Location $RepoRoot
try {
    Remove-Item Env:\PYTHONUTF8 -ErrorAction SilentlyContinue
    if ($PSVersionTable.PSEdition -eq 'Core') {
        $winMod = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\Modules'
        $psMods = @($env:PSModulePath -split ';' | Where-Object { $_ -and ($_ -notmatch '\\powershell\\7') -and ($_ -notmatch '\\PowerShell\\Modules') })
        if ($psMods -notcontains $winMod) { $psMods = @($winMod) + $psMods }
        $env:PSModulePath = $psMods -join ';'
    }
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $RepoRoot 'verificador-integridade\prototype\test-security-spec.ps1')
    if ($LASTEXITCODE -ne 0) { throw "test-security-spec.ps1 falhou ($LASTEXITCODE)" }

    if ($TestMode -eq 'full-local') {
        $env:CHAT_CAPTURE_SAMPLE_HTML = 'C:\Avilla-Forensics-Free\IPED-4.3.1_and_java_plugins\IPED_testes_chat_real\Exportados\arquivos\F\A\FAF3132301A706022E1C3EAD6AE0800B.html'
        $env:CHAT_CAPTURE_SAMPLE_ROOT = 'C:\Avilla-Forensics-Free\IPED-4.3.1_and_java_plugins\IPED_testes_chat_real\teste_chat_extraido'
        $env:IPED_JJS = Join-Path $IpedHome 'jre\bin\jjs.exe'

        & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $RepoRoot 'teste_antes_de_aplicar\run_all_tests.ps1') `
            -Java8Home $Java8Home -Java11Home $Java11Home -Java26Home $Java21Home -IpedHome $IpedHome -JavaFxLib $JavaFxLib
        if ($LASTEXITCODE -ne 0) { throw "run_all_tests.ps1 falhou ($LASTEXITCODE)" }
    } else {
        $javac8 = Join-Path $Java8Home 'bin\javac.exe'
        $java8 = Join-Path $Java8Home 'bin\java.exe'
        $javac21 = Join-Path $Java21Home 'bin\javac.exe'
        $java21 = Join-Path $Java21Home 'bin\java.exe'

        $jdk8Classes = Join-Path $RepoRoot 'tmp\ci-verifier-tests'
        Remove-Item -LiteralPath $jdk8Classes -Recurse -Force -ErrorAction SilentlyContinue
        New-Item -ItemType Directory -Force -Path $jdk8Classes | Out-Null
        $jdk8Deps = Join-Path $RepoRoot 'verificador-integridade\dependencies\*'
        & $javac8 -encoding UTF-8 -cp $jdk8Deps -d $jdk8Classes `
            verificador-integridade\src\IntegrityVerifier.java `
            verificador-integridade\tests\SecurityRegressionTest.java `
            verificador-integridade\tests\IndexHashParityTest.java
        if ($LASTEXITCODE -ne 0) { throw 'javac SecurityRegressionTest falhou' }
        & $java8 -cp "$jdk8Classes;$jdk8Deps" SecurityRegressionTest
        if ($LASTEXITCODE -ne 0) { throw 'SecurityRegressionTest falhou' }
        & $java8 -cp "$jdk8Classes;$jdk8Deps" IndexHashParityTest
        if ($LASTEXITCODE -ne 0) { throw 'IndexHashParityTest falhou' }

        $jsonJar = Join-Path $RepoRoot 'instalar_captura_chat_zap\dependencies\json-20260719.jar'
        $ptClasses = Join-Path $RepoRoot 'tmp\plugin-integrity-tests'
        Remove-Item -LiteralPath $ptClasses -Recurse -Force -ErrorAction SilentlyContinue
        New-Item -ItemType Directory -Force -Path $ptClasses | Out-Null
        & $javac21 -encoding UTF-8 -cp "$PluginJarPath;$jsonJar" -d $ptClasses `
            src\iped\viewers\CaptureIntegrity.java `
            src\iped\viewers\CaptureCensorship.java `
            src\iped\viewers\CaptureBoundaryResolver.java `
            src\iped\viewers\VerifierIntegrity.java `
            tests\iped\viewers\CaptureBoundaryResolverTest.java `
            tests\iped\viewers\CaptureCensorshipTest.java `
            tests\iped\viewers\VerifierIntegrityHashTest.java `
            tests\iped\viewers\MigrationIntegrityHistoryHarness.java
        if ($LASTEXITCODE -ne 0) { throw 'javac plugin-integrity-tests falhou' }
        $ptCp = "$ptClasses;$PluginJarPath;$jsonJar"
        foreach ($cls in @('iped.viewers.CaptureCensorshipTest', 'iped.viewers.CaptureBoundaryResolverTest', 'iped.viewers.VerifierIntegrityHashTest')) {
            & $java21 -cp $ptCp $cls
            if ($LASTEXITCODE -ne 0) { throw "Falhou: $cls" }
        }

        $vhClasses = Join-Path $RepoRoot 'tmp\verifier-html-v1'
        Remove-Item -LiteralPath $vhClasses -Recurse -Force -ErrorAction SilentlyContinue
        New-Item -ItemType Directory -Force -Path $vhClasses | Out-Null
        & $javac8 -source 1.8 -target 1.8 -encoding UTF-8 -cp $VerifierJarPath -d $vhClasses `
            verificador-integridade\src\IntegrityVerifier.java `
            teste_antes_de_aplicar\PublicManifestComparisonHarness.java
        if ($LASTEXITCODE -ne 0) { throw 'javac PublicManifestComparisonHarness falhou' }

        $p2Classes = Join-Path $RepoRoot 'tmp\phase2-plugin-tests'
        Remove-Item -LiteralPath $p2Classes -Recurse -Force -ErrorAction SilentlyContinue
        New-Item -ItemType Directory -Force -Path $p2Classes | Out-Null
        $p2CompileCp = "$PluginJarPath;$jsonJar;$IpedHome\lib\*"
        $p2Sources = @(Get-ChildItem 'src\iped\viewers' -Filter '*.java' | ForEach-Object { $_.FullName })
        & $javac21 -encoding UTF-8 -source 11 -target 11 -Xlint:-options `
            --module-path $JavaFxLib --add-modules javafx.controls,javafx.web,javafx.swing `
            -cp $p2CompileCp -d $p2Classes $p2Sources `
            tests\iped\viewers\ChatAnnotationStoreRoundTripTest.java `
            tests\iped\viewers\SafeNameTest.java `
            tests\iped\viewers\PathContainmentTest.java `
            tests\iped\viewers\CsvSanitizationTest.java `
            tests\iped\viewers\SwingHtmlDisableTest.java `
            tests\iped\viewers\ChatAnnotationActionDialogTest.java `
            tests\iped\viewers\JsonParsingRegressionTest.java `
            tests\iped\viewers\UiAndAuxFolderRegressionTest.java `
            tests\iped\viewers\OpenCvLoaderTest.java `
            tests\iped\viewers\CaptureIntegrityTest.java `
            tests\iped\viewers\OriginCustodyBinderTest.java `
            tests\iped\viewers\OriginAuditEndToEndTest.java
        if ($LASTEXITCODE -ne 0) { throw 'javac phase2-plugin-tests falhou' }
        $p2RtCp = "$p2Classes;$p2CompileCp"
        foreach ($cls in @(
            'iped.viewers.ChatAnnotationStoreRoundTripTest',
            'iped.viewers.SafeNameTest',
            'iped.viewers.PathContainmentTest',
            'iped.viewers.CsvSanitizationTest',
            'iped.viewers.SwingHtmlDisableTest',
            'iped.viewers.ChatAnnotationActionDialogTest',
            'iped.viewers.JsonParsingRegressionTest',
            'iped.viewers.UiAndAuxFolderRegressionTest',
            'iped.viewers.CaptureIntegrityTest',
            'iped.viewers.OriginCustodyBinderTest'
        )) {
            & $java21 '-Dprism.order=sw' --module-path $JavaFxLib --add-modules javafx.controls,javafx.web,javafx.swing -cp $p2RtCp $cls
            if ($LASTEXITCODE -ne 0) { throw "Falhou: $cls" }
        }
        & $java21 '-Dprism.order=sw' --module-path $JavaFxLib --add-modules javafx.controls,javafx.web,javafx.swing -cp "$vhClasses;$p2RtCp" iped.viewers.OriginAuditEndToEndTest
        if ($LASTEXITCODE -ne 0) { throw 'OriginAuditEndToEndTest falhou' }
        & $java21 '-Dprism.order=sw' --module-path $JavaFxLib --add-modules javafx.controls,javafx.web,javafx.swing -cp $p2RtCp iped.viewers.OpenCvLoaderTest (Join-Path $RepoRoot 'instalar_captura_chat_zap\opencv_java4120.dll')
        if ($LASTEXITCODE -ne 0) { throw 'OpenCvLoaderTest falhou' }

        & python -m unittest teste_antes_de_aplicar.legacy_migration.test_legacy_migration_spec -v
        if ($LASTEXITCODE -ne 0) { throw 'test_legacy_migration_spec falhou' }
        & python -m unittest teste_antes_de_aplicar.legacy_migration.test_legacy_migration_production_contract -v
        if ($LASTEXITCODE -ne 0) { throw 'test_legacy_migration_production_contract falhou' }
        & python teste_antes_de_aplicar\test_html_censorship_contract.py
        if ($LASTEXITCODE -ne 0) { throw 'test_html_censorship_contract falhou' }
        & python teste_antes_de_aplicar\test_verifier_java_integration.py -v
        if ($LASTEXITCODE -ne 0) { throw 'test_verifier_java_integration falhou' }
        & python teste_antes_de_aplicar\test_fase4_spec_validation.py -v
        if ($LASTEXITCODE -ne 0) { throw 'test_fase4_spec_validation falhou' }
    }

    foreach ($reg in @(
        'tests\run_capture_header_mime_regression.ps1',
        'tests\run_svg_sanitizer_compatibility.ps1'
    )) {
        & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $RepoRoot $reg) -IpedRoot $IpedHome -JavaFxLib $JavaFxLib -JdkHome $Java21Home
        if ($LASTEXITCODE -ne 0) { throw "$reg falhou ($LASTEXITCODE)" }
    }
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $RepoRoot 'tests\run_verifier_embedded_image_compatibility.ps1')
    if ($LASTEXITCODE -ne 0) { throw "run_verifier_embedded_image_compatibility.ps1 falhou ($LASTEXITCODE)" }

    # Teste ponta a ponta do instalador ZIP gerado contra copia limpa simulada do IPED 4.3.1
    $e2eBase = Join-Path ([IO.Path]::GetTempPath()) ('ci-e2e-zip-' + [Guid]::NewGuid().ToString('N'))
    $e2eRoot = Join-Path $e2eBase 'Caso!2026'
    try {
        New-Item -ItemType Directory -Force -Path $e2eRoot | Out-Null
        $e2eIped = Join-Path $e2eRoot 'iped-4.3.1'
        New-Item -ItemType Directory -Force -Path "$e2eIped\lib", "$e2eIped\localization", "$e2eIped\plugins" | Out-Null
        if (Test-Path -LiteralPath "$IpedHome\iped.exe") {
            Copy-Item -LiteralPath "$IpedHome\iped.exe" -Destination "$e2eIped\iped.exe" -Force
        } else {
            New-Item -ItemType File -Force -Path "$e2eIped\iped.exe" | Out-Null
        }
        Copy-Item -LiteralPath "$IpedHome\lib\iped-viewers-impl-4.3.1.jar" -Destination "$e2eIped\lib\iped-viewers-impl-4.3.1.jar" -Force
        Copy-Item -LiteralPath "$IpedHome\lib\json-20250517.jar" -Destination "$e2eIped\lib\json-20250517.jar" -Force
        Copy-Item -LiteralPath "$IpedHome\localization\iped-viewer-messages.properties" -Destination "$e2eIped\localization\iped-viewer-messages.properties" -Force
        Copy-Item -LiteralPath "$IpedHome\localization\iped-viewer-messages_pt_BR.properties" -Destination "$e2eIped\localization\iped-viewer-messages_pt_BR.properties" -Force

        [IO.Compression.ZipFile]::ExtractToDirectory((Resolve-Path -LiteralPath $ZipPath).Path, $e2eRoot)
        & cmd /c "`"$e2eRoot\instalar_captura_chat_zap\instalar_captura_chat_zap.bat`" < nul"
        if ($LASTEXITCODE -ne 0) { throw "Instalador ZIP em copia limpa do IPED 4.3.1 falhou ($LASTEXITCODE)" }

        $instJarHash = (Get-FileHash -LiteralPath "$e2eIped\lib\iped-viewers-impl-4.3.1.jar" -Algorithm SHA256).Hash.ToUpperInvariant()
        $instVJarHash = (Get-FileHash -LiteralPath "$e2eIped\verificador-integridade\integridade-verifier.jar" -Algorithm SHA256).Hash.ToUpperInvariant()
        $instVExeHash = (Get-FileHash -LiteralPath "$e2eIped\verificador-integridade\integridade-verifier.exe" -Algorithm SHA256).Hash.ToUpperInvariant()
        if ($instJarHash -ne $ExpectedPluginSha256) { throw "Hash plugin instalado diverge: $instJarHash != $ExpectedPluginSha256" }
        if ($instVJarHash -ne $ExpectedVerifierJarSha256) { throw "Hash Verificador JAR instalado diverge: $instVJarHash != $ExpectedVerifierJarSha256" }
        if ($instVExeHash -ne $ExpectedVerifierExeSha256) { throw "Hash Verificador EXE instalado diverge: $instVExeHash != $ExpectedVerifierExeSha256" }
        if (Test-Path -LiteralPath "$e2eIped\lib\json-20260719.jar") { throw 'Duplicacao json-20260719.jar em lib\' }
        if (Test-Path -LiteralPath "$e2eIped\lib\opencv-4120.jar") { throw 'Duplicacao opencv-4120.jar em lib\' }
        Write-Host 'E2E_CLEAN_IPED_INSTALL=PASS'
    } finally {
        Remove-Item -LiteralPath $e2eBase -Recurse -Force -ErrorAction SilentlyContinue
    }

    Write-Host "VERIFICATION_SUITES_COMPLETE=PASS ($TestMode)"
} finally {
    Pop-Location
}
