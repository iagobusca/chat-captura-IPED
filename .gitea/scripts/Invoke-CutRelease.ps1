<#
.SYNOPSIS
    Fluxo 1-click para cortar nova versao no GiteaRunner (reutiliza
    scripts/ci/Invoke-CutReleaseCore.ps1), publica Release + Gitea Packages,
    atualiza pastas IPED locais e espelha opcionalmente para o GitHub.
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
    [bool]$MirrorToGitHub = $false,
    [string]$GitHubRemoteUrl = 'https://github.com/iagobusca/chat-captura-IPED.git',
    [bool]$AllowSameVersion = $false,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'

$tagName = "v$($PluginVersion.Trim())"

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

$metaJson = Join-Path ([IO.Path]::GetTempPath()) ('gitea-cut-meta-' + [Guid]::NewGuid().ToString('N') + '.json')
$cachedJfx = Get-ChildItem -LiteralPath (Join-Path $ReproCacheDir 'openjfx-21.0.5-windows-x64') -Filter 'javafx.web.jar' -Recurse -File -ErrorAction SilentlyContinue | Select-Object -First 1

$coreParams = @{
    RepoRoot         = $RepoRoot
    RepoUrlBase      = $GiteaBaseUrl
    RepoSlug         = $RepoSlug
    PluginVersion    = $PluginVersion
    VerifierVersion  = $VerifierVersion
    Channel          = $Channel
    SummaryNotes     = $SummaryNotes
    CacheDir         = $ReproCacheDir
    IpedHome         = $IpedHome
    TestMode         = 'full-local'
    OutMetadataJson  = $metaJson
    AllowSameVersion = $AllowSameVersion
    Offline          = [bool]$cachedJfx
    DownloadDeps     = -not [bool]$cachedJfx
}

try {
    & (Join-Path $RepoRoot 'scripts\ci\Invoke-CutReleaseCore.ps1') @coreParams
    $meta = Get-Content -LiteralPath $metaJson -Raw | ConvertFrom-Json

    if ($DryRun) {
        Write-Host "=== DRY-RUN CONCLUIDO SEM ALTERAR GIT/RELEASES/PASTAS/GITHUB ==="
        if ($DeployLocalIped -and $LocalIpedTargets) {
            & (Join-Path $RepoRoot '.gitea\scripts\Invoke-LocalIpedDeploy.ps1') `
                -RepoRoot $RepoRoot -PluginJar $meta.pluginPath -VerifierJar $meta.jarPath -VerifierExe $meta.exePath `
                -PluginVersion $meta.pluginVersion -VerifierVersion $meta.verifierVersion `
                -ExpectedPluginSha256 $meta.pluginSha -ExpectedVerifierJarSha256 $meta.jarSha -ExpectedVerifierExeSha256 $meta.exeSha `
                -TargetsSemicolonList $LocalIpedTargets -JavaExe (Join-Path $meta.activeJava21 'bin\java.exe') -DryRun
        }
        Write-Host "DRY_RUN_CUT_RELEASE=PASS ($($meta.tag) | ZIP=$($meta.zipSha) | mirrorGitHub=$MirrorToGitHub)"
        return
    }

    Write-Host "=== Etapa 6: Commit, Branch, Tag, Push e Publicacao no Gitea ==="
    Push-Location $RepoRoot
    try {
        Remove-Item -LiteralPath (Join-Path $RepoRoot 'tmp') -Recurse -Force -ErrorAction SilentlyContinue
        & git config user.name 'GiteaRunner Bot'
        & git config user.email 'gitea-runner@local'

        function Clear-StaleGitIndexLock {
            $lockFile = Join-Path $RepoRoot '.git\index.lock'
            for ($w = 0; $w -lt 10 -and (Test-Path -LiteralPath $lockFile); $w++) {
                Start-Sleep -Milliseconds 300
            }
            if (Test-Path -LiteralPath $lockFile) {
                Remove-Item -LiteralPath $lockFile -Force -ErrorAction SilentlyContinue
            }
        }

        $releaseBranch = "release/$($meta.tag)"
        Clear-StaleGitIndexLock
        & git checkout -B $releaseBranch
        if ($LASTEXITCODE -ne 0) { throw "git checkout -B $releaseBranch falhou" }
        Clear-StaleGitIndexLock
        & git add -A
        if ($LASTEXITCODE -ne 0) { throw 'git add -A falhou' }
        Clear-StaleGitIndexLock
        & git commit -m "release($($meta.tag)): corte automatizado plugin $($meta.pluginVersion) / verificador $($meta.verifierVersion)"
        if ($LASTEXITCODE -ne 0) { throw 'git commit falhou' }

        $b64 = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("iago:$Token"))
        $remoteUrl = "$($GiteaBaseUrl.TrimEnd('/'))/$RepoSlug.git"
        & git remote set-url origin $remoteUrl
        & git fetch origin $meta.targetBranch
        if ($LASTEXITCODE -eq 0) {
            & git merge-base --is-ancestor FETCH_HEAD $releaseBranch
            if ($LASTEXITCODE -eq 0) {
                Clear-StaleGitIndexLock
                & git checkout -B $meta.targetBranch FETCH_HEAD
                if ($LASTEXITCODE -ne 0) { throw "git checkout $($meta.targetBranch) falhou" }
                Clear-StaleGitIndexLock
                & git merge --ff-only $releaseBranch
                if ($LASTEXITCODE -ne 0) { throw "git merge --ff-only $releaseBranch falhou" }
            } else {
                Clear-StaleGitIndexLock
                & git checkout -B $meta.targetBranch $releaseBranch
                if ($LASTEXITCODE -ne 0) { throw "git checkout -B $($meta.targetBranch) falhou" }
                Clear-StaleGitIndexLock
                & git merge --no-edit -s ours FETCH_HEAD -m "chore: reconciliar $($meta.targetBranch) antes de $($meta.tag)"
                if ($LASTEXITCODE -ne 0) { throw "git merge -s ours em $($meta.targetBranch) falhou" }
            }
        } else {
            Clear-StaleGitIndexLock
            & git checkout -B $meta.targetBranch
        }

        & git tag -f -a $meta.tag -m "Release $($meta.tag) ($Channel) - Plugin $($meta.pluginVersion) / Verificador $($meta.verifierVersion)"
        if ($LASTEXITCODE -ne 0) { throw "git tag $($meta.tag) falhou" }

        & git -c "http.extraHeader=Authorization: Basic $b64" push origin $releaseBranch $meta.targetBranch "refs/tags/$($meta.tag)"
        if ($LASTEXITCODE -ne 0) { throw 'git push origin falhou' }

        if ($MirrorToGitHub) {
            Write-Host "=== Espelhando $($meta.targetBranch) e tag $($meta.tag) para GitHub ==="
            $hasGhRemote = (& git remote) -contains 'github'
            if (-not $hasGhRemote) {
                & git remote add github $GitHubRemoteUrl
            } else {
                & git remote set-url github $GitHubRemoteUrl
            }
            & git push github $meta.targetBranch "refs/tags/$($meta.tag)"
            if ($LASTEXITCODE -ne 0) { throw "Espelhamento para GitHub falhou ($LASTEXITCODE). Verifique credenciais no Windows Credential Manager." }
            Write-Host "GITHUB_MIRROR_PUSH=PASS ($($meta.targetBranch) + $($meta.tag))"
        }
    } finally {
        Pop-Location
    }

    & (Join-Path $RepoRoot '.gitea\scripts\Publish-GiteaReleaseAndPackages.ps1') `
        -Repo $RepoSlug -Token $Token -Tag $meta.tag -TargetBranch $meta.targetBranch -Prerelease ([bool]$meta.isPrerelease) `
        -PluginVersion $meta.pluginVersion -VerifierVersion $meta.verifierVersion `
        -ZipPath $meta.zipPath -ExePath $meta.exePath -JarPath $meta.jarPath -PluginPath $meta.pluginPath `
        -GiteaBaseUrl $GiteaBaseUrl -SummaryNotes $SummaryNotes `
        -PublishRelease $true -PublishPackages $PublishGiteaPackages

    if ($DeployLocalIped) {
        & (Join-Path $RepoRoot '.gitea\scripts\Invoke-LocalIpedDeploy.ps1') `
            -RepoRoot $RepoRoot -PluginJar $meta.pluginPath -VerifierJar $meta.jarPath -VerifierExe $meta.exePath `
            -PluginVersion $meta.pluginVersion -VerifierVersion $meta.verifierVersion `
            -ExpectedPluginSha256 $meta.pluginSha -ExpectedVerifierJarSha256 $meta.jarSha -ExpectedVerifierExeSha256 $meta.exeSha `
            -TargetsSemicolonList $LocalIpedTargets -JavaExe (Join-Path $meta.activeJava21 'bin\java.exe')
    }

    Write-Host "CUT_RELEASE_COMPLETE=PASS ($($meta.tag))"
} finally {
    Remove-Item -LiteralPath $metaJson -Force -ErrorAction SilentlyContinue
}
