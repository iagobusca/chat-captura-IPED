<#
.SYNOPSIS
    Baixa os binarios verificados da Release oficial no GitHub (tag especifica ou
    Latest), confere SHA-256 (e atestado Sigstore opcional via gh CLI) e instala
    nas pastas locais do IPED com backup automatico via Invoke-LocalIpedDeploy.ps1.
#>
param(
    [string]$Tag = 'latest',
    [string]$GitHubRepo = 'iagobusca/chat-captura-IPED',
    [string]$Targets = 'C:\Avilla-Forensics-Free\IPED-4.3.1_and_java_plugins\iped-4.3.1;F:\Download\Avilla-Acquisitions\IPED_v10;F:\Download\Avilla-Acquisitions\IPED_v11;F:\Download\Avilla-Acquisitions\Prova_v2;F:\Download\Avilla-Acquisitions\Prova_v3',
    [switch]$VerifySigstore,
    [switch]$SyncGit,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$repoRoot = Split-Path -Parent $PSScriptRoot
$apiUrl = if ($Tag -eq 'latest' -or [string]::IsNullOrWhiteSpace($Tag)) {
    "https://api.github.com/repos/$GitHubRepo/releases/latest"
} else {
    "https://api.github.com/repos/$GitHubRepo/releases/tags/$Tag"
}

Write-Host "Consultando Release GitHub: $apiUrl"
$rel = Invoke-RestMethod -Uri $apiUrl -TimeoutSec 20
$relTag = $rel.tag_name
if ($relTag -notmatch '^v([0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9._-]+)?)$') {
    throw "Formato de tag inesperado na Release: $relTag"
}
$pluginVer = $Matches[1]

$zipAsset = $rel.assets | Where-Object { $_.name -like "instalador-chat-captura-IPED-$pluginVer-verificador-*.zip" } | Select-Object -First 1
if (-not $zipAsset) { throw "ZIP do instalador nao encontrado na Release $relTag" }
if ($zipAsset.name -notmatch 'verificador-([0-9]+\.[0-9]+\.[0-9]+)\.zip$') {
    throw "Nao foi possivel inferir versao do Verificador a partir de $($zipAsset.name)"
}
$verifierVer = $Matches[1]

$stageDir = Join-Path ([IO.Path]::GetTempPath()) ('gh-release-dl-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $stageDir | Out-Null

try {
    $zipLocal = Join-Path $stageDir $zipAsset.name
    Write-Host "Baixando $($zipAsset.name) ($($zipAsset.size) bytes)..."
    $curlCmd = Get-Command curl.exe -ErrorAction SilentlyContinue
    if ($curlCmd) {
        & $curlCmd.Source -L --fail --connect-timeout 20 --max-time 600 -sS -o $zipLocal $zipAsset.browser_download_url
        if ($LASTEXITCODE -ne 0) {
            throw "curl.exe falhou ao baixar $($zipAsset.name) (exit=$LASTEXITCODE)"
        }
    } else {
        Invoke-WebRequest -UseBasicParsing -TimeoutSec 300 -Uri $zipAsset.browser_download_url -OutFile $zipLocal
    }
    $actualSize = (Get-Item -LiteralPath $zipLocal).Length
    if ([long]$actualSize -ne [long]$zipAsset.size) {
        throw "Tamanho divergente para $($zipAsset.name): $actualSize != $($zipAsset.size)"
    }
    $actualZipSha = (Get-FileHash -LiteralPath $zipLocal -Algorithm SHA256).Hash.ToUpperInvariant()
    if ($zipAsset.digest -and $zipAsset.digest -match '^sha256:([0-9a-fA-F]{64})$') {
        $expectedZipSha = $Matches[1].ToUpperInvariant()
        if ($actualZipSha -ne $expectedZipSha) {
            throw "SHA-256 divergente do digest oficial do GitHub: $actualZipSha != $expectedZipSha"
        }
        Write-Host "GITHUB_DIGEST_OK: $actualZipSha"
    }

    if ($VerifySigstore) {
        $ghCmd = Get-Command gh -ErrorAction SilentlyContinue
        if (-not $ghCmd) {
            throw 'CLI gh nao encontrado no PATH para verificar atestado Sigstore.'
        }
        & gh attestation verify $zipLocal --repo $GitHubRepo
        if ($LASTEXITCODE -ne 0) { throw "Falha na verificacao Sigstore de $($zipAsset.name)" }
        Write-Host 'SIGSTORE_ATTESTATION_OK'
    }

    $extractDir = Join-Path $stageDir 'extracted'
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [IO.Compression.ZipFile]::ExtractToDirectory($zipLocal, $extractDir)

    $instDir = Join-Path $extractDir 'instalar_captura_chat_zap'
    $batPath = Join-Path $instDir 'instalar_captura_chat_zap.bat'
    $batText = [IO.File]::ReadAllText($batPath)
    if ($batText -notmatch 'EXPECTED_PLUGIN_SHA256=([A-F0-9]{64})') { throw 'EXPECTED_PLUGIN_SHA256 ausente no .bat extraido' }
    $expPluginSha = $Matches[1]
    if ($batText -notmatch 'EXPECTED_VERIFIER_JAR_SHA256=([A-F0-9]{64})') { throw 'EXPECTED_VERIFIER_JAR_SHA256 ausente no .bat extraido' }
    $expJarSha = $Matches[1]
    if ($batText -notmatch 'EXPECTED_VERIFIER_EXE_SHA256=([A-F0-9]{64})') { throw 'EXPECTED_VERIFIER_EXE_SHA256 ausente no .bat extraido' }
    $expExeSha = $Matches[1]

    $pJar = Join-Path $instDir 'iped-viewers-impl-4.3.1.jar'
    $vJar = Join-Path $instDir 'integridade-verifier.jar'
    $vExe = Join-Path $instDir 'integridade-verifier.exe'

    $deployParams = @{
        RepoRoot                   = $repoRoot
        PluginJar                  = $pJar
        VerifierJar                = $vJar
        VerifierExe                = $vExe
        PluginVersion              = $pluginVer
        VerifierVersion            = $verifierVer
        ExpectedPluginSha256       = $expPluginSha
        ExpectedVerifierJarSha256  = $expJarSha
        ExpectedVerifierExeSha256  = $expExeSha
        TargetsSemicolonList       = $Targets
        DryRun                     = $DryRun
    }
    & (Join-Path $repoRoot '.gitea\scripts\Invoke-LocalIpedDeploy.ps1') @deployParams

    if ($SyncGit -and -not $DryRun) {
        Push-Location $repoRoot
        try {
            & git fetch github main --tags
            if ($LASTEXITCODE -ne 0) { throw 'git fetch github falhou' }
            Write-Host 'GIT_FETCH_GITHUB_OK'
        } finally {
            Pop-Location
        }
    }

    Write-Host "GITHUB_RELEASE_LOCAL_INSTALL_COMPLETE=PASS ($relTag)"
} finally {
    Remove-Item -LiteralPath $stageDir -Recurse -Force -ErrorAction SilentlyContinue
}
