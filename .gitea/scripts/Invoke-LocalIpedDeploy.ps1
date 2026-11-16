<#
.SYNOPSIS
    Atualiza instalacoes locais do IPED (e pastas de aquisicao com subpasta iped\)
    com conferencia estrita SHA-256, backup automatico e --self-test do Verificador.
#>
param(
    [Parameter(Mandatory = $true)][string]$RepoRoot,
    [Parameter(Mandatory = $true)][string]$PluginJar,
    [Parameter(Mandatory = $true)][string]$VerifierJar,
    [Parameter(Mandatory = $true)][string]$VerifierExe,
    [Parameter(Mandatory = $true)][string]$PluginVersion,
    [Parameter(Mandatory = $true)][string]$VerifierVersion,
    [Parameter(Mandatory = $true)][string]$ExpectedPluginSha256,
    [Parameter(Mandatory = $true)][string]$ExpectedVerifierJarSha256,
    [Parameter(Mandatory = $true)][string]$ExpectedVerifierExeSha256,
    [Parameter(Mandatory = $true)][string]$TargetsSemicolonList,
    [string]$JavaExe = '',
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'

. (Join-Path $RepoRoot 'tests\resolve_test_env.ps1')

if ([string]::IsNullOrWhiteSpace($JavaExe)) {
    $jdk = Resolve-JdkHome -EnvNames @('JAVA21_HOME', 'JAVA26_HOME', 'JAVA11_HOME', 'JAVA_HOME') -MajorVersions @(21, 26, 25, 24, 23, 22, 17, 11) -RequiredTool 'bin\java.exe'
    $JavaExe = Join-Path $jdk 'bin\java.exe'
}

$resDir = Join-Path $RepoRoot 'instalar_captura_chat_zap'
$validateScript = Join-Path $resDir 'validate-plugin-jar.ps1'
$locScript = Join-Path $resDir 'install-localization.ps1'
$jsonSrc = Join-Path $resDir 'dependencies\json-20260719.jar'
$ocvDllSrc = Join-Path $resDir 'opencv_java4120.dll'

$ExpectedPluginSha256 = $ExpectedPluginSha256.Trim().ToUpperInvariant()
$ExpectedVerifierJarSha256 = $ExpectedVerifierJarSha256.Trim().ToUpperInvariant()
$ExpectedVerifierExeSha256 = $ExpectedVerifierExeSha256.Trim().ToUpperInvariant()

& powershell -NoProfile -ExecutionPolicy Bypass -File $validateScript -Jar $PluginJar -ExpectedSha256 $ExpectedPluginSha256
if ($LASTEXITCODE -ne 0) { throw "validate-plugin-jar.ps1 falhou para $PluginJar" }

$actualVJar = (Get-FileHash -LiteralPath $VerifierJar -Algorithm SHA256).Hash.ToUpperInvariant()
$actualVExe = (Get-FileHash -LiteralPath $VerifierExe -Algorithm SHA256).Hash.ToUpperInvariant()
if ($actualVJar -ne $ExpectedVerifierJarSha256) { throw "Hash do Verificador JAR diverge: $actualVJar != $ExpectedVerifierJarSha256" }
if ($actualVExe -ne $ExpectedVerifierExeSha256) { throw "Hash do Verificador EXE diverge: $actualVExe != $ExpectedVerifierExeSha256" }

$targetList = @($TargetsSemicolonList -split '[;\r\n]+' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
if ($targetList.Count -eq 0) { throw 'Nenhum diretorio IPED alvo informado.' }

$running = @(Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.ProcessName -match '^(iped|IPED-SearchApp|integridade-verifier)$' })
if ($running.Count -gt 0 -and -not $DryRun) {
    $names = ($running | ForEach-Object { "$($_.ProcessName)($($_.Id))" }) -join ', '
    throw "Feche os processos ativos antes do deploy local: $names"
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$bakTag = "$PluginVersion-$stamp"

foreach ($base in $targetList) {
    if (!(Test-Path -LiteralPath $base -PathType Container)) {
        throw "Diretorio alvo nao encontrado: $base"
    }
    $iped = if (Test-Path -LiteralPath (Join-Path $base 'lib\iped-viewers-impl-4.3.1.jar')) {
        $base
    } elseif (Test-Path -LiteralPath (Join-Path $base 'iped\lib\iped-viewers-impl-4.3.1.jar')) {
        Join-Path $base 'iped'
    } else {
        throw "Alvo nao contem lib\iped-viewers-impl-4.3.1.jar (nem em subpasta iped\): $base"
    }

    if ($DryRun) {
        Write-Host "[DRY-RUN] Alvo valido para deploy $PluginVersion/$VerifierVersion : $iped"
        continue
    }

    $dstPlugin = Join-Path $iped 'lib\iped-viewers-impl-4.3.1.jar'
    $dstVDir = Join-Path $iped 'verificador-integridade'
    if (!(Test-Path -LiteralPath $dstVDir)) { New-Item -ItemType Directory -Force -Path $dstVDir | Out-Null }

    $copyPairs = @(
        @{ Src = $PluginJar;   Dst = $dstPlugin; Hash = $ExpectedPluginSha256 },
        @{ Src = $VerifierJar; Dst = (Join-Path $dstVDir 'integridade-verifier.jar'); Hash = $ExpectedVerifierJarSha256 },
        @{ Src = $VerifierExe; Dst = (Join-Path $dstVDir 'integridade-verifier.exe'); Hash = $ExpectedVerifierExeSha256 },
        @{ Src = $VerifierJar; Dst = (Join-Path $iped 'integridade-verifier.jar'); Hash = $ExpectedVerifierJarSha256 },
        @{ Src = $VerifierExe; Dst = (Join-Path $iped 'integridade-verifier.exe'); Hash = $ExpectedVerifierExeSha256 }
    )

    if (Test-Path -LiteralPath $jsonSrc) {
        $jsonExpected = (Get-FileHash -LiteralPath $jsonSrc -Algorithm SHA256).Hash.ToUpperInvariant()
        $dstJsonOld = Join-Path $iped 'lib\json-20250517.jar'
        if (Test-Path -LiteralPath $dstJsonOld) {
            $copyPairs += @{ Src = $jsonSrc; Dst = $dstJsonOld; Hash = $jsonExpected }
        }
    }
    if (Test-Path -LiteralPath $ocvDllSrc) {
        $ocvExpected = (Get-FileHash -LiteralPath $ocvDllSrc -Algorithm SHA256).Hash.ToUpperInvariant()
        $copyPairs += @{ Src = $ocvDllSrc; Dst = (Join-Path $iped 'opencv_java4120.dll'); Hash = $ocvExpected }
        $pluginsDir = Join-Path $iped 'plugins'
        if (!(Test-Path -LiteralPath $pluginsDir)) { New-Item -ItemType Directory -Force -Path $pluginsDir | Out-Null }
        $copyPairs += @{ Src = $ocvDllSrc; Dst = (Join-Path $pluginsDir 'opencv_java4120.dll'); Hash = $ocvExpected }
    }

    foreach ($dupRel in @('lib\json-20260719.jar', 'lib\opencv-4120.jar')) {
        $dupPath = Join-Path $iped $dupRel
        if (Test-Path -LiteralPath $dupPath) {
            Move-Item -LiteralPath $dupPath -Destination "$dupPath.bak-$bakTag" -Force
        }
    }

    foreach ($p in $copyPairs) {
        if (Test-Path -LiteralPath $p.Dst) {
            $curHash = (Get-FileHash -LiteralPath $p.Dst -Algorithm SHA256).Hash.ToUpperInvariant()
            if ($curHash -ne $p.Hash) {
                Copy-Item -LiteralPath $p.Dst -Destination "$($p.Dst).bak-$bakTag" -Force
            }
        }
        Copy-Item -LiteralPath $p.Src -Destination $p.Dst -Force
        $writtenHash = (Get-FileHash -LiteralPath $p.Dst -Algorithm SHA256).Hash.ToUpperInvariant()
        if ($writtenHash -ne $p.Hash) {
            throw "Divergencia SHA-256 pos-copia em $($p.Dst): $writtenHash != $($p.Hash)"
        }
    }

    $locDir = Join-Path $iped 'localization'
    if (Test-Path -LiteralPath $locDir) {
        & powershell -NoProfile -ExecutionPolicy Bypass -File $locScript -Jar $dstPlugin -ResourceDir $resDir -LocaleDir $locDir -BackupStamp $bakTag
        if ($LASTEXITCODE -ne 0) { throw "install-localization.ps1 falhou em $locDir" }
    }

    $installedJar = Join-Path $dstVDir 'integridade-verifier.jar'
    $installedExe = Join-Path $dstVDir 'integridade-verifier.exe'
    $jVer = (& $JavaExe -jar $installedJar --version) | Out-String
    $jSt = (& $JavaExe -jar $installedJar --self-test) | Out-String
    $exeProc = Start-Process -FilePath $installedExe -ArgumentList '--self-test' -Wait -PassThru -NoNewWindow
    if ($jVer -notmatch [Regex]::Escape($VerifierVersion) -or $jSt -notmatch ("SELF_TEST_OK " + [Regex]::Escape($VerifierVersion)) -or $exeProc.ExitCode -ne 0) {
        throw "Autoteste falhou em $iped (jVer=$jVer | jSt=$jSt | exeExit=$($exeProc.ExitCode))"
    }
    Write-Host "LOCAL_DEPLOY_VERIFIED_OK: $iped (plugin=$PluginVersion | verifier=$VerifierVersion)"
}

Write-Host "ALL_LOCAL_DEPLOY_TARGETS_OK ($($targetList.Count))"
