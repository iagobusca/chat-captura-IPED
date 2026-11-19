<#
.SYNOPSIS
    Compilador reprodutivel multiplataforma (Windows, Linux e macOS) para o Chat Captura IPED e Verificador de Integridade.
.DESCRIPTION
    Compila de forma auditavel e deterministica (sem alterar arquivos versionados por padrao):
      1. verificador-integridade JAR (bytecode Java 8 / major 52, empacotado com timestamps e ordem fixos);
      2. plugin fat-JAR iped-viewers-impl-4.3.1.jar (bytecode Java 11 / major 55, OpenCV + i18n preservados, timestamps/manifesto fixos);
      3. No Windows (Target=full): integridade-verifier.exe (jpackage + payload.zip deterministico + dotnet publish com flags deterministicas);
      4. Pacote ZIP do instalador (completo no Windows; versao portatil JAR-only em Linux/macOS ou Target=jar-only).

    Executa com PowerShell 7+ (pwsh) em Linux/macOS/Windows ou Windows PowerShell 5.1 no Windows.
    Exemplo de auditoria dupla (compila duas vezes e exige SHA-256 identicos):
      pwsh -NoProfile -File ./build-reproducible.ps1 -DownloadDeps -VerifyReproducibility
#>
param(
    [ValidateSet('auto', 'jar-only', 'full')]
    [string]$Target = 'auto',

    [string]$JdkHome = '',
    [string]$Jdk11ForJlink = '',
    [string]$IpedHome = '',
    [string]$JavaFxLib = '',
    [string]$CacheDir = '',
    [string]$OutDir = '',
    [string]$TimestampIso = '1980-02-01T00:00:00Z',

    [switch]$DownloadDeps,
    [switch]$PinTemurin,
    [switch]$Offline,
    [switch]$RunTests,
    [switch]$VerifyReproducibility
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$IsWin = if ($PSVersionTable.PSEdition -eq 'Core') { $IsWindows } else { $true }
$IsLin = if ($PSVersionTable.PSEdition -eq 'Core') { $IsLinux } else { $false }
$IsMac = if ($PSVersionTable.PSEdition -eq 'Core') { $IsMacOS } else { $false }

$RepoRoot = $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($CacheDir)) { $CacheDir = Join-Path $RepoRoot 'ci-deps' }
if ([string]::IsNullOrWhiteSpace($OutDir)) { $OutDir = Join-Path (Join-Path $RepoRoot 'out') 'reproducible' }

if ($Target -eq 'auto') {
    $Target = if ($IsWin) { 'full' } else { 'jar-only' }
}
if ($Target -eq 'full' -and -not $IsWin) {
    throw "Target='full' (integrando jpackage.exe e WinForms win-x64 em integridade-verifier.exe) requer execucao nativa em Windows. Em Linux/macOS utilize -Target jar-only ou -Target auto para auditar os JARs bit-a-bit."
}

$ResolvedTimestamp = [DateTimeOffset]::Parse($TimestampIso, [Globalization.CultureInfo]::InvariantCulture, [Globalization.DateTimeStyles]::AssumeUniversal).UtcDateTime
$FixedZipLocalTuple = @(
    $ResolvedTimestamp.Year,
    $ResolvedTimestamp.Month,
    $ResolvedTimestamp.Day,
    $ResolvedTimestamp.Hour,
    $ResolvedTimestamp.Minute,
    ($ResolvedTimestamp.Second - ($ResolvedTimestamp.Second % 2))
)

$IpedOfficialUrl = 'https://github.com/sepinf-inc/IPED/releases/download/4.3.1/IPED-4.3.1_and_java_plugins.zip'
$IpedOfficialSha256 = 'E8EE71E7E2E41A770B521BFBAABF0CF0B88ADD5FEBFF27C5A5AD375248CFEF8E'

$OpenJfxCatalog = @{
    'windows-x64'   = @{ Url = 'https://download2.gluonhq.com/openjfx/21.0.5/openjfx-21.0.5_windows-x64_bin-sdk.zip';   Sha = '237DBAA24AC05557A65540B5E9EBC6FF5BC011BF0F7B06F5637EAC31AC7235D3' }
    'linux-x64'     = @{ Url = 'https://download2.gluonhq.com/openjfx/21.0.5/openjfx-21.0.5_linux-x64_bin-sdk.zip';     Sha = 'E38B24A91B8C8BC20A3631D57CF0504BE29DF32AAF28BEA8A0265D65C0F89965' }
    'macos-x64'     = @{ Url = 'https://download2.gluonhq.com/openjfx/21.0.5/openjfx-21.0.5_osx-x64_bin-sdk.zip';       Sha = '266FB89BE84BF7D291B3F67E4305826A2470AFDF787EB5736C857E30F0E33858' }
    'macos-aarch64' = @{ Url = 'https://download2.gluonhq.com/openjfx/21.0.5/openjfx-21.0.5_osx-aarch64_bin-sdk.zip';   Sha = '4C1B58366F09E044635AFB241D583104029A0E11CEC9043075548CBA4F4E2085' }
}

$Temurin21Catalog = @{
    'windows-x64'   = @{ File = 'OpenJDK21U-jdk_x64_windows_hotspot_21.0.13_9.zip';     Url = 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9/OpenJDK21U-jdk_x64_windows_hotspot_21.0.13_9.zip';     Sha = 'A4DBAA05FCA4BD89D84584E58C459BD732EFEBB1722AF80AB13C6516832004F9' }
    'linux-x64'     = @{ File = 'OpenJDK21U-jdk_x64_linux_hotspot_21.0.13_9.tar.gz';    Url = 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9/OpenJDK21U-jdk_x64_linux_hotspot_21.0.13_9.tar.gz';    Sha = 'D7F5D5CF865A2902B70FF549B97586A7D3F234E016F041AB447EA740C7D66D1C' }
    'macos-x64'     = @{ File = 'OpenJDK21U-jdk_x64_mac_hotspot_21.0.13_9.tar.gz';      Url = 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9/OpenJDK21U-jdk_x64_mac_hotspot_21.0.13_9.tar.gz';      Sha = '9AA1D1C17AE5467A022679E7E0AF5C00797D94AC462ED1A46F4DB07A8A34A6DD' }
    'macos-aarch64' = @{ File = 'OpenJDK21U-jdk_aarch64_mac_hotspot_21.0.13_9.tar.gz';  Url = 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9/OpenJDK21U-jdk_aarch64_mac_hotspot_21.0.13_9.tar.gz';  Sha = 'BF036B35B071BFB2F54F2C0AF8EE8D88AFA95EE6EB3DCE02C0F94CFC2524D04A' }
}

function Get-HostPlatformKey {
    $osKey = if ($IsWin) { 'windows' } elseif ($IsLin) { 'linux' } elseif ($IsMac) { 'macos' } else { throw 'Sistema operacional nao suportado.' }
    $archRaw = [System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString().ToLowerInvariant()
    $archKey = switch ($archRaw) {
        'x64'   { 'x64' }
        'arm64' { 'aarch64' }
        default { throw "Arquitetura nao suportada no catalogo: $archRaw (use -JavaFxLib / -JdkHome explicitamente)" }
    }
    return "$osKey-$archKey"
}

function Get-JavaExeName([string]$BaseName) {
    if ($IsWin) { return "$BaseName.exe" }
    return $BaseName
}

function Get-JavaMajorFromHome([string]$HomeDir) {
    $rel = Join-Path $HomeDir 'release'
    if (Test-Path -LiteralPath $rel) {
        $line = Get-Content -LiteralPath $rel -ErrorAction SilentlyContinue | Where-Object { $_ -match '^JAVA_VERSION=' } | Select-Object -First 1
        if ($line -match 'JAVA_VERSION="1\.(\d+)') { return [int]$Matches[1] }
        if ($line -match 'JAVA_VERSION="(\d+)') { return [int]$Matches[1] }
    }
    return 0
}

function Resolve-CandidateJdkHome([string]$CandidateDir) {
    if ([string]::IsNullOrWhiteSpace($CandidateDir)) { return $null }
    $javacName = Get-JavaExeName 'javac'
    foreach ($sub in @($CandidateDir, (Join-Path (Join-Path $CandidateDir 'Contents') 'Home'))) {
        $tool = Join-Path (Join-Path $sub 'bin') $javacName
        if (Test-Path -LiteralPath $tool -PathType Leaf) {
            return $sub
        }
    }
    return $null
}

function Ensure-VerifiedDownload([string]$Url, [string]$DestPath, [string]$ExpectedSha256, [string]$Label) {
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $DestPath) | Out-Null
    if (Test-Path -LiteralPath $DestPath -PathType Leaf) {
        $curHash = (Get-FileHash -LiteralPath $DestPath -Algorithm SHA256).Hash.ToUpperInvariant()
        if ($curHash -eq $ExpectedSha256.ToUpperInvariant()) {
            return $DestPath
        }
        if ($Offline) { throw "Arquivo em cache com SHA-256 divergente para $Label ($DestPath): $curHash != $ExpectedSha256" }
    }
    if ($Offline) { throw "Dependencia ausente em modo -Offline: $Label ($DestPath). Execute sem -Offline ou com -DownloadDeps." }
    if (-not $DownloadDeps) { throw "Dependencia externa necessaria nao encontrada ($Label): $DestPath. Informe o caminho via parametro ou passe -DownloadDeps." }

    Write-Host "Baixando $Label..."
    if ($IsWin) {
        try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch {}
    }
    $tmpFile = "$DestPath.tmp"
    Remove-Item -LiteralPath $tmpFile -Force -ErrorAction SilentlyContinue
    Invoke-WebRequest -UseBasicParsing -Uri $Url -OutFile $tmpFile
    $actual = (Get-FileHash -LiteralPath $tmpFile -Algorithm SHA256).Hash.ToUpperInvariant()
    if ($actual -ne $ExpectedSha256.ToUpperInvariant()) {
        Remove-Item -LiteralPath $tmpFile -Force -ErrorAction SilentlyContinue
        throw "FALHA DE INTEGRIDADE SHA-256 em $Label! Esperado=$ExpectedSha256 Recebido=$actual"
    }
    Move-Item -LiteralPath $tmpFile -Destination $DestPath -Force
    Write-Host "OK SHA-256 verificado: $Label ($actual)"
    return $DestPath
}

function Expand-ArchiveCrossPlatform([string]$ArchivePath, [string]$DestDir) {
    Remove-Item -LiteralPath $DestDir -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path $DestDir | Out-Null
    if ($ArchivePath.EndsWith('.zip', [StringComparison]::OrdinalIgnoreCase)) {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        [IO.Compression.ZipFile]::ExtractToDirectory($ArchivePath, $DestDir)
    } elseif ($ArchivePath.EndsWith('.tar.gz', [StringComparison]::OrdinalIgnoreCase) -or $ArchivePath.EndsWith('.tgz', [StringComparison]::OrdinalIgnoreCase)) {
        & tar -xzf $ArchivePath -C $DestDir
        if ($LASTEXITCODE -ne 0) { throw "tar falhou ao extrair $ArchivePath (exit=$LASTEXITCODE)" }
    } else {
        throw "Formato de arquivo compacto nao suportado: $ArchivePath"
    }
}

function Resolve-ToolchainJdk {
    $platformKey = Get-HostPlatformKey
    $javacName = Get-JavaExeName 'javac'

    if (-not [string]::IsNullOrWhiteSpace($JdkHome)) {
        $resolved = Resolve-CandidateJdkHome $JdkHome
        if (-not $resolved) { throw "JDK invalido em -JdkHome: $JdkHome" }
        return $resolved
    }

    if ($PinTemurin) {
        $entry = $Temurin21Catalog[$platformKey]
        if (-not $entry) { throw "Temurin 21 nao catalogado para $platformKey" }
        $pkgPath = Ensure-VerifiedDownload -Url $entry.Url -DestPath (Join-Path $CacheDir $entry.File) -ExpectedSha256 $entry.Sha -Label "Temurin JDK 21 ($platformKey)"
        $extractDir = Join-Path $CacheDir 'temurin-21.0.13+9'
        $existing = Get-ChildItem -LiteralPath $extractDir -Directory -ErrorAction SilentlyContinue | ForEach-Object { Resolve-CandidateJdkHome $_.FullName } | Where-Object { $_ } | Select-Object -First 1
        if (-not $existing) {
            Expand-ArchiveCrossPlatform -ArchivePath $pkgPath -DestDir $extractDir
            $existing = Get-ChildItem -LiteralPath $extractDir -Directory -ErrorAction SilentlyContinue | ForEach-Object { Resolve-CandidateJdkHome $_.FullName } | Where-Object { $_ } | Select-Object -First 1
        }
        if (-not $existing) { throw "Nao foi possivel localizar bin/$javacName no Temurin extraido em $extractDir" }
        return $existing
    }

    foreach ($envName in @('JAVA26_HOME', 'JAVA21_HOME', 'JAVA17_HOME')) {
        $val = [Environment]::GetEnvironmentVariable($envName)
        $cand = Resolve-CandidateJdkHome $val
        if ($cand -and (Get-JavaMajorFromHome $cand) -ge 11) { return $cand }
    }

    $probeRoots = New-Object System.Collections.Generic.List[string]
    if ($IsWin) {
        foreach ($r in @($env:ProgramFiles, ${env:ProgramFiles(x86)}, (Join-Path $env:LOCALAPPDATA 'Programs'), (Join-Path $env:USERPROFILE '.jdks'))) {
            if ($r -and (Test-Path -LiteralPath $r)) { $probeRoots.Add($r) }
        }
        $vendors = @('Eclipse Adoptium', 'Java', 'Microsoft', 'Zulu', 'BellSoft', 'Amazon Corretto', 'Temurin', '')
    } elseif ($IsMac) {
        foreach ($r in @('/Library/Java/JavaVirtualMachines', (Join-Path $HOME 'Library/Java/JavaVirtualMachines'), '/opt/homebrew/opt', '/usr/local/opt', (Join-Path $HOME '.jdks'))) {
            if ($r -and (Test-Path -LiteralPath $r)) { $probeRoots.Add($r) }
        }
        $vendors = @('', 'openjdk@21', 'temurin-21.jdk')
    } else {
        foreach ($r in @('/usr/lib/jvm', '/opt/java', (Join-Path $HOME '.jdks'), (Join-Path $HOME '.sdkman/candidates/java'))) {
            if ($r -and (Test-Path -LiteralPath $r)) { $probeRoots.Add($r) }
        }
        $vendors = @('')
    }

    $candidates = New-Object System.Collections.Generic.List[object]
    foreach ($root in ($probeRoots | Select-Object -Unique)) {
        foreach ($v in $vendors) {
            $dir = if ($v) { Join-Path $root $v } else { $root }
            if (-not (Test-Path -LiteralPath $dir)) { continue }
            $direct = Resolve-CandidateJdkHome $dir
            if ($direct) {
                $maj = Get-JavaMajorFromHome $direct
                if ($maj -ge 11) { $candidates.Add([pscustomobject]@{ Major = $maj; Name = (Split-Path -Leaf $direct); Path = $direct }) }
            }
            foreach ($child in (Get-ChildItem -LiteralPath $dir -Directory -ErrorAction SilentlyContinue)) {
                $resolved = Resolve-CandidateJdkHome $child.FullName
                if ($resolved) {
                    $maj = Get-JavaMajorFromHome $resolved
                    if ($maj -ge 11) { $candidates.Add([pscustomobject]@{ Major = $maj; Name = $child.Name; Path = $resolved }) }
                }
            }
        }
    }
    $pick = $candidates | Sort-Object @{ Expression = 'Major'; Descending = $true }, @{ Expression = 'Name'; Descending = $true } | Select-Object -First 1
    if ($pick) { return $pick.Path }

    $envJavaHome = Resolve-CandidateJdkHome ([Environment]::GetEnvironmentVariable('JAVA_HOME'))
    if ($envJavaHome -and (Get-JavaMajorFromHome $envJavaHome) -ge 11) { return $envJavaHome }

    $whichJavac = Get-Command $javacName -ErrorAction SilentlyContinue
    if ($whichJavac) {
        $homeFromPath = Split-Path -Parent (Split-Path -Parent $whichJavac.Source)
        if ((Get-JavaMajorFromHome $homeFromPath) -ge 11) { return $homeFromPath }
    }
    throw "JDK 11+ nao encontrado. Informe -JdkHome ou execute com -DownloadDeps -PinTemurin."
}

function Resolve-IpedHomePath {
    if (-not [string]::IsNullOrWhiteSpace($IpedHome)) {
        if (Test-Path -LiteralPath (Join-Path $IpedHome 'lib')) { return (Resolve-Path -LiteralPath $IpedHome).Path }
        throw "IPED invalido em -IpedHome: $IpedHome"
    }
    if ($env:IPED_HOME -and (Test-Path -LiteralPath (Join-Path $env:IPED_HOME 'lib'))) { return $env:IPED_HOME }

    $cur = $RepoRoot
    for ($i = 0; $i -lt 4 -and $cur; $i++) {
        $cand = Join-Path $cur 'iped-4.3.1'
        if (Test-Path -LiteralPath (Join-Path $cand 'lib/iped-viewers-api-4.3.1.jar')) { return $cand }
        $cur = Split-Path -Parent $cur
    }

    $cachedExtract = Join-Path $CacheDir 'iped-4.3.1-official'
    $apiJar = Get-ChildItem -LiteralPath $cachedExtract -Filter 'iped-viewers-api-4.3.1.jar' -Recurse -File -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $apiJar) {
        $zipPath = Ensure-VerifiedDownload -Url $IpedOfficialUrl -DestPath (Join-Path $CacheDir 'IPED-4.3.1_and_java_plugins.zip') -ExpectedSha256 $IpedOfficialSha256 -Label 'IPED 4.3.1 oficial'
        Expand-ArchiveCrossPlatform -ArchivePath $zipPath -DestDir $cachedExtract
        $apiJar = Get-ChildItem -LiteralPath $cachedExtract -Filter 'iped-viewers-api-4.3.1.jar' -Recurse -File -ErrorAction SilentlyContinue | Select-Object -First 1
    }
    if (-not $apiJar) { throw "Nao foi possivel localizar iped-viewers-api-4.3.1.jar dentro de $cachedExtract" }
    return (Split-Path -Parent (Split-Path -Parent $apiJar.FullName))
}

function Get-JarMinJavaMajor([string]$JarPath) {
    try {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $z = [IO.Compression.ZipFile]::OpenRead($JarPath)
        try {
            $e = $z.GetEntry('module-info.class')
            if ($e) {
                $s = $e.Open()
                try {
                    $buf = New-Object byte[] 8
                    $n = $s.Read($buf, 0, 8)
                    if ($n -eq 8) {
                        $major = ([int]$buf[6] -shl 8) -bor [int]$buf[7]
                        return ($major - 44)
                    }
                } finally { $s.Dispose() }
            }
        } finally { $z.Dispose() }
    } catch {}
    return 11
}

function Resolve-JavaFxLibPath([string]$ResolvedIpedHome, [int]$JdkMajor) {
    if (-not [string]::IsNullOrWhiteSpace($JavaFxLib)) {
        if (Test-Path -LiteralPath (Join-Path $JavaFxLib 'javafx.web.jar')) { return (Resolve-Path -LiteralPath $JavaFxLib).Path }
        throw "JavaFX lib invalido em -JavaFxLib: $JavaFxLib"
    }
    $platformKey = Get-HostPlatformKey
    $cachedJfxExtract = Join-Path $CacheDir "openjfx-21.0.5-$platformKey"
    $cachedJfxWeb = Get-ChildItem -LiteralPath $cachedJfxExtract -Filter 'javafx.web.jar' -Recurse -File -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($PinTemurin -or $JdkMajor -le 23) {
        if ($cachedJfxWeb) { return (Split-Path -Parent $cachedJfxWeb.FullName) }
    }
    foreach ($envCand in @($env:JAVAFX_LIB, $(if ($env:JAVAFX_HOME) { Join-Path $env:JAVAFX_HOME 'lib' }), $env:PATH_TO_FX)) {
        if ($envCand -and (Test-Path -LiteralPath (Join-Path $envCand 'javafx.web.jar'))) {
            if ((Get-JarMinJavaMajor (Join-Path $envCand 'javafx.web.jar')) -le $JdkMajor) { return $envCand }
        }
    }
    if (-not $PinTemurin) {
        $parentRepo = Split-Path -Parent $RepoRoot
        foreach ($base in @($RepoRoot, $parentRepo, (Split-Path -Parent $parentRepo))) {
            if (-not $base -or -not (Test-Path -LiteralPath $base)) { continue }
            $sdks = Get-ChildItem -LiteralPath $base -Directory -Filter 'javafx-sdk*' -ErrorAction SilentlyContinue | Sort-Object Name -Descending
            foreach ($sdk in $sdks) {
                $web = Join-Path (Join-Path $sdk.FullName 'lib') 'javafx.web.jar'
                if ((Test-Path -LiteralPath $web) -and ((Get-JarMinJavaMajor $web) -le $JdkMajor)) {
                    return (Split-Path -Parent $web)
                }
            }
        }
    }

    $jfxEntry = $OpenJfxCatalog[$platformKey]
    if (-not $jfxEntry) { throw "OpenJFX 21.0.5 nao catalogado para $platformKey; informe -JavaFxLib" }
    if (-not $cachedJfxWeb) {
        $zipPath = Ensure-VerifiedDownload -Url $jfxEntry.Url -DestPath (Join-Path $CacheDir "openjfx-21.0.5-$platformKey.zip") -ExpectedSha256 $jfxEntry.Sha -Label "OpenJFX 21.0.5 ($platformKey)"
        Expand-ArchiveCrossPlatform -ArchivePath $zipPath -DestDir $cachedJfxExtract
        $cachedJfxWeb = Get-ChildItem -LiteralPath $cachedJfxExtract -Filter 'javafx.web.jar' -Recurse -File -ErrorAction SilentlyContinue | Select-Object -First 1
    }
    if (-not $cachedJfxWeb) { throw "javafx.web.jar nao encontrado apos extracao de OpenJFX em $cachedJfxExtract" }
    return (Split-Path -Parent $cachedJfxWeb.FullName)
}

function Get-ReproduciblePackerSource {
    return @'
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.*;

public final class ReproducibleZipPacker {
    public static void main(String[] args) throws Exception {
        if (args.length < 8) {
            throw new IllegalArgumentException("Uso: ReproducibleZipPacker <srcDir> <outZip> <y> <m> <d> <h> <min> <sec> [level]");
        }
        Path srcDir = Paths.get(args[0]).toAbsolutePath().normalize();
        Path outZip = Paths.get(args[1]).toAbsolutePath().normalize();
        LocalDateTime fixedTime = LocalDateTime.of(
                Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]),
                Integer.parseInt(args[5]), Integer.parseInt(args[6]), Integer.parseInt(args[7]));
        int level = args.length >= 9 ? Integer.parseInt(args[8]) : Deflater.DEFAULT_COMPRESSION;

        List<Path> files = new ArrayList<>();
        Files.walk(srcDir).filter(Files::isRegularFile).forEach(files::add);
        files.sort(Comparator.comparing(p -> srcDir.relativize(p).toString().replace('\\', '/')));

        if (outZip.getParent() != null) Files.createDirectories(outZip.getParent());
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(outZip)))) {
            zos.setLevel(level);
            byte[] buf = new byte[16384];
            for (Path f : files) {
                String rel = srcDir.relativize(f).toString().replace('\\', '/');
                ZipEntry entry = new ZipEntry(rel);
                entry.setTimeLocal(fixedTime);
                zos.putNextEntry(entry);
                try (InputStream in = Files.newInputStream(f)) {
                    int n;
                    while ((n = in.read(buf)) >= 0) {
                        if (n > 0) zos.write(buf, 0, n);
                    }
                }
                zos.closeEntry();
            }
        }
    }
}
'@
}

function Invoke-SingleReproducibleBuild([string]$WorkRoot, [string]$RunOutDir, [string]$PackerClassDir, [string]$Jdk, [string]$Iped, [string]$Jfx) {
    $cpSep = [IO.Path]::PathSeparator
    $javac = Join-Path (Join-Path $Jdk 'bin') (Get-JavaExeName 'javac')
    $java = Join-Path (Join-Path $Jdk 'bin') (Get-JavaExeName 'java')

    Remove-Item -LiteralPath $WorkRoot -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path $WorkRoot, $RunOutDir | Out-Null

    $pluginSrc = [IO.File]::ReadAllText((Join-Path $RepoRoot 'src/iped/viewers/HtmlLinkViewer.java'))
    if ($pluginSrc -notmatch 'CHAT_CAPTURE_PLUGIN_VERSION\s*=\s*"([^"]+)"') { throw 'CHAT_CAPTURE_PLUGIN_VERSION nao encontrado.' }
    $pluginVer = $Matches[1]
    $verifierVer = (Get-Content -LiteralPath (Join-Path $RepoRoot 'verificador-integridade/VERSION') -Raw).Trim()

    # 1. Compila Verificador de Integridade JAR (Java 8 bytecode) de forma deterministica
    $vClasses = Join-Path $WorkRoot 'verifier-classes'
    New-Item -ItemType Directory -Force -Path $vClasses | Out-Null
    $jsonJar = Join-Path $RepoRoot 'verificador-integridade/dependencies/json-20260719.jar'
    & $javac --release 8 -encoding UTF-8 -Xlint:none -cp $jsonJar -d $vClasses (Join-Path $RepoRoot 'verificador-integridade/src/IntegrityVerifier.java')
    if ($LASTEXITCODE -ne 0) { throw "Falha ao compilar IntegrityVerifier.java ($LASTEXITCODE)" }

    $vIcon = Join-Path $RepoRoot 'verificador-integridade/imagem doc/verificador-icon.png'
    if (Test-Path -LiteralPath $vIcon) {
        $iconDst = Join-Path $vClasses 'imagem doc'
        New-Item -ItemType Directory -Force -Path $iconDst | Out-Null
        Copy-Item -LiteralPath $vIcon -Destination (Join-Path $iconDst 'verificador-icon.png') -Force
    }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $jsonExtract = Join-Path $WorkRoot 'json-extract'
    [IO.Compression.ZipFile]::ExtractToDirectory($jsonJar, $jsonExtract)
    Copy-Item -Path (Join-Path $jsonExtract 'org') -Destination $vClasses -Recurse -Force
    New-Item -ItemType Directory -Force -Path (Join-Path $vClasses 'META-INF') | Out-Null
    $vMf = @(
        'Manifest-Version: 1.0',
        'Created-By: ChatCaptura-Reproducible-Builder',
        'Main-Class: IntegrityVerifier',
        "Implementation-Version: $verifierVer",
        ''
    ) -join "`r`n"
    [IO.File]::WriteAllText((Join-Path $vClasses 'META-INF/MANIFEST.MF'), $vMf, [Text.UTF8Encoding]::new($false))

    $vJarOut = Join-Path $RunOutDir 'integridade-verifier.jar'
    & $java -cp $PackerClassDir ReproducibleZipPacker $vClasses $vJarOut @FixedZipLocalTuple
    if ($LASTEXITCODE -ne 0) { throw "Falha ao empacotar integridade-verifier.jar deterministico" }

    # 2. Compila Plugin fat-JAR (Java 11 bytecode) preservando classes base MultiViewer/OpenCV/i18n
    $pWork = Join-Path $WorkRoot 'plugin-work'
    $pClasses = Join-Path $pWork 'compiled'
    $pStage = Join-Path $pWork 'stage'
    New-Item -ItemType Directory -Force -Path $pClasses, $pStage | Out-Null

    $sources = @(Get-ChildItem (Join-Path $RepoRoot 'src/iped/viewers') -Filter '*.java' | Sort-Object Name | ForEach-Object { $_.FullName })
    $ipedJars = @(Get-ChildItem (Join-Path $Iped 'lib') -Filter '*.jar' | Sort-Object Name | ForEach-Object { $_.FullName })
    $pluginDepCp = @(
        (Join-Path $RepoRoot 'instalar_captura_chat_zap/dependencies/json-20260719.jar'),
        (Join-Path $RepoRoot 'instalar_captura_chat_zap/dependencies/opencv-4120.jar')
    ) + $ipedJars
    $cpArg = $pluginDepCp -join $cpSep
    $argsFile = Join-Path $pWork 'javac.args'
    $quoteArg = { param([string]$s) '"' + $s.Replace('\', '/').Replace('"', '\"') + '"' }
    $argLines = New-Object System.Collections.Generic.List[string]
    $argLines.Add('--release')
    $argLines.Add('11')
    $argLines.Add('-encoding')
    $argLines.Add('UTF-8')
    $argLines.Add('-Xlint:none')
    $argLines.Add('--module-path')
    $argLines.Add((& $quoteArg $Jfx))
    $argLines.Add('--add-modules')
    $argLines.Add('javafx.controls,javafx.web,javafx.swing')
    $argLines.Add('-cp')
    $argLines.Add((& $quoteArg $cpArg))
    $argLines.Add('-d')
    $argLines.Add((& $quoteArg $pClasses))
    foreach ($s in $sources) { $argLines.Add((& $quoteArg $s)) }
    [IO.File]::WriteAllLines($argsFile, $argLines, [Text.UTF8Encoding]::new($false))
    & $javac "@$argsFile"
    if ($LASTEXITCODE -ne 0) { throw "Falha ao compilar Plugin ($LASTEXITCODE)" }

    $basePluginJar = Join-Path $RepoRoot 'instalar_captura_chat_zap/iped-viewers-impl-4.3.1.jar'
    [IO.Compression.ZipFile]::ExtractToDirectory($basePluginJar, $pStage)
    Get-ChildItem (Join-Path $pStage 'iped/viewers') -Filter 'HtmlLinkViewer*.class' -ErrorAction SilentlyContinue | Remove-Item -Force
    foreach ($src in $sources) {
        $stem = [IO.Path]::GetFileNameWithoutExtension($src)
        Get-ChildItem (Join-Path $pStage 'iped/viewers') -Filter "$stem*.class" -ErrorAction SilentlyContinue | Where-Object {
            $_.Name -eq "$stem.class" -or $_.Name.StartsWith("$stem`$")
        } | Remove-Item -Force
    }
    Copy-Item -Path (Join-Path (Join-Path $pClasses 'iped/viewers') '*.class') -Destination (Join-Path (Join-Path $pStage 'iped') 'viewers') -Force
    Copy-Item -LiteralPath (Join-Path $RepoRoot 'instalar_captura_chat_zap/iped-viewer-messages.properties') -Destination $pStage -Force
    Copy-Item -LiteralPath (Join-Path $RepoRoot 'instalar_captura_chat_zap/iped-viewer-messages_pt_BR.properties') -Destination $pStage -Force
    Copy-Item -LiteralPath (Join-Path $RepoRoot 'instalar_captura_chat_zap/iped-viewer-messages.properties') -Destination (Join-Path $pStage 'iped/viewers/localization') -Force
    Copy-Item -LiteralPath (Join-Path $RepoRoot 'instalar_captura_chat_zap/iped-viewer-messages_pt_BR.properties') -Destination (Join-Path $pStage 'iped/viewers/localization') -Force

    $pMf = @(
        'Manifest-Version: 1.0',
        'Created-By: ChatCaptura-Reproducible-Builder',
        "Implementation-Version: $pluginVer",
        ''
    ) -join "`r`n"
    [IO.File]::WriteAllText((Join-Path $pStage 'META-INF/MANIFEST.MF'), $pMf, [Text.UTF8Encoding]::new($false))

    $pJarOut = Join-Path $RunOutDir 'iped-viewers-impl-4.3.1.jar'
    & $java -cp $PackerClassDir ReproducibleZipPacker $pStage $pJarOut @FixedZipLocalTuple
    if ($LASTEXITCODE -ne 0) { throw "Falha ao empacotar iped-viewers-impl-4.3.1.jar deterministico" }

    $vExeOut = $null
    if ($Target -eq 'full') {
        $exeWork = Join-Path $WorkRoot 'exe-work'
        $jlinkInput = Join-Path $exeWork 'input'
        $runtimeDir = Join-Path $exeWork 'runtime'
        $appImgDest = Join-Path $exeWork 'app-image'
        $payloadDir = Join-Path $appImgDest 'integridade-verifier'
        $launcherWork = Join-Path $exeWork 'launcher'
        New-Item -ItemType Directory -Force -Path $jlinkInput, $appImgDest | Out-Null
        Copy-Item -LiteralPath $vJarOut -Destination (Join-Path $jlinkInput 'integridade-verifier.jar') -Force

        $jlinkSrcJdk = if ($Jdk11ForJlink) { $Jdk11ForJlink } else { $Jdk }
        $jlink = Join-Path (Join-Path $jlinkSrcJdk 'bin') (Get-JavaExeName 'jlink')
        $jpackage = Join-Path (Join-Path $Jdk 'bin') (Get-JavaExeName 'jpackage')
        & $jlink --module-path (Join-Path $jlinkSrcJdk 'jmods') --add-modules java.base,java.datatransfer,java.desktop,java.xml,java.logging,java.sql --strip-debug --no-header-files --no-man-pages --output $runtimeDir | Out-Host
        if ($LASTEXITCODE -ne 0) { throw "jlink falhou ($LASTEXITCODE)" }
        & $jpackage --type app-image --name integridade-verifier --input $jlinkInput --main-jar integridade-verifier.jar --main-class IntegrityVerifier --runtime-image $runtimeDir --dest $appImgDest --app-version $verifierVer | Out-Host
        if ($LASTEXITCODE -ne 0) { throw "jpackage falhou ($LASTEXITCODE)" }

        Copy-Item -LiteralPath (Join-Path $RepoRoot 'verificador-integridade/launcher') -Destination $launcherWork -Recurse -Force
        Remove-Item -LiteralPath (Join-Path $launcherWork 'bin'), (Join-Path $launcherWork 'obj'), (Join-Path $launcherWork 'publish') -Recurse -Force -ErrorAction SilentlyContinue

        $rows = Get-ChildItem -LiteralPath $payloadDir -File -Recurse | Sort-Object FullName | ForEach-Object {
            $rel = $_.FullName.Substring($payloadDir.Length).TrimStart('\', '/').Replace('\', '/')
            $h = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
            "$h|$rel"
        }
        [IO.File]::WriteAllLines((Join-Path $launcherWork 'payload.sha256'), $rows, [Text.UTF8Encoding]::new($false))
        $payloadZip = Join-Path $launcherWork 'payload.zip'
        Remove-Item -LiteralPath $payloadZip -Force -ErrorAction SilentlyContinue
        & $java -cp $PackerClassDir ReproducibleZipPacker $payloadDir $payloadZip @FixedZipLocalTuple
        if ($LASTEXITCODE -ne 0) { throw "Falha ao empacotar payload.zip deterministico" }

        $env:MSBUILDDISABLENODEREUSE = '1'
        $env:DOTNET_CLI_DO_NOT_USE_MSBUILD_SERVER = '1'
        $dotnetArgs = @(
            'publish', (Join-Path $launcherWork 'Launcher.csproj'),
            '--disable-build-servers',
            '--configfile', (Join-Path $RepoRoot 'verificador-integridade/nuget.config'),
            '--ignore-failed-sources',
            '-p:UseSharedCompilation=false',
            '-p:Deterministic=true',
            '-p:ContinuousIntegrationBuild=true',
            '-p:IncludeSourceRevisionInInformationalVersion=false',
            '-p:DebugType=none',
            '-p:DebugSymbols=false',
            "-p:PathMap=$launcherWork=/src",
            '-m:1', '-nodeReuse:false', '-c', 'Release', '-r', 'win-x64',
            '--self-contained', 'true',
            '-p:PublishSingleFile=true',
            '-p:IncludeNativeLibrariesForSelfExtract=true',
            '-o', (Join-Path $launcherWork 'publish')
        )
        if ($Offline) { $dotnetArgs += '-p:NuGetAudit=false' }
        & dotnet @dotnetArgs | Out-Host
        $pubExit = $LASTEXITCODE
        try { & dotnet build-server shutdown | Out-Null } catch {}
        if ($pubExit -ne 0) { throw "dotnet publish falhou ($pubExit)" }

        $vExeOut = Join-Path $RunOutDir 'integridade-verifier.exe'
        Copy-Item -LiteralPath (Join-Path $launcherWork 'publish/integridade-verifier.exe') -Destination $vExeOut -Force
    }

    # 3. Gera ZIP do instalador de forma deterministica (completo no Windows; portatil JAR-only em jar-only)
    $zipStage = Join-Path $WorkRoot 'zip-stage'
    $zipInst = Join-Path $zipStage 'instalar_captura_chat_zap'
    New-Item -ItemType Directory -Force -Path (Join-Path $zipInst 'dependencies') | Out-Null
    Copy-Item -LiteralPath (Join-Path $RepoRoot 'LEIA-ME.txt') -Destination $zipStage -Force
    Copy-Item -LiteralPath (Join-Path $RepoRoot 'instalar_captura_chat_zap/dependencies/json-20260719.jar') -Destination (Join-Path $zipInst 'dependencies') -Force
    Copy-Item -LiteralPath (Join-Path $RepoRoot 'instalar_captura_chat_zap/dependencies/opencv-4120.jar') -Destination (Join-Path $zipInst 'dependencies') -Force
    Copy-Item -LiteralPath (Join-Path $RepoRoot 'instalar_captura_chat_zap/instalar_captura_chat_zap.bat') -Destination $zipInst -Force
    Copy-Item -LiteralPath (Join-Path $RepoRoot 'instalar_captura_chat_zap/install-localization.ps1') -Destination $zipInst -Force
    Copy-Item -LiteralPath (Join-Path $RepoRoot 'instalar_captura_chat_zap/iped-viewer-messages.properties') -Destination $zipInst -Force
    Copy-Item -LiteralPath (Join-Path $RepoRoot 'instalar_captura_chat_zap/iped-viewer-messages_pt_BR.properties') -Destination $zipInst -Force
    Copy-Item -LiteralPath (Join-Path $RepoRoot 'instalar_captura_chat_zap/validate-plugin-jar.ps1') -Destination $zipInst -Force
    Copy-Item -LiteralPath $vJarOut -Destination (Join-Path $zipInst 'integridade-verifier.jar') -Force
    Copy-Item -LiteralPath $pJarOut -Destination (Join-Path $zipInst 'iped-viewers-impl-4.3.1.jar') -Force
    if ($Target -eq 'full') {
        Copy-Item -LiteralPath (Join-Path $RepoRoot 'instalar_captura_chat_zap/opencv_java4120.dll') -Destination $zipInst -Force
        Copy-Item -LiteralPath $vExeOut -Destination (Join-Path $zipInst 'integridade-verifier.exe') -Force
        $zipName = "instalador-chat-captura-IPED-$pluginVer-verificador-$verifierVer.zip"
    } else {
        $zipName = "instalador-chat-captura-IPED-$pluginVer-verificador-$verifierVer-portable-jars.zip"
    }
    $zipOut = Join-Path $RunOutDir $zipName
    & $java -cp $PackerClassDir ReproducibleZipPacker $zipStage $zipOut @FixedZipLocalTuple
    if ($LASTEXITCODE -ne 0) { throw "Falha ao empacotar $zipName deterministico" }

    $results = [ordered]@{
        'integridade-verifier.jar'     = $vJarOut
        'iped-viewers-impl-4.3.1.jar'  = $pJarOut
        $zipName                       = $zipOut
    }
    if ($vExeOut) { $results['integridade-verifier.exe'] = $vExeOut }
    return $results
}

$resolvedJdk = Resolve-ToolchainJdk
$resolvedJdkMajor = Get-JavaMajorFromHome $resolvedJdk
$resolvedIped = Resolve-IpedHomePath
$resolvedJfx = Resolve-JavaFxLibPath $resolvedIped $resolvedJdkMajor

Write-Host "=== Reproducible Build Config ==="
Write-Host "Platform:      $(Get-HostPlatformKey) | Target=$Target"
Write-Host "JDK:           $resolvedJdk (major=$(Get-JavaMajorFromHome $resolvedJdk))"
Write-Host "IPED Home:     $resolvedIped"
Write-Host "JavaFX Lib:    $resolvedJfx"
Write-Host "Fixed Zip UTC: $TimestampIso"
Write-Host "Output Dir:    $OutDir"

$workBase = Join-Path ([IO.Path]::GetTempPath()) ('chat-captura-repro-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $workBase | Out-Null
try {
    $packerSrcDir = Join-Path $workBase 'packer-src'
    $packerClsDir = Join-Path $workBase 'packer-cls'
    New-Item -ItemType Directory -Force -Path $packerSrcDir, $packerClsDir | Out-Null
    $packerJava = Join-Path $packerSrcDir 'ReproducibleZipPacker.java'
    [IO.File]::WriteAllText($packerJava, (Get-ReproduciblePackerSource), [Text.UTF8Encoding]::new($false))
    $javacBin = Join-Path (Join-Path $resolvedJdk 'bin') (Get-JavaExeName 'javac')
    & $javacBin --release 11 -encoding UTF-8 -d $packerClsDir $packerJava
    if ($LASTEXITCODE -ne 0) { throw "Falha ao compilar ReproducibleZipPacker" }

    $raw1 = Invoke-SingleReproducibleBuild -WorkRoot (Join-Path $workBase 'run1') -RunOutDir $OutDir -PackerClassDir $packerClsDir -Jdk $resolvedJdk -Iped $resolvedIped -Jfx $resolvedJfx
    $artifacts = $raw1 | Where-Object { $_ -is [System.Collections.IDictionary] } | Select-Object -Last 1

    Write-Host "`n=== Artefatos Gerados ==="
    $hashes1 = @{}
    foreach ($k in $artifacts.Keys) {
        $p = $artifacts[$k]
        $fi = Get-Item -LiteralPath $p
        $h = (Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash
        $hashes1[$k] = $h
        Write-Host ("  {0,-60} | {1,10} bytes | {2}" -f $k, $fi.Length, $h)
    }

    if ($VerifyReproducibility) {
        Write-Host "`n=== Reexecutando Build #2 para Prova de Reprodutibilidade ==="
        $out2 = Join-Path $workBase 'out2'
        $raw2 = Invoke-SingleReproducibleBuild -WorkRoot (Join-Path $workBase 'run2') -RunOutDir $out2 -PackerClassDir $packerClsDir -Jdk $resolvedJdk -Iped $resolvedIped -Jfx $resolvedJfx
        $artifacts2 = $raw2 | Where-Object { $_ -is [System.Collections.IDictionary] } | Select-Object -Last 1
        foreach ($k in $artifacts.Keys) {
            $h1 = $hashes1[$k]
            $h2 = (Get-FileHash -LiteralPath $artifacts2[$k] -Algorithm SHA256).Hash
            if ($h1 -ne $h2) {
                throw "REPRODUCIBILITY_MISMATCH em ${k}: Run1=$h1 vs Run2=$h2"
            }
            Write-Host "  REPRODUCIBLE_MATCH: $k ($h1)"
        }
        Write-Host "REPRODUCIBLE_BUILD_VERIFIED=PASS"
    }

    if ($RunTests) {
        Write-Host "`n=== Validacao Pos-Build ==="
        $javaBin = Join-Path (Join-Path $resolvedJdk 'bin') (Get-JavaExeName 'java')
        $vJar = $artifacts['integridade-verifier.jar']
        $pJar = $artifacts['iped-viewers-impl-4.3.1.jar']
        & $javaBin -jar $vJar --version
        if ($LASTEXITCODE -ne 0) { throw 'Verifier JAR --version falhou' }
        & $javaBin -jar $vJar --self-test
        if ($LASTEXITCODE -ne 0) { throw 'Verifier JAR --self-test falhou' }
        if ($artifacts.Contains('integridade-verifier.exe')) {
            $exeProc = Start-Process -FilePath $artifacts['integridade-verifier.exe'] -ArgumentList '--self-test' -Wait -PassThru -NoNewWindow
            if ($exeProc.ExitCode -ne 0) { throw "Verifier EXE --self-test falhou ($($exeProc.ExitCode))" }
        }
        $psExe = if ($IsWin) { 'powershell' } else { 'pwsh' }
        & $psExe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $RepoRoot 'instalar_captura_chat_zap/validate-plugin-jar.ps1') -Jar $pJar -ExpectedSha256 $hashes1['iped-viewers-impl-4.3.1.jar']
        if ($LASTEXITCODE -ne 0) { throw 'validate-plugin-jar.ps1 falhou' }
        Write-Host 'POST_BUILD_CHECKS=PASS'
    }
} finally {
    Remove-Item -LiteralPath $workBase -Recurse -Force -ErrorAction SilentlyContinue
}
