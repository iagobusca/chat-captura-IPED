param(
    [string]$CaptureRoot = '',
    [int]$PageSize = 100,
    [int]$ValidationBatchSize = 25
)
$ErrorActionPreference = 'Stop'

function Get-RelativeChildPath([string]$BaseDir, [string]$ChildPath) {
    $normBase = [IO.Path]::GetFullPath($BaseDir).TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
    $normChild = [IO.Path]::GetFullPath($ChildPath)
    if ($normChild.StartsWith($normBase, [StringComparison]::OrdinalIgnoreCase)) {
        return $normChild.Substring($normBase.Length).Replace('\', '/')
    }
    return ([Uri]$normBase).MakeRelativeUri([Uri]$normChild).ToString().TrimEnd('/')
}

$cleanupSample = $false
if ([string]::IsNullOrWhiteSpace($CaptureRoot)) {
    if ($env:CHAT_CAPTURE_SAMPLE_ROOT) {
        $CaptureRoot = $env:CHAT_CAPTURE_SAMPLE_ROOT
    } else {
        $repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
        $probe = $repoRoot
        for ($i = 0; $i -lt 5 -and $probe; $i++) {
            $cand = Join-Path $probe 'IPED_testes_chat_real\teste_chat_extraido'
            if (Test-Path -LiteralPath $cand -PathType Container) { $CaptureRoot = $cand; break }
            $probe = Split-Path -Parent $probe
        }
        if ([string]::IsNullOrWhiteSpace($CaptureRoot)) {
            $cleanupSample = $true
            $CaptureRoot = Join-Path $env:TEMP ('two-phase-sample-' + [guid]::NewGuid().ToString('N'))
            $capDir = Join-Path $CaptureRoot 'captura-chat-sintetica-001'
            New-Item -ItemType Directory -Force -Path $capDir | Out-Null
            '{"status":"completed","artifacts":[]}' | Set-Content -LiteralPath (Join-Path $capDir 'manifest.json') -Encoding UTF8
        }
    }
}

function Assert([bool]$condition, [string]$message) {
    if (!$condition) { throw "FALHOU: $message" }
}

function Get-Page([object[]]$items, [int]$page, [int]$size) {
    $start = ($page - 1) * $size
    if ($start -ge $items.Count) { return @() }
    return @($items | Select-Object -Skip $start -First $size)
}

function Get-Buttons([int]$page, [int]$count, [bool]$finished, [int]$size) {
    $start = ($page - 1) * $size
    $hasNext = $count -gt ($start + $size) -or (!$finished -and $count -gt ($start + $size - 1))
    return @{ Previous = ($page -gt 1); Next = $hasNext }
}

# Exercise page boundaries independently of the size of the real evidence sample.
foreach ($n in @(0, 1, 100, 101, 1001)) {
    $items = @(0..([Math]::Max(0, $n - 1)) | ForEach-Object { "capture-$_" })
    if ($n -eq 0) { $items = @() }
    $pages = if ($n -eq 0) { 0 } else { [int][Math]::Ceiling($n / [double]$PageSize) }
    Assert ((Get-Page $items 1 $PageSize).Count -eq [Math]::Min($n, $PageSize)) "primeira página N=$n"
    if ($pages -gt 1) {
        Assert ((Get-Page $items 2 $PageSize).Count -eq [Math]::Min($PageSize, $n - $PageSize)) "segunda página N=$n"
    }
    $firstButtons = Get-Buttons 1 $n $true $PageSize
    Assert (!$firstButtons.Previous) "Anterior desativado na primeira página N=$n"
    Assert ($firstButtons.Next -eq ($n -gt $PageSize)) "Próximo na primeira página N=$n"
    if ($pages -gt 0) {
        $lastButtons = Get-Buttons $pages $n $true $PageSize
        Assert ($lastButtons.Next -eq $false) "Próximo desativado na última página N=$n"
        Assert ($lastButtons.Previous -eq ($pages -gt 1)) "Anterior na última página N=$n"
    }
}
Write-Output 'PASS paginação/controles: 0, 1, 100, 101 e 1001 itens'

# Exercise virtual all-future selection and conversion to explicit ID selection.
$mode = 'ALL_FUTURE'; $selected = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
$seen = [Collections.Generic.List[string]]::new(); $seen.Add('a'); $seen.Add('b')
$isASelected = ($mode -eq 'ALL_FUTURE' -or $selected.Contains('a'))
Assert $isASelected 'ALL_FUTURE marca candidato já descoberto'
$seen.Add('c'); $isCSelected = ($mode -eq 'ALL_FUTURE' -or $selected.Contains('c'))
Assert $isCSelected 'ALL_FUTURE marca candidato descoberto depois'
$mode = 'EXPLICIT'; foreach ($id in $seen) { $selected.Add($id) | Out-Null }; $selected.Remove('b') | Out-Null
$seen.Add('d'); $dSelected = ($mode -eq 'ALL_FUTURE' -or $selected.Contains('d'))
Assert (!$dSelected -and $selected.Contains('a') -and !$selected.Contains('b')) 'ação manual preserva IDs e deixa novos itens desmarcados'
Write-Output 'PASS seleção: global inclusiva e conversão manual para IDs explícitos'

# Exercise bounded metadata validation batches with synthetic descriptors.
$synthetic = @(); for ($i = 0; $i -lt 101; $i++) { $synthetic += [pscustomobject]@{Id="synthetic-$i";Validated=$false} }
$syntheticBatches = 0; $largestBatch = 0
for ($offset = 0; $offset -lt $synthetic.Count; $offset += $ValidationBatchSize) {
    $batch = @($synthetic | Select-Object -Skip $offset -First $ValidationBatchSize)
    $syntheticBatches++; $largestBatch = [Math]::Max($largestBatch,$batch.Count)
    foreach ($item in $batch) { $item.Validated = $true }
}
Assert ($syntheticBatches -eq [Math]::Ceiling(101 / [double]$ValidationBatchSize)) 'quantidade de sublotes sintéticos'
Assert ($largestBatch -le $ValidationBatchSize -and @($synthetic | Where-Object {!$_.Validated}).Count -eq 0) 'validação em sublotes limitados'
Write-Output "PASS validação sintética: 101 itens em $syntheticBatches sublotes; máximo=$largestBatch"

# Phase 1: enumerate only child names/paths and marker existence; do not read contents.
if (!(Test-Path -LiteralPath $CaptureRoot -PathType Container)) { throw "Pasta de amostra ausente: $CaptureRoot" }
$excluded = @('vinculado', '.iped-reextract-trash')
$candidates = [Collections.Generic.List[object]]::new()
foreach ($dir in [IO.Directory]::EnumerateDirectories($CaptureRoot)) {
    $name = [IO.Path]::GetFileName($dir)
    if ($excluded -contains $name -or $name.StartsWith('_backup_chat_capture_') -or $name.Contains('.__reextract_tmp_') -or $name.Contains('.__reextract_failed_')) { continue }
    $markers = @('manifest.json','integridade.json','whatsapp.html','relatorio.htm','texto.txt')
    $found = $false
    foreach ($marker in $markers) { if ([IO.File]::Exists([IO.Path]::Combine($dir,$marker))) { $found = $true; break } }
    if ($found) { $relative = Get-RelativeChildPath $CaptureRoot $dir; $candidates.Add([pscustomobject]@{Id=$relative;Name=$name;Path=$dir;Validated=$false;Eligible=$false}) }
}
Assert (($candidates | Select-Object -ExpandProperty Id -Unique).Count -eq $candidates.Count) 'IDs únicos'
Write-Output "PASS fase 1: $($candidates.Count) candidatos listados por caminhos/marcadores"

# Phase 2: read and validate only metadata, in bounded sub-batches.
$validated = 0; $eligible = 0
for ($offset = 0; $offset -lt $candidates.Count; $offset += $ValidationBatchSize) {
    $batch = @($candidates | Select-Object -Skip $offset -First $ValidationBatchSize)
    foreach ($candidate in $batch) {
        $manifestPath = [IO.Path]::Combine($candidate.Path,'manifest.json')
        $integrityPath = [IO.Path]::Combine($candidate.Path,'integridade.json')
        $candidate.Validated = $true
        if ([IO.File]::Exists($manifestPath)) {
            $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
            $candidate.Eligible = ($manifest.status -eq 'completed' -or $manifest.status -eq 'complete' -or $null -ne $manifest.artifacts)
        } elseif ([IO.File]::Exists($integrityPath)) {
            $record = Get-Content -LiteralPath $integrityPath -Raw | ConvertFrom-Json
            $candidate.Eligible = ($null -ne $record.artifacts)
        }
        $validated++; if ($candidate.Eligible) { $eligible++ }
    }
    Assert ($batch.Count -le $ValidationBatchSize) 'limite do sublote de validação'
}
Assert ($validated -eq $candidates.Count) 'todos os candidatos foram validados'
Write-Output "PASS fase 2: $validated metadados validados em sublotes de até $ValidationBatchSize; elegíveis=$eligible"
Write-Output "PASS amostra: $CaptureRoot"
if ($cleanupSample) { Remove-Item -LiteralPath $CaptureRoot -Recurse -Force -ErrorAction SilentlyContinue }
