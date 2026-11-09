param(
    [string]$Java8 = 'C:\Program Files\Eclipse Adoptium\jdk-8.0.512.9-hotspot',
    [string]$Java11 = 'C:\Program Files\Eclipse Adoptium\jdk-11.0.32.101-hotspot',
    [string]$JdkWithJpackage = 'C:\Program Files\Eclipse Adoptium\jdk-26.0.2.101-hotspot',
    [string]$JsonJar = '',
    [string]$Output = ''
)
$ErrorActionPreference = 'Stop'
$Base = $PSScriptRoot
if (!$JsonJar) { $JsonJar = Join-Path $Base 'dependencies\json-20260719.jar' }
if (!$Output) { $Output = Join-Path $Base 'dist' }
$Work = Join-Path $Base 'build-single-exe\1.3.4'
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
@('Manifest-Version: 1.0','Main-Class: IntegrityVerifier','Implementation-Version: 1.3.4','') | Set-Content -LiteralPath $Manifest -Encoding ASCII
& (Join-Path $Java8 'bin\jar.exe') cfm $Jar $Manifest -C $Classes .
if ($LASTEXITCODE) { throw 'Falha ao montar o JAR Java.' }
$JsonClasses = Join-Path $Work 'json-classes'
New-Item -ItemType Directory -Force -Path $JsonClasses | Out-Null
Push-Location $JsonClasses
try { & (Join-Path $Java8 'bin\jar.exe') xf $JsonJar; if ($LASTEXITCODE) { throw 'Falha ao extrair biblioteca JSON para o pacote standalone.' } } finally { Pop-Location }
& (Join-Path $Java8 'bin\jar.exe') uf $Jar -C $JsonClasses org/json
if ($LASTEXITCODE) { throw 'Falha ao embutir biblioteca JSON no JAR standalone.' }
Copy-Item $Jar (Join-Path $Base 'integridade-verifier.jar') -Force
& (Join-Path $Java11 'bin\jlink.exe') --module-path (Join-Path $Java11 'jmods') --add-modules java.base,java.desktop --strip-debug --no-header-files --no-man-pages --output $Runtime
if ($LASTEXITCODE) { throw 'Falha ao criar runtime Java 11 reduzido.' }
New-Item -ItemType Directory -Force -Path $AppImage | Out-Null
& (Join-Path $JdkWithJpackage 'bin\jpackage.exe') --type app-image --name integridade-verifier --input $Input --main-jar integridade-verifier.jar --main-class IntegrityVerifier --runtime-image $Runtime --dest $AppImage --app-version 1.3.4
if ($LASTEXITCODE) { throw 'Falha ao criar imagem Java empacotada.' }
$Rows = Get-ChildItem -LiteralPath $PayloadDir -File -Recurse | ForEach-Object {
    $Relative = $_.FullName.Substring($PayloadDir.Length).TrimStart('\','/').Replace('\','/')
    $Hash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    "$Hash|$Relative"
}
Set-Content -LiteralPath (Join-Path $Base 'launcher\payload.sha256') -Value $Rows -Encoding UTF8
Remove-Item -LiteralPath (Join-Path $Base 'launcher\payload.zip') -Force -ErrorAction SilentlyContinue
Compress-Archive -Path (Join-Path $PayloadDir '*') -DestinationPath (Join-Path $Base 'launcher\payload.zip') -CompressionLevel Optimal
& dotnet publish (Join-Path $Base 'launcher\Launcher.csproj') --configfile (Join-Path $Base 'nuget.config') --ignore-failed-sources -p:NuGetAudit=false -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true -o (Join-Path $Base 'launcher\publish')
if ($LASTEXITCODE) { throw 'Falha ao compilar o lançador EXE autoextraível.' }
Copy-Item (Join-Path $Base 'launcher\publish\integridade-verifier.exe') (Join-Path $Output 'integridade-verifier.exe') -Force
Write-Host "JAR Java 8: $Base\integridade-verifier.jar"
Write-Host "EXE autoextraível: $Output\integridade-verifier.exe"
