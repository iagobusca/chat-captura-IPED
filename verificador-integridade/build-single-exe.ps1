param(
    [string]$Java8 = '',
    [string]$Java11 = '',
    [string]$JdkWithJpackage = '',
    [string]$JsonJar = '',
    [string]$Output = '',
    [switch]$OfflineBuild
)
$ErrorActionPreference = 'Stop'
$Base = $PSScriptRoot

function Get-JavaMajorFromHome {
    param([string]$HomeDir)
    $releaseFile = Join-Path $HomeDir 'release'
    if (Test-Path -LiteralPath $releaseFile) {
        $line = Get-Content -LiteralPath $releaseFile -ErrorAction SilentlyContinue | Where-Object { $_ -match '^JAVA_VERSION=' } | Select-Object -First 1
        if ($line -match 'JAVA_VERSION="1\.(\d+)') { return [int]$Matches[1] }
        if ($line -match 'JAVA_VERSION="(\d+)') { return [int]$Matches[1] }
    }
    return 0
}

function Resolve-JdkHome {
    param([string]$Explicit, [string[]]$EnvNames, [int[]]$Majors, [string]$Tool)
    if ($Explicit) {
        if (!(Test-Path -LiteralPath (Join-Path $Explicit $Tool))) { throw "JDK invalido ($Tool ausente): $Explicit" }
        return $Explicit
    }
    foreach ($name in ($EnvNames | Where-Object { $_ -ne 'JAVA_HOME' })) {
        $val = [Environment]::GetEnvironmentVariable($name)
        if ($val -and (Test-Path -LiteralPath (Join-Path $val $Tool)) -and (Test-Path -LiteralPath (Join-Path $val 'bin\javac.exe')) -and ($Majors -contains (Get-JavaMajorFromHome $val))) { return $val }
    }
    $roots = @($env:ProgramFiles, ${env:ProgramFiles(x86)}, (Join-Path $env:LOCALAPPDATA 'Programs'), (Join-Path $env:USERPROFILE '.jdks')) |
        Where-Object { $_ -and (Test-Path -LiteralPath $_) } | Select-Object -Unique
    $vendors = @('Eclipse Adoptium', 'Java', 'Microsoft', 'Zulu', 'BellSoft', 'Amazon Corretto', 'Temurin', '')
    $found = New-Object System.Collections.Generic.List[object]
    foreach ($r in $roots) {
        foreach ($v in $vendors) {
            $dir = if ($v) { Join-Path $r $v } else { $r }
            if (!(Test-Path -LiteralPath $dir)) { continue }
            foreach ($child in (Get-ChildItem -LiteralPath $dir -Directory -ErrorAction SilentlyContinue)) {
                if (!(Test-Path -LiteralPath (Join-Path $child.FullName $Tool)) -or !(Test-Path -LiteralPath (Join-Path $child.FullName 'bin\javac.exe'))) { continue }
                $major = Get-JavaMajorFromHome $child.FullName
                if ($Majors -contains $major) {
                    $found.Add([pscustomobject]@{ Rank = [Array]::IndexOf($Majors, $major); Name = $child.Name; Path = $child.FullName })
                }
            }
        }
    }
    $pick = $found | Sort-Object Rank, @{ Expression = 'Name'; Descending = $true } | Select-Object -First 1
    if ($pick) { return $pick.Path }
    if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME $Tool)) -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\javac.exe')) -and ($Majors -contains (Get-JavaMajorFromHome $env:JAVA_HOME))) { return $env:JAVA_HOME }
    throw "JDK compativel nao encontrado dinamicamente (majors=$($Majors -join ','); tool=$Tool)."
}

$Java8 = Resolve-JdkHome -Explicit $Java8 -EnvNames @('JAVA8_HOME', 'JAVA_HOME') -Majors @(8) -Tool 'bin\javac.exe'
$Java11 = Resolve-JdkHome -Explicit $Java11 -EnvNames @('JAVA11_HOME', 'JAVA_HOME') -Majors @(11) -Tool 'bin\jlink.exe'
$JdkWithJpackage = Resolve-JdkHome -Explicit $JdkWithJpackage -EnvNames @('JAVA26_HOME', 'JAVA21_HOME', 'JAVA17_HOME', 'JAVA_HOME') -Majors @(26, 25, 24, 23, 22, 21, 17, 16, 15, 14) -Tool 'bin\jpackage.exe'

if (!$JsonJar) { $JsonJar = Join-Path $Base 'dependencies\json-20260719.jar' }
if (!$Output) { $Output = Join-Path $Base 'dist' }
$Work = Join-Path $Base 'build-single-exe\1.5.10'
$Classes = Join-Path $Work 'classes'
$Input = Join-Path $Work 'input'
$Runtime = Join-Path $Work 'runtime11'
$AppImage = Join-Path $Work 'app-image'
$PayloadDir = Join-Path $AppImage 'integridade-verifier'
Remove-Item -LiteralPath $Work -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $Classes,$Input,$Output | Out-Null
if (!(Test-Path -LiteralPath $JsonJar)) { throw "Biblioteca JSON não encontrada: $JsonJar" }
& (Join-Path $Java8 'bin\javac.exe') -encoding UTF-8 -source 1.8 -target 1.8 -cp $JsonJar -d $Classes (Join-Path $Base 'src\IntegrityVerifier.java')
if ($LASTEXITCODE) { throw 'Falha ao compilar o verificador Java 8.' }
$Icon = Join-Path $Base 'imagem doc\verificador-icon.png'
if (Test-Path -LiteralPath $Icon) {
    New-Item -ItemType Directory -Force -Path (Join-Path $Classes 'imagem doc') | Out-Null
    Copy-Item -LiteralPath $Icon -Destination (Join-Path $Classes 'imagem doc\verificador-icon.png') -Force
}
$Jar = Join-Path $Input 'integridade-verifier.jar'
$Manifest = Join-Path $Work 'MANIFEST.MF'
@('Manifest-Version: 1.0','Main-Class: IntegrityVerifier','Implementation-Version: 1.5.10','') | Set-Content -LiteralPath $Manifest -Encoding ASCII
& (Join-Path $Java8 'bin\jar.exe') cfm $Jar $Manifest -C $Classes .
if ($LASTEXITCODE) { throw 'Falha ao montar o JAR Java.' }
$JsonClasses = Join-Path $Work 'json-classes'
New-Item -ItemType Directory -Force -Path $JsonClasses | Out-Null
Push-Location $JsonClasses
try { & (Join-Path $Java8 'bin\jar.exe') xf $JsonJar; if ($LASTEXITCODE) { throw 'Falha ao extrair biblioteca JSON para o pacote standalone.' } } finally { Pop-Location }
& (Join-Path $Java8 'bin\jar.exe') uf $Jar -C $JsonClasses org/json
if ($LASTEXITCODE) { throw 'Falha ao embutir biblioteca JSON no JAR standalone.' }
$SqliteExpectedSha = '0CDAB410947E04B6743DF99CF1543267DDD107357D6F76948D145BE590FD497D'
$SqliteJar = Join-Path $Base 'dependencies\sqlite-jdbc-3.41.2.2.jar'
if (!(Test-Path -LiteralPath $SqliteJar)) {
    $SqliteJar = Join-Path (Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $Base))) 'iped-4.3.1\lib\sqlite-jdbc-3.41.2.2.jar'
}
if (!(Test-Path -LiteralPath $SqliteJar)) { throw "Biblioteca SQLite oficial nao encontrada: $SqliteJar" }
$SqliteActualSha = (Get-FileHash -LiteralPath $SqliteJar -Algorithm SHA256).Hash.ToUpperInvariant()
if ($SqliteActualSha -ne $SqliteExpectedSha) { throw "SHA-256 invalido para sqlite-jdbc-3.41.2.2.jar: $SqliteActualSha != $SqliteExpectedSha" }
$SqliteClasses = Join-Path $Work 'sqlite-classes'
New-Item -ItemType Directory -Force -Path $SqliteClasses | Out-Null
Push-Location $SqliteClasses
try { & (Join-Path $Java8 'bin\jar.exe') xf $SqliteJar; if ($LASTEXITCODE) { throw 'Falha ao extrair sqlite-jdbc para o pacote standalone.' } } finally { Pop-Location }
& (Join-Path $Java8 'bin\jar.exe') uf $Jar -C $SqliteClasses org/sqlite -C $SqliteClasses sqlite-jdbc.properties -C $SqliteClasses META-INF/services -C $SqliteClasses META-INF/maven
if ($LASTEXITCODE) { throw 'Falha ao embutir sqlite-jdbc no JAR standalone.' }
Copy-Item $Jar (Join-Path $Base 'integridade-verifier.jar') -Force
& (Join-Path $Java11 'bin\jlink.exe') --module-path (Join-Path $Java11 'jmods') --add-modules java.base,java.datatransfer,java.desktop,java.xml,java.logging,java.sql --strip-debug --no-header-files --no-man-pages --output $Runtime
if ($LASTEXITCODE) { throw 'Falha ao criar runtime Java 11 reduzido.' }
New-Item -ItemType Directory -Force -Path $AppImage | Out-Null
& (Join-Path $JdkWithJpackage 'bin\jpackage.exe') --type app-image --name integridade-verifier --input $Input --main-jar integridade-verifier.jar --main-class IntegrityVerifier --runtime-image $Runtime --dest $AppImage --app-version 1.5.10
if ($LASTEXITCODE) { throw 'Falha ao criar imagem Java empacotada.' }
$Rows = Get-ChildItem -LiteralPath $PayloadDir -File -Recurse | ForEach-Object {
    $Relative = $_.FullName.Substring($PayloadDir.Length).TrimStart('\','/').Replace('\','/')
    $Hash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    "$Hash|$Relative"
}
Set-Content -LiteralPath (Join-Path $Base 'launcher\payload.sha256') -Value $Rows -Encoding UTF8
Remove-Item -LiteralPath (Join-Path $Base 'launcher\payload.zip') -Force -ErrorAction SilentlyContinue
Compress-Archive -Path (Join-Path $PayloadDir '*') -DestinationPath (Join-Path $Base 'launcher\payload.zip') -CompressionLevel Optimal
$env:MSBUILDDISABLENODEREUSE = '1'
$env:DOTNET_CLI_DO_NOT_USE_MSBUILD_SERVER = '1'
$dotnetArgs = @(
    'publish', (Join-Path $Base 'launcher\Launcher.csproj'),
    '--disable-build-servers',
    '--configfile', (Join-Path $Base 'nuget.config'),
    '--ignore-failed-sources',
    '-p:UseSharedCompilation=false',
    '-m:1',
    '-nodeReuse:false',
    '-c', 'Release',
    '-r', 'win-x64',
    '--self-contained', 'true',
    '-p:PublishSingleFile=true',
    '-p:IncludeNativeLibrariesForSelfExtract=true',
    '-o', (Join-Path $Base 'launcher\publish')
)
if ($OfflineBuild) { $dotnetArgs += '-p:NuGetAudit=false' }
& dotnet @dotnetArgs
$publishExit = $LASTEXITCODE
try { & dotnet build-server shutdown | Out-Null } catch {}
if ($publishExit) { throw 'Falha ao compilar o lançador EXE autoextraível.' }
$depsJson = Get-ChildItem -LiteralPath (Join-Path $Base 'launcher') -Filter 'integridade-verifier.deps.json' -Recurse -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
if (!$depsJson) { throw 'integridade-verifier.deps.json nao encontrado apos dotnet publish.' }
$depsContent = [IO.File]::ReadAllText($depsJson.FullName)
if ($depsContent -notmatch 'Microsoft\.NETCore\.App\.Runtime\.win-x64/([0-9]+\.[0-9]+\.[0-9]+)') {
    throw "Versao do runtime .NET win-x64 nao identificada em $($depsJson.FullName)"
}
$embeddedNetRuntime = [version]$Matches[1]
if ($embeddedNetRuntime -lt [version]'10.0.15') {
    throw "Runtime .NET inseguro detectado ($embeddedNetRuntime < 10.0.15). Verifique conectividade com nuget.org."
}
if ($depsContent -notmatch 'Microsoft\.WindowsDesktop\.App\.Runtime\.win-x64/([0-9]+\.[0-9]+\.[0-9]+)' -or [version]$Matches[1] -lt [version]'10.0.15') {
    throw "Runtime WindowsDesktop inseguro detectado em $($depsJson.FullName)"
}
Write-Host "Runtime .NET embutido validado: $embeddedNetRuntime (WindowsDesktop $($Matches[1]))"
Copy-Item (Join-Path $Base 'launcher\publish\integridade-verifier.exe') (Join-Path $Output 'integridade-verifier.exe') -Force
Write-Host "JAR Java 8: $Base\integridade-verifier.jar"
Write-Host "EXE autoextraível: $Output\integridade-verifier.exe"
