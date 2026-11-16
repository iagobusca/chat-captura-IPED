<#
.SYNOPSIS
    Fluxo completo 1-click para cortar uma nova versao (bump, build deterministico
    duplo em 2 etapas, allowlists/testes/docs, commit/branch/tag, Release/Packages e
    deploy local opcional nas pastas IPED).
#>
param(
    [Parameter(Mandatory = $true)][string]$RepoRoot,
    [Parameter(Mandatory = $true)][string]$RepoSlug,
    [Parameter(Mandatory = $true)][string]$PluginVersion,
    [Parameter(Mandatory = $true)][string]$VerifierVersion,
    [ValidateSet('estavel', 'beta')][string]$Channel = 'estavel',
    [string]$SummaryNotes = '',
    [string]$Token = '',
    [string]$GiteaBaseUrl = 'http://127.0.0.1:3000',
    [string]$ReproCacheDir = 'C:\Avilla-Forensics-Free\repro-cache',
    [string]$IpedHome = '',
    [bool]$PublishGiteaPackages = $true,
    [bool]$DeployLocalIped = $false,
    [string]$LocalIpedTargets = '',
    [bool]$AllowSameVersion = $false,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'

$PluginVersion = $PluginVersion.Trim()
$VerifierVersion = $VerifierVersion.Trim()
if ($PluginVersion -notmatch '^[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9._-]+)?$') {
    throw "PluginVersion invalida (esperado X.Y.Z ou X.Y.Z-beta): $PluginVersion"
}
if ($VerifierVersion -notmatch '^[0-9]+\.[0-9]+\.[0-9]+$') {
    throw "VerifierVersion invalida (esperado X.Y.Z): $VerifierVersion"
}

$targetBranch = if ($Channel -eq 'estavel') { 'main' } else { 'beta' }
$isPrerelease = ($Channel -eq 'beta') -or ($PluginVersion -match '-')
$tagName = "v$PluginVersion"
$todayIso = Get-Date -Format 'yyyy-MM-dd'
$todayBr = Get-Date -Format 'dd/MM/yyyy'
$ptBr = [Globalization.CultureInfo]::GetCultureInfo('pt-BR')

. (Join-Path $RepoRoot 'tests\resolve_test_env.ps1')

$jdk8 = Resolve-JdkHome -EnvNames @('JAVA8_HOME', 'JAVA_HOME') -MajorVersions @(8) -RequiredTool 'bin\javac.exe'
$jdk11 = Resolve-JdkHome -EnvNames @('JAVA11_HOME', 'JAVA_HOME') -MajorVersions @(11) -RequiredTool 'bin\jar.exe'
$jdk21 = Resolve-JdkHome -Explicit $(if (Test-Path -LiteralPath 'C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot\bin\javac.exe') { 'C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot' } else { '' }) -EnvNames @('JAVA21_HOME', 'JAVA_HOME') -MajorVersions @(21) -RequiredTool 'bin\javac.exe'
if ([string]::IsNullOrWhiteSpace($IpedHome) -or !(Test-Path -LiteralPath (Join-Path $IpedHome 'lib'))) {
    $IpedHome = if (Test-Path -LiteralPath 'C:\Avilla-Forensics-Free\IPED-4.3.1_and_java_plugins\iped-4.3.1\lib') {
        'C:\Avilla-Forensics-Free\IPED-4.3.1_and_java_plugins\iped-4.3.1'
    } else {
        Resolve-WorkspaceIpedRoot -RepoRoot $RepoRoot
    }
}

$reproScript = Join-Path $RepoRoot 'build-reproducible.ps1'
$reproOut = Join-Path $RepoRoot 'out\reproducible'

function Get-RepoText([string]$RelPath) {
    return [IO.File]::ReadAllText((Join-Path $RepoRoot $RelPath), [Text.UTF8Encoding]::new($false))
}
function Set-RepoText([string]$RelPath, [string]$Content) {
    [IO.File]::WriteAllText((Join-Path $RepoRoot $RelPath), $Content, [Text.UTF8Encoding]::new($false))
}
function Insert-BeforeRegexAnchor([string]$Text, [string]$Pattern, [string]$InsertBlock, [string]$Label) {
    $m = [regex]::Match($Text, $Pattern, [Text.RegularExpressions.RegexOptions]::Multiline)
    if (-not $m.Success) { throw "Ancora esperada nao encontrada em ${Label}: $Pattern" }
    return ($Text.Substring(0, $m.Index) + $InsertBlock + $Text.Substring($m.Index))
}
function Invoke-ReproBuildStep([string]$TargetMode, [switch]$Verify, [switch]$RunSelfTests) {
    $args = @(
        '-NoProfile', '-ExecutionPolicy', 'Bypass',
        '-File', $reproScript,
        '-Target', $TargetMode,
        '-PinTemurin',
        '-JdkHome', $jdk21,
        '-IpedHome', $IpedHome,
        '-CacheDir', $ReproCacheDir,
        '-OutDir', $reproOut
    )
    $cachedJfx = Join-Path $ReproCacheDir 'openjfx-21.0.5-windows-x64\javafx-sdk-21.0.5\lib\javafx.web.jar'
    if (Test-Path -LiteralPath $cachedJfx) { $args += '-Offline' } else { $args += '-DownloadDeps' }
    if ($Verify) { $args += '-VerifyReproducibility' }
    if ($RunSelfTests) { $args += '-RunTests' }
    & powershell @args
    if ($LASTEXITCODE -ne 0) { throw "build-reproducible.ps1 ($TargetMode) falhou ($LASTEXITCODE)" }
}

$prevVerifierVer = (Get-RepoText 'verificador-integridade\VERSION').Trim()
$viewerText = Get-RepoText 'src\iped\viewers\HtmlLinkViewer.java'
if ($viewerText -notmatch 'CHAT_CAPTURE_PLUGIN_VERSION\s*=\s*"([^"]+)"') {
    throw 'CHAT_CAPTURE_PLUGIN_VERSION nao encontrado em HtmlLinkViewer.java'
}
$prevPluginVer = $Matches[1]

$viText = Get-RepoText 'src\iped\viewers\VerifierIntegrity.java'
if ($viText -notmatch 'VALID_JAR_HASHES\s*=\s*Set\.of\("([A-F0-9]{64})"\)') { throw 'VALID_JAR_HASHES nao encontrado' }
$prevJarHash = $Matches[1]
if ($viText -notmatch 'VALID_EXE_HASHES\s*=\s*Set\.of\("([A-F0-9]{64})"\)') { throw 'VALID_EXE_HASHES nao encontrado' }
$prevExeHash = $Matches[1]

if (-not $AllowSameVersion -and $PluginVersion -eq $prevPluginVer) {
    throw "PluginVersion informada ($PluginVersion) e igual a versao atual do codigo ($prevPluginVer). Informe uma nova versao (ex.: 2.7.1)."
}

if (-not $DryRun) {
    if ([string]::IsNullOrWhiteSpace($Token)) { throw 'Token Gitea necessario fora do modo DryRun.' }
    try {
        $existingTag = Invoke-RestMethod -Method Get -Uri "$($GiteaBaseUrl.TrimEnd('/'))/api/v1/repos/$RepoSlug/releases/tags/$tagName" -Headers @{ Authorization = "token $Token" } -TimeoutSec 10
        if ($existingTag -and $existingTag.id) {
            throw "Ja existe uma release publicada para a tag $tagName no Gitea (id=$($existingTag.id))."
        }
    } catch {
        if ($_.Exception.Message -like 'Ja existe*') { throw }
    }
}

Write-Host "=== Etapa 1: Bump do Verificador ($prevVerifierVer -> $VerifierVersion) + Build #1 ==="
if ($VerifierVersion -ne $prevVerifierVer) {
    Set-RepoText 'verificador-integridade\VERSION' "$VerifierVersion`r`n"

    $iv = Get-RepoText 'verificador-integridade\src\IntegrityVerifier.java'
    $iv = [regex]::Replace($iv, 'private static final String VERIFIER_VERSION = "[^"]+";', "private static final String VERIFIER_VERSION = `"$VerifierVersion`";")
    Set-RepoText 'verificador-integridade\src\IntegrityVerifier.java' $iv

    $csproj = Get-RepoText 'verificador-integridade\launcher\Launcher.csproj'
    $csproj = [regex]::Replace($csproj, '<Version>[^<]+</Version>', "<Version>$VerifierVersion</Version>")
    Set-RepoText 'verificador-integridade\launcher\Launcher.csproj' $csproj

    $progCs = Get-RepoText 'verificador-integridade\launcher\Program.cs'
    $progCs = [regex]::Replace($progCs, '^// Vers[^\r\n]*', [Regex]::Unescape("// Vers\u00e3o $VerifierVersion \u2014 corte automatizado GiteaRunner + empacotamento determin\u00edstico (.NET 10.0.15)."))
    Set-RepoText 'verificador-integridade\launcher\Program.cs' $progCs

    $bse = Get-RepoText 'verificador-integridade\build-single-exe.ps1'
    $bse = $bse.Replace("build-single-exe\$prevVerifierVer", "build-single-exe\$VerifierVersion")
    $bse = $bse.Replace("Implementation-Version: $prevVerifierVer", "Implementation-Version: $VerifierVersion")
    $bse = $bse.Replace("--app-version $prevVerifierVer", "--app-version $VerifierVersion")
    Set-RepoText 'verificador-integridade\build-single-exe.ps1' $bse

    $bbat = Get-RepoText 'verificador-integridade\build.bat'
    $bbat = $bbat.Replace("Implementation-Version: $prevVerifierVer", "Implementation-Version: $VerifierVersion")
    Set-RepoText 'verificador-integridade\build.bat' $bbat

    $pyInt = Get-RepoText 'teste_antes_de_aplicar\test_verifier_java_integration.py'
    $pyInt = $pyInt.Replace("Verificador de Integridade $prevVerifierVer", "Verificador de Integridade $VerifierVersion")
    $pyInt = $pyInt.Replace("SELF_TEST_OK $prevVerifierVer", "SELF_TEST_OK $VerifierVersion")
    Set-RepoText 'teste_antes_de_aplicar\test_verifier_java_integration.py' $pyInt
}

Invoke-ReproBuildStep -TargetMode 'full'

$vJarBuilt = Join-Path $reproOut 'integridade-verifier.jar'
$vExeBuilt = Join-Path $reproOut 'integridade-verifier.exe'
$vJarSha = (Get-FileHash -LiteralPath $vJarBuilt -Algorithm SHA256).Hash.ToUpperInvariant()
$vExeSha = (Get-FileHash -LiteralPath $vExeBuilt -Algorithm SHA256).Hash.ToUpperInvariant()
$vJarBytes = (Get-Item -LiteralPath $vJarBuilt).Length
$vExeBytes = (Get-Item -LiteralPath $vExeBuilt).Length

Copy-Item -LiteralPath $vJarBuilt -Destination (Join-Path $RepoRoot 'verificador-integridade\integridade-verifier.jar') -Force
Copy-Item -LiteralPath $vJarBuilt -Destination (Join-Path $RepoRoot 'instalar_captura_chat_zap\integridade-verifier.jar') -Force
Copy-Item -LiteralPath $vExeBuilt -Destination (Join-Path $RepoRoot 'verificador-integridade\integridade-verifier.exe') -Force
Copy-Item -LiteralPath $vExeBuilt -Destination (Join-Path $RepoRoot 'instalar_captura_chat_zap\integridade-verifier.exe') -Force

$java21Exe = Join-Path $jdk21 'bin\java.exe'
$verCheck = (& $java21Exe -jar (Join-Path $RepoRoot 'verificador-integridade\integridade-verifier.jar') --version) | Out-String
$stCheck = (& $java21Exe -jar (Join-Path $RepoRoot 'verificador-integridade\integridade-verifier.jar') --self-test) | Out-String
$exeProc = Start-Process -FilePath (Join-Path $RepoRoot 'verificador-integridade\integridade-verifier.exe') -ArgumentList '--self-test' -Wait -PassThru -NoNewWindow
if ($verCheck -notmatch [Regex]::Escape($VerifierVersion) -or $stCheck -notmatch ("SELF_TEST_OK " + [Regex]::Escape($VerifierVersion)) -or $exeProc.ExitCode -ne 0) {
    throw "Verificador $VerifierVersion falhou no autoteste"
}

Write-Host "=== Etapa 2: Sincronizar Allowlists ($prevPluginVer -> $PluginVersion) + Plugin JAR ==="
$viewerText = [regex]::Replace($viewerText, 'CHAT_CAPTURE_PLUGIN_VERSION\s*=\s*"[^"]+"', "CHAT_CAPTURE_PLUGIN_VERSION = `"$PluginVersion`"")
Set-RepoText 'src\iped\viewers\HtmlLinkViewer.java' $viewerText

$viText = [regex]::Replace($viText, 'VALID_JAR_HASHES\s*=\s*Set\.of\("[^"]+"\)', "VALID_JAR_HASHES = Set.of(`"$vJarSha`")")
$viText = [regex]::Replace($viText, 'VALID_EXE_HASHES\s*=\s*Set\.of\("[^"]+"\)', "VALID_EXE_HASHES = Set.of(`"$vExeSha`")")
Set-RepoText 'src\iped\viewers\VerifierIntegrity.java' $viText

$vDigits = ($VerifierVersion -replace '[^0-9]', '')
$vht = Get-RepoText 'tests\iped\viewers\VerifierIntegrityHashTest.java'
$vht = [regex]::Replace($vht, 'EXPECTED_JAR_\d+', "EXPECTED_JAR_$vDigits")
$vht = [regex]::Replace($vht, 'EXPECTED_EXE_\d+', "EXPECTED_EXE_$vDigits")
$vht = [regex]::Replace($vht, "private static final String EXPECTED_JAR_$vDigits = `"[A-F0-9]{64}`";", "private static final String EXPECTED_JAR_$vDigits = `"$vJarSha`";")
$vht = [regex]::Replace($vht, "private static final String EXPECTED_EXE_$vDigits = `"[A-F0-9]{64}`";", "private static final String EXPECTED_EXE_$vDigits = `"$vExeSha`";")
if ($prevJarHash -ne $vJarSha -and $vht -notmatch [Regex]::Escape($prevJarHash)) {
    $anchor = '"current EXE hash must be accepted");'
    $rejBlock = @"
$anchor
        check(!(Boolean) isValid.invoke(null, "integridade-verifier.jar",
                "$prevJarHash"), "previous $prevVerifierVer JAR hash must be rejected");
        check(!(Boolean) isValid.invoke(null, "integridade-verifier.exe",
                "$prevExeHash"), "previous $prevVerifierVer EXE hash must be rejected");
"@.TrimEnd()
    $vht = $vht.Replace($anchor, $rejBlock)
}
Set-RepoText 'tests\iped\viewers\VerifierIntegrityHashTest.java' $vht

$bat = Get-RepoText 'instalar_captura_chat_zap\instalar_captura_chat_zap.bat'
$bat = [regex]::Replace($bat, 'EXPECTED_VERIFIER_JAR_SHA256=[0-9A-Fa-f]{64}', "EXPECTED_VERIFIER_JAR_SHA256=$vJarSha")
$bat = [regex]::Replace($bat, 'EXPECTED_VERIFIER_EXE_SHA256=[0-9A-Fa-f]{64}', "EXPECTED_VERIFIER_EXE_SHA256=$vExeSha")
$bat = [regex]::Replace($bat, 'echo Versao do plugin: [^\r\n]+', "echo Versao do plugin: $PluginVersion")
$bat = [regex]::Replace($bat, 'echo Verificador de integridade: [^\r\n]+', "echo Verificador de integridade: $VerifierVersion")
Set-RepoText 'instalar_captura_chat_zap\instalar_captura_chat_zap.bat' $bat

Invoke-ReproBuildStep -TargetMode 'jar-only'

$pJarBuilt = Join-Path $reproOut 'iped-viewers-impl-4.3.1.jar'
$pJarSha = (Get-FileHash -LiteralPath $pJarBuilt -Algorithm SHA256).Hash.ToUpperInvariant()
$pJarBytes = (Get-Item -LiteralPath $pJarBuilt).Length

Copy-Item -LiteralPath $pJarBuilt -Destination (Join-Path $RepoRoot 'instalar_captura_chat_zap\iped-viewers-impl-4.3.1.jar') -Force
Copy-Item -LiteralPath $pJarBuilt -Destination (Join-Path $RepoRoot "instalar_captura_chat_zap\iped-viewers-impl-4.3.1-$PluginVersion.jar") -Force
if ($prevPluginVer -ne $PluginVersion) {
    $oldVerJar = Join-Path $RepoRoot "instalar_captura_chat_zap\iped-viewers-impl-4.3.1-$prevPluginVer.jar"
    if (Test-Path -LiteralPath $oldVerJar) {
        Remove-Item -LiteralPath $oldVerJar -Force
    }
}

$bat = [regex]::Replace($bat, 'EXPECTED_PLUGIN_SHA256=[0-9A-Fa-f]{64}', "EXPECTED_PLUGIN_SHA256=$pJarSha")
Set-RepoText 'instalar_captura_chat_zap\instalar_captura_chat_zap.bat' $bat

$leia = Get-RepoText 'LEIA-ME.txt'
$leia = [regex]::Replace($leia, 'Pacote Chat Captura IPED [^\r\n]+ - Verificador de Integridade [^\r\n]+', "Pacote Chat Captura IPED $PluginVersion - Verificador de Integridade $VerifierVersion")
$leia = $leia.Replace("Verificador de Integridade $prevVerifierVer", "Verificador de Integridade $VerifierVersion")
$leia = $leia.Replace("SHA-256 do Verificador ${prevVerifierVer}:", "SHA-256 do Verificador ${VerifierVersion}:")
$leia = $leia.Replace("JAR $prevJarHash", "JAR $vJarSha")
$leia = $leia.Replace("EXE $prevExeHash", "EXE $vExeSha")
$leia = $leia.Replace("SHA-256 do plugin ${prevPluginVer}:", "SHA-256 do plugin ${PluginVersion}:")
$leia = [regex]::Replace($leia, "(?s)(SHA-256 do plugin [^\r\n]+:\r?\n)JAR [A-F0-9]{64}", "`${1}JAR $pJarSha")
$leia = [regex]::Replace($leia, 'Novidades desta vers[^\r\n]+\([^\)]+\):', [Regex]::Unescape("Novidades desta vers\u00e3o ($PluginVersion / Verificador $VerifierVersion \u2014 $Channel):"))
$leia = $leia.Replace("- Verificador ${prevVerifierVer}:", "- Verificador ${VerifierVersion}:")
$leia = $leia.Replace("- Plugin ${prevPluginVer}:", "- Plugin ${PluginVersion}:")
$leia = $leia.Replace("Verificador $prevVerifierVer", "Verificador $VerifierVersion")
Set-RepoText 'LEIA-ME.txt' $leia

Write-Host "=== Etapa 3: Compilacao Deterministica Dupla Final + Validacao ZIP ==="
Invoke-ReproBuildStep -TargetMode 'full' -Verify -RunSelfTests

$zipName = "instalador-chat-captura-IPED-$PluginVersion-verificador-$VerifierVersion.zip"
$zipBuilt = Join-Path $reproOut $zipName
$zipSha = (Get-FileHash -LiteralPath $zipBuilt -Algorithm SHA256).Hash.ToUpperInvariant()
$zipBytes = (Get-Item -LiteralPath $zipBuilt).Length

foreach ($pair in @(
    @{ Path = $vJarBuilt; Expected = $vJarSha; Label = 'Verifier JAR' },
    @{ Path = $vExeBuilt; Expected = $vExeSha; Label = 'Verifier EXE' },
    @{ Path = $pJarBuilt; Expected = $pJarSha; Label = 'Plugin JAR' }
)) {
    $chk = (Get-FileHash -LiteralPath $pair.Path -Algorithm SHA256).Hash.ToUpperInvariant()
    if ($chk -ne $pair.Expected) {
        throw "Drift inesperado apos finalizar docs internas em $($pair.Label): $chk != $($pair.Expected)"
    }
}

Write-Host "=== Etapa 4: Atualizar Documentacao e Historico de Checksums ==="
$summaryDesc = if ([string]::IsNullOrWhiteSpace($SummaryNotes)) {
    [Regex]::Unescape("Incremento automatizado via GiteaRunner (``cut-release.yml`` / ``build-reproducible.ps1``) para **$PluginVersion** / Verificador **$VerifierVersion**.")
} else {
    $SummaryNotes.Trim()
}
$zipFmt = [string]::Format($ptBr, '{0:N0}', $zipBytes)
$exeFmt = [string]::Format($ptBr, '{0:N0}', $vExeBytes)
$jarFmt = [string]::Format($ptBr, '{0:N0}', $vJarBytes)
$pFmt = [string]::Format($ptBr, '{0:N0}', $pJarBytes)
$chanLabel = if ($Channel -eq 'estavel') { [Regex]::Unescape('Est\u00e1vel / ``main``') } else { 'Beta / ``beta``' }

$checksums = Get-RepoText 'CHECKSUMS.md'
$csHeader = @"
## Build $PluginVersion ($chanLabel) / Verificador $VerifierVersion

| Arquivo | Tamanho (bytes) | SHA-256 | Descri$([char]0x00E7)$([char]0x00E3)o |
| --- | ---: | --- | --- |
| ``$zipName`` | $zipBytes | ``$zipSha`` | Pacote completo compilado via ``build-reproducible.ps1`` (plugin ``$PluginVersion``, Verificador ``$VerifierVersion`` JAR+EXE com ``.NET 10.0.15``) |
| ``instalar_captura_chat_zap/iped-viewers-impl-4.3.1-$PluginVersion.jar`` | $pJarBytes | ``$pJarSha`` | Fat-JAR determin$([char]0x00ED)stico do plugin (``Implementation-Version: $PluginVersion``, ``--release 11``, OpenCV embutido); mesmo bin$([char]0x00E1)rio de ``iped-viewers-impl-4.3.1.jar`` |
| ``verificador-integridade/integridade-verifier.jar`` | $vJarBytes | ``$vJarSha`` | Verificador ``$VerifierVersion`` JAR determin$([char]0x00ED)stico (``--release 8``, bytecode 52) |
| ``verificador-integridade/integridade-verifier.exe`` | $vExeBytes | ``$vExeSha`` | Execut$([char]0x00E1)vel Windows x64 determin$([char]0x00ED)stico do Verificador ``$VerifierVersion`` (``jlink``/``jpackage`` + ``.NET 10.0.15``, ``--self-test`` OK) |

"@
$checksums = Insert-BeforeRegexAnchor -Text $checksums -Pattern '^## Build ' -InsertBlock $csHeader -Label 'CHECKSUMS.md'
Set-RepoText 'CHECKSUMS.md' $checksums

$rn = Get-RepoText 'RELEASE_NOTES.md'
$rnEntry = @"
## $tagName ($chanLabel) / Verificador $VerifierVersion

- **Tag**: [$tagName]($GiteaBaseUrl/$RepoSlug/releases/tag/$tagName) (Corte automatizado via ``GiteaRunner``)
- **Branch base**: ``$targetBranch``

### Downloads
- [$zipName]($GiteaBaseUrl/$RepoSlug/releases/download/$tagName/$zipName) (``$zipFmt`` bytes | SHA-256: ``$zipSha``)
- [integridade-verifier-$VerifierVersion.exe]($GiteaBaseUrl/$RepoSlug/releases/download/$tagName/integridade-verifier-$VerifierVersion.exe) (``$exeFmt`` bytes | SHA-256: ``$vExeSha``)
- [integridade-verifier-$VerifierVersion.jar]($GiteaBaseUrl/$RepoSlug/releases/download/$tagName/integridade-verifier-$VerifierVersion.jar) (``$jarFmt`` bytes | SHA-256: ``$vJarSha``)
- [iped-viewers-impl-4.3.1-$PluginVersion.jar]($GiteaBaseUrl/$RepoSlug/releases/download/$tagName/iped-viewers-impl-4.3.1-$PluginVersion.jar) (``$pFmt`` bytes | SHA-256: ``$pJarSha``)

### Resumo
- $summaryDesc
- Compila$([char]0x00E7)$([char]0x00E3)o determin$([char]0x00ED)stica dupla verificada (``REPRODUCIBLE_BUILD_VERIFIED=PASS``) e allowlist exclusiva sincronizada para Verificador **$VerifierVersion**.

---

"@
$rn = Insert-BeforeRegexAnchor -Text $rn -Pattern '^## v[0-9]' -InsertBlock $rnEntry -Label 'RELEASE_NOTES.md'
Set-RepoText 'RELEASE_NOTES.md' $rn

$vh = Get-RepoText 'versao_hash.md'
if ($VerifierVersion -ne $prevVerifierVer -or $vJarSha -ne $prevJarHash -or $vExeSha -ne $prevExeHash) {
    $vhRows = @"
| $VerifierVersion (release $tagName) | ``integridade-verifier.jar`` | ``$vJarSha`` | Build determin$([char]0x00ED)stico (``build-reproducible.ps1`` ``--release 8``); ``$jarFmt`` bytes; ``SELF_TEST_OK $VerifierVersion`` |
| $VerifierVersion (release $tagName) | ``integridade-verifier.exe`` | ``$vExeSha`` | Build Windows x64 determin$([char]0x00ED)stico (``.NET 10.0.15``); ``$exeFmt`` bytes; ``SELF_TEST_OK $VerifierVersion`` |
"@
    $vh = Insert-BeforeRegexAnchor -Text $vh -Pattern '^\| [0-9]+\.[0-9]+\.[0-9]+ \(release ' -InsertBlock $vhRows -Label 'versao_hash.md'
}
$vh = $vh.Replace("A vers$([char]0x00E3)o $prevVerifierVer acompanha a release ``v$prevPluginVer``", "A vers$([char]0x00E3)o $VerifierVersion acompanha a release ``$tagName``")
Set-RepoText 'versao_hash.md' $vh

$ch = Get-RepoText 'CHANGELOG.md'
$chEntry = @"
## [$PluginVersion] - $todayIso

### Corte Automatizado ($chanLabel / GiteaRunner)
- $summaryDesc
- Verificador **$VerifierVersion** (``$jarFmt`` bytes JAR ``$($vJarSha.Substring(0,8))...`` | ``$exeFmt`` bytes EXE ``$($vExeSha.Substring(0,8))...``) e Plugin **$PluginVersion** (``$pFmt`` bytes ``$($pJarSha.Substring(0,8))...``) compilados via ``build-reproducible.ps1``; instalador ``$zipName`` (``$zipFmt`` bytes ``$($zipSha.Substring(0,8))...``).

"@
$ch = Insert-BeforeRegexAnchor -Text $ch -Pattern '^## \[[0-9]' -InsertBlock $chEntry -Label 'CHANGELOG.md'
Set-RepoText 'CHANGELOG.md' $ch

$readme = Get-RepoText 'README.md'
$readme = [regex]::Replace($readme, 'Vers[^\r\n]*atual do plugin:\*\* `[^`]+` \(branch `[^`]+`\)', [Regex]::Unescape("Vers\u00e3o atual do plugin:** ``$PluginVersion`` (branch ``$targetBranch``)"))
$readme = [regex]::Replace($readme, 'Verificador de Integridade:\*\* `[^`]+`', "Verificador de Integridade:** ``$VerifierVersion``")
$readme = $readme.Replace("instalador-chat-captura-IPED-$prevPluginVer-verificador-$prevVerifierVer.zip", $zipName)
$readme = $readme.Replace("iped-viewers-impl-4.3.1-$prevPluginVer.jar", "iped-viewers-impl-4.3.1-$PluginVersion.jar")
$readme = $readme.Replace("integridade-verifier-$prevVerifierVer.jar", "integridade-verifier-$VerifierVersion.jar")
$readme = $readme.Replace("integridade-verifier-$prevVerifierVer.exe", "integridade-verifier-$VerifierVersion.exe")
$readme = $readme.Replace("Allowlist exclusiva de seguran$([char]0x00E7)a:** o plugin ``$prevPluginVer``", "Allowlist exclusiva de seguran$([char]0x00E7)a:** o plugin ``$PluginVersion``")
$readme = $readme.Replace("($prevPluginVer / Verificador $prevVerifierVer)", "($PluginVersion / Verificador $VerifierVersion)")
$readme = $readme.Replace("da s$([char]0x00E9)rie ``$prevVerifierVer``", "da s$([char]0x00E9)rie ``$VerifierVersion``")
$readme = $readme.Replace("Verificador de Integridade ``$prevVerifierVer``", "Verificador de Integridade ``$VerifierVersion``")
$readme = $readme.Replace("Verificador ``$prevVerifierVer``", "Verificador ``$VerifierVersion``")
$readme = $readme.Replace("fat-JAR ``$prevPluginVer``", "fat-JAR ``$PluginVersion``")
$readme = $readme.Replace("Implementation-Version: $prevPluginVer", "Implementation-Version: $PluginVersion")
$readme = $readme.Replace("SELF_TEST_OK $prevVerifierVer", "SELF_TEST_OK $VerifierVersion")
$readme = $readme.Replace("Artefatos e SHA-256 (``v$prevPluginVer`` / Verificador ``$prevVerifierVer``)", "Artefatos e SHA-256 (``$tagName`` / Verificador ``$VerifierVersion``)")
$readme = $readme.Replace("da tag ``v$prevPluginVer``", "da tag ``$tagName``")
$readme = [regex]::Replace($readme, "(\|\s*``$([Regex]::Escape($zipName))``\s*\|\s*``)\d+(``\s*\|\s*``)[A-F0-9]{64}(``)", "`${1}$zipBytes`${2}$zipSha`${3}")
$readme = [regex]::Replace($readme, "(\|\s*``iped-viewers-impl-4\.3\.1-$([Regex]::Escape($PluginVersion))\.jar``\s*\|\s*``)\d+(``\s*\|\s*``)[A-F0-9]{64}(``)", "`${1}$pJarBytes`${2}$pJarSha`${3}")
$readme = [regex]::Replace($readme, "(\|\s*``integridade-verifier-$([Regex]::Escape($VerifierVersion))\.jar``\s*\|\s*``)\d+(``\s*\|\s*``)[A-F0-9]{64}(``)", "`${1}$vJarBytes`${2}$vJarSha`${3}")
$readme = [regex]::Replace($readme, "(\|\s*``integridade-verifier-$([Regex]::Escape($VerifierVersion))\.exe``\s*\|\s*``)\d+(``\s*\|\s*``)[A-F0-9]{64}(``)", "`${1}$vExeBytes`${2}$vExeSha`${3}")
Set-RepoText 'README.md' $readme

$guia = Get-RepoText 'GUIA_DE_USO.md'
$guia = $guia.Replace("(v$prevPluginVer)", "($tagName)").Replace("(v$prevVerifierVer)", "(v$VerifierVersion)").Replace("Verificador de Integridade $prevVerifierVer", "Verificador de Integridade $VerifierVersion").Replace("Verificador de Integridade (``$prevVerifierVer``)", "Verificador de Integridade (``$VerifierVersion``)")
Set-RepoText 'GUIA_DE_USO.md' $guia

$espec = Get-RepoText 'espec.md'
$espec = $espec.Replace("Chat Captura IPED (``v$prevPluginVer``)", "Chat Captura IPED (``$tagName``)")
$espec = $espec.Replace("Verificador de Integridade (``$prevVerifierVer``)", "Verificador de Integridade (``$VerifierVersion``)")
$espec = $espec.Replace("Plugin ``$prevPluginVer`` | Verificador de Integridade ``$prevVerifierVer``", "Plugin ``$PluginVersion`` | Verificador de Integridade ``$VerifierVersion``")
$espec = $espec.Replace("(``$tagName`` / ``$prevVerifierVer``)", "(``$tagName`` / ``$VerifierVersion``)")
$espec = $espec.Replace("(``v$prevPluginVer`` / ``$prevVerifierVer``)", "(``$tagName`` / ``$VerifierVersion``)")
$espec = $espec.Replace("o plugin ``$prevPluginVer`` aceita", "o plugin ``$PluginVersion`` aceita")
$espec = $espec.Replace("Verificador $prevVerifierVer.", "Verificador $VerifierVersion.")
$espec = $espec.Replace("SELF_TEST_OK $prevVerifierVer", "SELF_TEST_OK $VerifierVersion")
$espec = $espec.Replace("Verificador de Integridade $prevVerifierVer", "Verificador de Integridade $VerifierVersion")
Set-RepoText 'espec.md' $espec

$vReadme = Get-RepoText 'verificador-integridade\README.md'
$vReadme = $vReadme.Replace("Verificador de Integridade $prevVerifierVer", "Verificador de Integridade $VerifierVersion")
$vReadme = $vReadme.Replace("A vers$([char]0x00E3)o $prevVerifierVer", "A vers$([char]0x00E3)o $VerifierVersion")
$vReadme = $vReadme.Replace("Hashes dos bin$([char]0x00E1)rios ${prevVerifierVer}:", "Hashes dos bin$([char]0x00E1)rios ${VerifierVersion}:")
$vReadme = $vReadme.Replace($prevJarHash, $vJarSha).Replace($prevExeHash, $vExeSha)
Set-RepoText 'verificador-integridade\README.md' $vReadme

if ($VerifierVersion -ne $prevVerifierVer) {
    $vCh = Get-RepoText 'verificador-integridade\CHANGELOG.md'
    $vChEntry = @"
## [$VerifierVersion] - $todayIso

### Corte Automatizado ($tagName)
- $summaryDesc (``build-reproducible.ps1``; JAR ``$($vJarSha.Substring(0,8))...`` / EXE ``$($vExeSha.Substring(0,8))...``).

"@
    $vCh = Insert-BeforeRegexAnchor -Text $vCh -Pattern '^## \[[0-9]' -InsertBlock $vChEntry -Label 'verificador-integridade/CHANGELOG.md'
    Set-RepoText 'verificador-integridade\CHANGELOG.md' $vCh
}

$vDoc = Get-RepoText 'verificador-integridade\doc.md'
$vDoc = $vDoc.Replace($prevVerifierVer, $VerifierVersion)
Set-RepoText 'verificador-integridade\doc.md' $vDoc

$ghWf = Get-RepoText '.github\workflows\release.yml'
$ghWf = $ghWf.Replace("default: 'v$prevPluginVer'", "default: '$tagName'").Replace("(ex.: v$prevPluginVer)", "(ex.: $tagName)")
Set-RepoText '.github\workflows\release.yml' $ghWf

Write-Host "=== Etapa 5: Executar Suites Completas Antes de Commitar/Publicar ==="
$env:IPED_HOME = $IpedHome
$env:JAVAFX_LIB = Join-Path $ReproCacheDir 'openjfx-21.0.5-windows-x64\javafx-sdk-21.0.5\lib'
$env:JAVA26_HOME = $jdk21
$env:JAVA21_HOME = $jdk21
$env:JAVA_HOME = $jdk21
$env:JAVA8_HOME = $jdk8
$env:JAVA11_HOME = $jdk11
$env:TEST_VERIFIER_EXE = $vExeBuilt
$env:TEST_VERIFIER_JAR = $vJarBuilt
$env:TEST_PLUGIN_JAR = $pJarBuilt
$env:CHAT_CAPTURE_SAMPLE_HTML = 'C:\Avilla-Forensics-Free\IPED-4.3.1_and_java_plugins\IPED_testes_chat_real\Exportados\arquivos\F\A\FAF3132301A706022E1C3EAD6AE0800B.html'
$env:CHAT_CAPTURE_SAMPLE_ROOT = 'C:\Avilla-Forensics-Free\IPED-4.3.1_and_java_plugins\IPED_testes_chat_real\teste_chat_extraido'
$env:IPED_JJS = Join-Path $IpedHome 'jre\bin\jjs.exe'

& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $RepoRoot 'verificador-integridade\prototype\test-security-spec.ps1')
if ($LASTEXITCODE -ne 0) { throw "test-security-spec.ps1 falhou" }
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $RepoRoot 'teste_antes_de_aplicar\run_all_tests.ps1') -Java8Home $jdk8 -Java11Home $jdk11 -Java26Home $jdk21 -IpedHome $IpedHome -JavaFxLib $env:JAVAFX_LIB
if ($LASTEXITCODE -ne 0) { throw "run_all_tests.ps1 falhou" }
foreach ($reg in @(
    'tests\run_capture_header_mime_regression.ps1',
    'tests\run_svg_sanitizer_compatibility.ps1',
    'tests\run_verifier_embedded_image_compatibility.ps1'
)) {
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $RepoRoot $reg)
    if ($LASTEXITCODE -ne 0) { throw "$reg falhou" }
}

if ($DryRun) {
    Write-Host "=== DRY-RUN CONCLUIDO SEM ALTERAR GIT/RELEASES/PASTAS ==="
    if ($DeployLocalIped -and $LocalIpedTargets) {
        & (Join-Path $RepoRoot '.gitea\scripts\Invoke-LocalIpedDeploy.ps1') `
            -RepoRoot $RepoRoot -PluginJar $pJarBuilt -VerifierJar $vJarBuilt -VerifierExe $vExeBuilt `
            -PluginVersion $PluginVersion -VerifierVersion $VerifierVersion `
            -ExpectedPluginSha256 $pJarSha -ExpectedVerifierJarSha256 $vJarSha -ExpectedVerifierExeSha256 $vExeSha `
            -TargetsSemicolonList $LocalIpedTargets -JavaExe $java21Exe -DryRun
    }
    Write-Host "DRY_RUN_CUT_RELEASE=PASS ($tagName | ZIP=$zipSha)"
    return
}

Write-Host "=== Etapa 6: Commit, Branch, Tag, Push e Publicacao no Gitea ==="
Push-Location $RepoRoot
try {
    & git config user.name 'GiteaRunner Bot'
    & git config user.email 'gitea-runner@local'

    $releaseBranch = "release/$tagName"
    & git checkout -B $releaseBranch
    if ($LASTEXITCODE -ne 0) { throw "git checkout -B $releaseBranch falhou" }
    & git add -A
    if ($LASTEXITCODE -ne 0) { throw 'git add -A falhou' }
    & git commit -m "release($tagName): corte automatizado plugin $PluginVersion / verificador $VerifierVersion"
    if ($LASTEXITCODE -ne 0) { throw 'git commit falhou' }

    $b64 = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("iago:$Token"))
    $remoteUrl = "$($GiteaBaseUrl.TrimEnd('/'))/$RepoSlug.git"
    & git remote set-url origin $remoteUrl
    & git fetch origin $targetBranch
    if ($LASTEXITCODE -eq 0) {
        & git merge-base --is-ancestor FETCH_HEAD $releaseBranch
        if ($LASTEXITCODE -eq 0) {
            & git checkout -B $targetBranch FETCH_HEAD
            if ($LASTEXITCODE -ne 0) { throw "git checkout $targetBranch falhou" }
            & git merge --ff-only $releaseBranch
            if ($LASTEXITCODE -ne 0) { throw "git merge --ff-only $releaseBranch falhou" }
        } else {
            & git checkout -B $targetBranch $releaseBranch
            if ($LASTEXITCODE -ne 0) { throw "git checkout -B $targetBranch falhou" }
            & git merge --no-edit -s ours FETCH_HEAD -m "chore: reconciliar $targetBranch antes de $tagName"
            if ($LASTEXITCODE -ne 0) { throw "git merge -s ours em $targetBranch falhou" }
        }
    } else {
        & git checkout -B $targetBranch
    }

    & git tag -f -a $tagName -m "Release $tagName ($Channel) - Plugin $PluginVersion / Verificador $VerifierVersion"
    if ($LASTEXITCODE -ne 0) { throw "git tag $tagName falhou" }

    & git -c "http.extraHeader=Authorization: Basic $b64" push origin $releaseBranch $targetBranch "refs/tags/$tagName"
    if ($LASTEXITCODE -ne 0) { throw 'git push origin falhou' }
} finally {
    Pop-Location
}

& (Join-Path $RepoRoot '.gitea\scripts\Publish-GiteaReleaseAndPackages.ps1') `
    -Repo $RepoSlug -Token $Token -Tag $tagName -TargetBranch $targetBranch -Prerelease $isPrerelease `
    -PluginVersion $PluginVersion -VerifierVersion $VerifierVersion `
    -ZipPath $zipBuilt -ExePath $vExeBuilt -JarPath $vJarBuilt -PluginPath $pJarBuilt `
    -GiteaBaseUrl $GiteaBaseUrl -SummaryNotes $SummaryNotes `
    -PublishRelease $true -PublishPackages $PublishGiteaPackages

if ($DeployLocalIped) {
    & (Join-Path $RepoRoot '.gitea\scripts\Invoke-LocalIpedDeploy.ps1') `
        -RepoRoot $RepoRoot -PluginJar $pJarBuilt -VerifierJar $vJarBuilt -VerifierExe $vExeBuilt `
        -PluginVersion $PluginVersion -VerifierVersion $VerifierVersion `
        -ExpectedPluginSha256 $pJarSha -ExpectedVerifierJarSha256 $vJarSha -ExpectedVerifierExeSha256 $vExeSha `
        -TargetsSemicolonList $LocalIpedTargets -JavaExe $java21Exe
}

Write-Host "CUT_RELEASE_COMPLETE=PASS ($tagName)"
