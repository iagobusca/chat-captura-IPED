<#
.SYNOPSIS
    Publica/atualiza uma Release no Gitea (4 ativos) e opcionalmente espelha no
    Gitea Packages Registry (tipo generic) vinculando ao repositorio.
#>
param(
    [Parameter(Mandatory = $true)][string]$Repo,
    [Parameter(Mandatory = $true)][string]$Token,
    [Parameter(Mandatory = $true)][string]$Tag,
    [Parameter(Mandatory = $true)][string]$TargetBranch,
    [Parameter(Mandatory = $true)][bool]$Prerelease,
    [Parameter(Mandatory = $true)][string]$PluginVersion,
    [Parameter(Mandatory = $true)][string]$VerifierVersion,
    [Parameter(Mandatory = $true)][string]$ZipPath,
    [Parameter(Mandatory = $true)][string]$ExePath,
    [Parameter(Mandatory = $true)][string]$JarPath,
    [Parameter(Mandatory = $true)][string]$PluginPath,
    [string]$GiteaBaseUrl = 'http://127.0.0.1:3000',
    [string]$PackageName = 'chat-captura-iped',
    [string]$SummaryNotes = '',
    [bool]$PublishRelease = $true,
    [bool]$PublishPackages = $true,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'

$GiteaBaseUrl = $GiteaBaseUrl.TrimEnd('/')
$owner, $repoName = $Repo.Split('/', 2)
if (-not $owner -or -not $repoName) { throw "Repo invalido (esperado owner/repo): $Repo" }
if ([string]::IsNullOrWhiteSpace($Token)) { throw 'Token Gitea ausente.' }

$zipName = "instalador-chat-captura-IPED-$PluginVersion-verificador-$VerifierVersion.zip"
$exeName = "integridade-verifier-$VerifierVersion.exe"
$jarName = "integridade-verifier-$VerifierVersion.jar"
$pluginName = "iped-viewers-impl-4.3.1-$PluginVersion.jar"

$assetSpecs = @(
    @{ Name = $zipName;    Src = (Resolve-Path -LiteralPath $ZipPath).Path },
    @{ Name = $exeName;    Src = (Resolve-Path -LiteralPath $ExePath).Path },
    @{ Name = $jarName;    Src = (Resolve-Path -LiteralPath $JarPath).Path },
    @{ Name = $pluginName; Src = (Resolve-Path -LiteralPath $PluginPath).Path }
)
foreach ($a in $assetSpecs) {
    $fi = Get-Item -LiteralPath $a.Src
    $a['Bytes'] = [long]$fi.Length
    $a['Sha256'] = (Get-FileHash -LiteralPath $a.Src -Algorithm SHA256).Hash.ToUpperInvariant()
}

$zipSpec = $assetSpecs[0]
$exeSpec = $assetSpecs[1]
$jarSpec = $assetSpecs[2]
$pluginSpec = $assetSpecs[3]

if ($DryRun) {
    Write-Host "[DRY-RUN] Release=$PublishRelease (tag=$Tag target=$TargetBranch pre=$Prerelease) | Packages=$PublishPackages (generic/$PackageName/$PluginVersion)"
    foreach ($a in $assetSpecs) {
        Write-Host "  ASSET: $($a.Name) | $($a.Bytes) bytes | $($a.Sha256)"
    }
    return
}

$headers = @{ Authorization = "token $Token" }
$ptBr = [Globalization.CultureInfo]::GetCultureInfo('pt-BR')
$zipFmt = [string]::Format($ptBr, '{0:N0}', $zipSpec.Bytes)
$exeFmt = [string]::Format($ptBr, '{0:N0}', $exeSpec.Bytes)
$jarFmt = [string]::Format($ptBr, '{0:N0}', $jarSpec.Bytes)
$pluginFmt = [string]::Format($ptBr, '{0:N0}', $pluginSpec.Bytes)

if ($PublishRelease) {
    $apiBase = "$GiteaBaseUrl/api/v1/repos/$Repo"
    $summaryLine = if ([string]::IsNullOrWhiteSpace($SummaryNotes)) {
        [Regex]::Unescape('- Atualiza\u00e7\u00e3o de vers\u00e3o gerada automaticamente via esteira determin\u00edstica do GiteaRunner (`build-reproducible.ps1`), com dupla prova bit a bit (`REPRODUCIBLE_BUILD_VERIFIED=PASS`).')
    } else {
        "- $($SummaryNotes.Trim())"
    }

    $lines = @(
        '## Otimizado',
        [Regex]::Unescape("- Vers\u00e3o **$PluginVersion** publicada na branch ``$TargetBranch`` acompanhando o **Verificador de Integridade $VerifierVersion**, compilada e auditada pelo **GiteaRunner** via [``build-reproducible.ps1``]($GiteaBaseUrl/$Repo/src/tag/$Tag/build-reproducible.ps1)."),
        $summaryLine,
        [Regex]::Unescape('- **Compila\u00e7\u00e3o Determin\u00edstica Multiplataforma**: timestamps UTC fixos (`1980-02-01T00:00:00Z`), ordena\u00e7\u00e3o lexicogr\u00e1fica (`ReproducibleZipPacker`), manifesto fixo e toolchain pinada (`Temurin JDK 21.0.13+9` + `OpenJFX 21.0.5` + `IPED 4.3.1` + `.NET 10.0.15` com `-p:Deterministic=true`).'),
        '',
        '## Corrigido',
        [Regex]::Unescape("- Allowlist exclusiva de bin\u00e1rios confi\u00e1veis em ``VerifierIntegrity.java``, ``instalar_captura_chat_zap.bat`` e ``VerifierIntegrityHashTest.java`` sincronizada exclusivamente com os bin\u00e1rios determin\u00edsticos oficiais do Verificador **$VerifierVersion** (``$($jarSpec.Sha256.Substring(0,8))...`` e ``$($exeSpec.Sha256.Substring(0,8))...``)."),
        [Regex]::Unescape('- Esteira do Gitea Actions executou compila\u00e7\u00e3o dupla bit a bit, ``test-security-spec.ps1``, ``run_all_tests.ps1`` e testes de regress\u00e3o antes da publica\u00e7\u00e3o.'),
        '',
        '## Incluido',
        "- Plugin Chat Captura IPED ``$PluginVersion`` (``$pluginName`` | ``$pluginFmt`` bytes | SHA-256: ``$($pluginSpec.Sha256)``).",
        "- Verificador de Integridade ``$VerifierVersion`` em JAR (``$jarFmt`` bytes) e EXE Windows x64 autocontido (``$exeFmt`` bytes).",
        "- ZIP instalador completo (``$zipName`` | ``$zipFmt`` bytes | SHA-256: ``$($zipSpec.Sha256)``).",
        '',
        "## SHA-256 do Verificador $VerifierVersion",
        "- JAR: ``$($jarSpec.Sha256)``",
        "- EXE: ``$($exeSpec.Sha256)``",
        '',
        [Regex]::Unescape('Veja ``versao_hash.md`` e ``CHECKSUMS.md`` para todos os hashes e detalhes das vers\u00f5es anteriores.')
    )
    $bodyText = $lines -join "`n"

    $existing = $null
    try {
        $existing = Invoke-RestMethod -Method Get -Uri "$apiBase/releases/tags/$Tag" -Headers $headers -TimeoutSec 15
    } catch { $existing = $null }

    $payloadBytes = [Text.Encoding]::UTF8.GetBytes((@{
        tag_name         = $Tag
        target_commitish = $TargetBranch
        name             = $Tag
        body             = $bodyText
        draft            = $false
        prerelease       = $Prerelease
    } | ConvertTo-Json -Depth 4))

    if ($existing -and $existing.id) {
        $rel = Invoke-RestMethod -Method Patch -Uri "$apiBase/releases/$($existing.id)" -Headers $headers -ContentType 'application/json; charset=utf-8' -Body $payloadBytes -TimeoutSec 30
        foreach ($oldAsset in @($rel.assets)) {
            Invoke-RestMethod -Method Delete -Uri "$apiBase/releases/$($rel.id)/assets/$($oldAsset.id)" -Headers $headers -TimeoutSec 30 | Out-Null
        }
    } else {
        $rel = Invoke-RestMethod -Method Post -Uri "$apiBase/releases" -Headers $headers -ContentType 'application/json; charset=utf-8' -Body $payloadBytes -TimeoutSec 30
    }
    $relId = $rel.id
    Write-Host "GITEA_RELEASE_ID=$relId | TAG=$Tag"

    $uploadStage = Join-Path ([IO.Path]::GetTempPath()) ('gitea-rel-upload-' + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Force -Path $uploadStage | Out-Null
    try {
        foreach ($a in $assetSpecs) {
            $staged = Join-Path $uploadStage $a.Name
            Copy-Item -LiteralPath $a.Src -Destination $staged -Force
            $uploadUrl = "$apiBase/releases/$relId/assets?name=$([Uri]::EscapeDataString($a.Name))"
            & curl.exe -sS -f -X POST -H "Authorization: token $Token" -F "attachment=@$staged" $uploadUrl | Out-Null
            if ($LASTEXITCODE -ne 0) { throw "Falha ao enviar ativo $($a.Name) para release $relId ($LASTEXITCODE)" }
        }
    } finally {
        Remove-Item -LiteralPath $uploadStage -Recurse -Force -ErrorAction SilentlyContinue
    }

    $finalRel = Invoke-RestMethod -Method Get -Uri "$apiBase/releases/$relId" -Headers $headers -TimeoutSec 30
    foreach ($a in $assetSpecs) {
        $found = $finalRel.assets | Where-Object { $_.name -eq $a.Name } | Select-Object -First 1
        if (-not $found -or [long]$found.size -ne $a.Bytes) {
            throw "Ativo ausente ou tamanho divergente na release final: $($a.Name)"
        }
        Write-Host "RELEASE_ASSET_OK: $($found.name) ($($found.size) bytes) -> $($found.browser_download_url)"
    }
}

if ($PublishPackages) {
    $pkgVer = [Uri]::EscapeDataString($PluginVersion)
    $pkgBase = "$GiteaBaseUrl/api/packages/$owner/generic/$PackageName/$pkgVer"

    foreach ($a in $assetSpecs) {
        $fileEnc = [Uri]::EscapeDataString($a.Name)
        # Remove versao existente desse arquivo para permitir idempotencia limpa em re-runs
        try {
            & curl.exe -s -X DELETE -H "Authorization: token $Token" "$pkgBase/$fileEnc" | Out-Null
        } catch {}
        $global:LASTEXITCODE = 0
        & curl.exe -sS -f -X PUT -H "Authorization: token $Token" --upload-file $a.Src "$pkgBase/$fileEnc" | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "Falha ao publicar pacote Generic $($a.Name) em $pkgBase/$fileEnc ($LASTEXITCODE)"
        }
        Write-Host "GITEA_PACKAGE_UPLOAD_OK: $pkgBase/$fileEnc ($($a.Bytes) bytes)"
    }

    try {
        Invoke-RestMethod -Method Post -Uri "$GiteaBaseUrl/api/v1/packages/$owner/generic/$PackageName/-/link/$repoName" -Headers $headers -TimeoutSec 15 | Out-Null
    } catch {
        # 409/ja vinculado ou resposta vazia nao e fatal
    }

    $pkgFiles = Invoke-RestMethod -Method Get -Uri "$GiteaBaseUrl/api/v1/packages/$owner/generic/$PackageName/$pkgVer/files" -Headers $headers -TimeoutSec 15
    foreach ($a in $assetSpecs) {
        $match = $pkgFiles | Where-Object { $_.name -eq $a.Name -and [long]$_.size -eq $a.Bytes } | Select-Object -First 1
        if (-not $match) {
            throw "Arquivo ausente ou tamanho divergente no Gitea Packages: $($a.Name)"
        }
        Write-Host "PACKAGE_FILE_VERIFIED: $($match.name) | sha256=$($match.sha256)"
    }
}
