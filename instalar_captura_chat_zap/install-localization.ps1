param(
    [Parameter(Mandatory = $true)][string]$Jar,
    [Parameter(Mandatory = $true)][string]$ResourceDir,
    [Parameter(Mandatory = $true)][string]$LocaleDir,
    [string]$BackupStamp = ''
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$required = @('iped-viewer-messages.properties', 'iped-viewer-messages_pt_BR.properties')
foreach ($name in $required) {
    $sourcePath = Join-Path $ResourceDir $name
    if (!(Test-Path -LiteralPath $sourcePath)) { throw "Recurso ausente: $sourcePath" }
    $sourceText = [IO.File]::ReadAllText($sourcePath)
    if ($sourceText -notmatch '(?m)^ChatCapturePanel\.EndDateNotAllowed=') {
        throw "Bundle sem as mensagens de validação dos marcadores: $name"
    }
    if ($name -eq 'iped-viewer-messages.properties' -and $sourceText -notmatch '(?m)^HexViewerPlus\.appName=') {
        throw 'Bundle base sem HexViewerPlus.appName'
    }
}

# O JAR vem compilado com os bundles correspondentes. Não reescreva ZIP/JAR em
# PowerShell: valide os bytes e evite corromper offsets usados pelo Java.
$archive = [IO.Compression.ZipFile]::OpenRead($Jar)
try {
    foreach ($name in $required) {
        $entry = $archive.GetEntry($name)
        if (!$entry) { throw "Bundle ausente no JAR compilado: $name" }
        $entryStream = $entry.Open()
        $memory = [IO.MemoryStream]::new()
        try {
            $entryStream.CopyTo($memory)
            $jarBytes = $memory.ToArray()
        } finally {
            $memory.Dispose()
            $entryStream.Dispose()
        }
        $sourceBytes = [IO.File]::ReadAllBytes((Join-Path $ResourceDir $name))
        if ([Convert]::ToBase64String($jarBytes) -cne [Convert]::ToBase64String($sourceBytes)) {
            throw "O bundle no JAR diverge do recurso compilado: $name. Recompile o JAR antes de instalar."
        }
    }
} finally {
    $archive.Dispose()
}

foreach ($name in $required) {
    $target = Join-Path $LocaleDir $name
    $source = Join-Path $ResourceDir $name
    if (!(Test-Path -LiteralPath $target)) { throw "Locale do IPED nao encontrado: $target" }
    $current = [IO.File]::ReadAllText($target)
    $newline = if ($current.Contains("`r`n")) { "`r`n" } else { "`n" }
    $updated = $current
    $changed = $false
    $add = [Collections.Generic.List[string]]::new()
    foreach ($line in [IO.File]::ReadAllLines($source)) {
        if (!$line -or $line.TrimStart().StartsWith('#') -or $line.TrimStart().StartsWith('!') -or !$line.Contains('=')) { continue }
        $key = $line.Substring(0, $line.IndexOf('='))
        $pattern = '(?m)^' + [regex]::Escape($key) + '=[^\r\n]*'
        $m = [regex]::Match($updated, $pattern)
        if ($m.Success) {
            if ($m.Value.EndsWith('\') -or $line.EndsWith('\')) { continue }
            if ($m.Value -cne $line) {
                $updated = $updated.Substring(0, $m.Index) + $line + $updated.Substring($m.Index + $m.Length)
                $changed = $true
            }
        } else {
            $add.Add($line)
        }
    }
    if ($add.Count -gt 0) {
        $trimmed = $updated.TrimEnd("`r", "`n")
        if ($trimmed -match '(?<!\\)(\\\\)*\\$') {
            $trimmed = $trimmed + ' '
        }
        $updated = $trimmed + $newline + ($add -join $newline) + $newline
        $changed = $true
    }
    if ($changed) {
        $stamp = if ([string]::IsNullOrWhiteSpace($BackupStamp)) { Get-Date -Format yyyyMMdd-HHmmss } else { $BackupStamp.Trim() }
        $backup = "$target.bak-$stamp"
        Copy-Item -LiteralPath $target -Destination $backup
        [IO.File]::WriteAllText($target, $updated, [Text.UTF8Encoding]::new($false))
        Write-Host "Localization atualizada com backup: $backup"
    }
}

Write-Host 'Bundles do JAR verificados; idiomas do IPED atualizados sem alterar o JAR.'
