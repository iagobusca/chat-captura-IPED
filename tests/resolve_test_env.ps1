# Shared dynamic environment resolution helpers for test scripts.
function Get-JavaMajorFromHome {
    param([Parameter(Mandatory = $true)][string]$HomeDir)
    $releaseFile = Join-Path $HomeDir 'release'
    if (Test-Path -LiteralPath $releaseFile) {
        $line = Get-Content -LiteralPath $releaseFile -ErrorAction SilentlyContinue |
            Where-Object { $_ -match '^JAVA_VERSION=' } | Select-Object -First 1
        if ($line -match 'JAVA_VERSION="1\.(\d+)') { return [int]$Matches[1] }
        if ($line -match 'JAVA_VERSION="(\d+)') { return [int]$Matches[1] }
    }
    return 0
}

function Resolve-JdkHome {
    param(
        [string]$Explicit = '',
        [string[]]$EnvNames = @('JAVA_HOME'),
        [int[]]$MajorVersions = @(),
        [string]$RequiredTool = 'bin\javac.exe'
    )
    if ($Explicit) {
        if (Test-Path -LiteralPath (Join-Path $Explicit $RequiredTool)) { return $Explicit }
        throw "JDK informado nao possui $RequiredTool : $Explicit"
    }
    foreach ($envName in ($EnvNames | Where-Object { $_ -ne 'JAVA_HOME' })) {
        $val = [Environment]::GetEnvironmentVariable($envName)
        if ($val -and (Test-Path -LiteralPath (Join-Path $val $RequiredTool)) -and (Test-Path -LiteralPath (Join-Path $val 'bin\javac.exe'))) {
            if (!$MajorVersions.Count -or ($MajorVersions -contains (Get-JavaMajorFromHome $val))) {
                return $val
            }
        }
    }
    $searchRoots = @($env:ProgramFiles, ${env:ProgramFiles(x86)}, (Join-Path $env:LOCALAPPDATA 'Programs'), (Join-Path $env:USERPROFILE '.jdks')) |
        Where-Object { $_ -and (Test-Path -LiteralPath $_) } | Select-Object -Unique
    $vendors = @('Eclipse Adoptium', 'Java', 'Microsoft', 'Zulu', 'BellSoft', 'Amazon Corretto', 'Temurin', '')
    $candidates = New-Object System.Collections.Generic.List[object]
    foreach ($root in $searchRoots) {
        foreach ($vendor in $vendors) {
            $dir = if ($vendor) { Join-Path $root $vendor } else { $root }
            if (!(Test-Path -LiteralPath $dir)) { continue }
            foreach ($child in (Get-ChildItem -LiteralPath $dir -Directory -ErrorAction SilentlyContinue)) {
                if (!(Test-Path -LiteralPath (Join-Path $child.FullName $RequiredTool)) -or !(Test-Path -LiteralPath (Join-Path $child.FullName 'bin\javac.exe'))) { continue }
                $major = Get-JavaMajorFromHome $child.FullName
                if (!$MajorVersions.Count -or ($MajorVersions -contains $major)) {
                    $rank = if ($MajorVersions.Count) { [Array]::IndexOf($MajorVersions, $major) } else { 0 }
                    $candidates.Add([pscustomobject]@{ Rank = $rank; Major = $major; Name = $child.Name; Path = $child.FullName })
                }
            }
        }
    }
    $picked = $candidates | Sort-Object Rank, @{ Expression = 'Name'; Descending = $true } | Select-Object -First 1
    if ($picked) { return $picked.Path }
    if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME $RequiredTool)) -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) {
        if (!$MajorVersions.Count -or ($MajorVersions -contains (Get-JavaMajorFromHome $env:JAVA_HOME))) {
            return $env:JAVA_HOME
        }
    }
    foreach ($cmdName in @('javac.exe', 'java.exe')) {
        $cmd = Get-Command $cmdName -ErrorAction SilentlyContinue
        if ($cmd -and $cmd.Source) {
            $candidate = Split-Path -Parent (Split-Path -Parent $cmd.Source)
            if (Test-Path -LiteralPath (Join-Path $candidate $RequiredTool)) {
                $major = Get-JavaMajorFromHome $candidate
                if (!$MajorVersions.Count -or ($MajorVersions -contains $major)) { return $candidate }
            }
        }
    }
    throw "JDK compativel nao encontrado dinamicamente (versoes=$($MajorVersions -join ','); ferramenta=$RequiredTool)."
}

function Resolve-WorkspaceIpedRoot {
    param(
        [string]$Explicit = '',
        [Parameter(Mandatory = $true)][string]$RepoRoot,
        [switch]$PreferTestIped
    )
    if ($Explicit) {
        if (Test-Path -LiteralPath (Join-Path $Explicit 'lib')) { return $Explicit }
        throw "Pasta IPED invalida: $Explicit"
    }
    if ($env:IPED_HOME -and (Test-Path -LiteralPath (Join-Path $env:IPED_HOME 'lib'))) { return $env:IPED_HOME }
    $probeDirs = New-Object System.Collections.Generic.List[string]
    $cur = $RepoRoot
    for ($i = 0; $i -lt 5 -and $cur; $i++) {
        if ($PreferTestIped) {
            $probeDirs.Add((Join-Path $cur 'IPED_testes_chat_real\iped'))
        }
        $probeDirs.Add((Join-Path $cur 'iped-4.3.1'))
        if (Test-Path -LiteralPath $cur) {
            foreach ($d in (Get-ChildItem -LiteralPath $cur -Directory -Filter 'iped-*' -ErrorAction SilentlyContinue | Sort-Object Name -Descending)) {
                $probeDirs.Add($d.FullName)
            }
        }
        if (!$PreferTestIped) {
            $probeDirs.Add((Join-Path $cur 'IPED_testes_chat_real\iped'))
        }
        $cur = Split-Path -Parent $cur
    }
    foreach ($p in $probeDirs) {
        if ($p -and (Test-Path -LiteralPath (Join-Path $p 'lib'))) { return $p }
    }
    throw 'IPED_HOME / pasta iped-4.3.1 nao encontrada em torno do repositorio.'
}

function Resolve-JavaFxLib {
    param(
        [string]$Explicit = '',
        [Parameter(Mandatory = $true)][string]$RepoRoot,
        [string]$IpedRoot = ''
    )
    if ($Explicit) {
        if (Test-Path -LiteralPath $Explicit) { return $Explicit }
        throw "JavaFX lib nao encontrado: $Explicit"
    }
    foreach ($envCandidate in @($env:JAVAFX_LIB, $(if ($env:JAVAFX_HOME) { Join-Path $env:JAVAFX_HOME 'lib' }), $env:PATH_TO_FX)) {
        if ($envCandidate -and (Test-Path -LiteralPath $envCandidate)) { return $envCandidate }
    }
    $searchDirs = @($RepoRoot, (Split-Path -Parent $RepoRoot), (Split-Path -Parent (Split-Path -Parent $RepoRoot)), (Join-Path $env:ProgramFiles 'Java')) |
        Where-Object { $_ -and (Test-Path -LiteralPath $_) } | Select-Object -Unique
    foreach ($base in $searchDirs) {
        $found = Get-ChildItem -LiteralPath $base -Directory -Filter 'javafx-sdk*' -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending | ForEach-Object { Join-Path $_.FullName 'lib' } | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
        if ($found) { return $found }
    }
    if ($IpedRoot -and (Test-Path -LiteralPath (Join-Path $IpedRoot 'lib\javafx.web.jar'))) {
        return (Join-Path $IpedRoot 'lib')
    }
    throw 'JavaFX SDK lib nao encontrado dinamicamente. Defina JAVAFX_HOME / JAVAFX_LIB ou passe -JavaFxLib.'
}

function Resolve-OpenCvJar {
    param(
        [string]$Explicit = '',
        [Parameter(Mandatory = $true)][string]$RepoRoot,
        [string]$IpedRoot = ''
    )
    if ($Explicit) {
        if (Test-Path -LiteralPath $Explicit) { return $Explicit }
        throw "OpenCV JAR nao encontrado: $Explicit"
    }
    if ($env:OPENCV_JAR -and (Test-Path -LiteralPath $env:OPENCV_JAR)) { return $env:OPENCV_JAR }
    $candidates = @(
        (Join-Path $RepoRoot 'instalar_captura_chat_zap\dependencies'),
        (Join-Path (Split-Path -Parent $RepoRoot) '.'),
        $(if ($IpedRoot) { Join-Path $IpedRoot 'plugins' })
    ) | Where-Object { $_ -and (Test-Path -LiteralPath $_) }
    foreach ($dir in $candidates) {
        $jar = Get-ChildItem -LiteralPath $dir -File -Filter 'opencv-*.jar' -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($jar) { return $jar.FullName }
    }
    throw 'JAR do OpenCV nao encontrado dinamicamente.'
}
