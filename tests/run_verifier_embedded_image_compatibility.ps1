$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$verifier = Join-Path $repoRoot 'verificador-integridade\integridade-verifier.jar'
$jdkHome = 'C:\Program Files\Eclipse Adoptium\jdk-8.0.512.9-hotspot'
$out = Join-Path $repoRoot 'tmp\verifier-embedded-image-compatibility'
$javac = Join-Path $jdkHome 'bin\javac.exe'
$java = Join-Path $jdkHome 'bin\java.exe'
$jsonJar = Join-Path $repoRoot 'verificador-integridade\dependencies\json-20260719.jar'
foreach ($path in @($verifier, $javac, $java, $jsonJar)) { if (!(Test-Path $path)) { throw "Dependência não encontrada: $path" } }
New-Item -ItemType Directory -Force -Path $out | Out-Null
$test = Join-Path $PSScriptRoot 'verifier\EmbeddedImageMimeCompatibilityTest.java'
& $javac -source 1.8 -target 1.8 -encoding UTF-8 -cp "$verifier;$jsonJar" -d $out $test
if ($LASTEXITCODE -ne 0) { throw "Compilação da regressão falhou: $LASTEXITCODE" }
& $java -cp "$out;$verifier" EmbeddedImageMimeCompatibilityTest
if ($LASTEXITCODE -ne 0) { throw "Regressão do MIME legado falhou: $LASTEXITCODE" }
