param(
    [string]$IpedRoot = '',
    [string]$JavaFxLib = '',
    [string]$OpenCvJar = '',
    [string]$JdkHome = ''
)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'resolve_test_env.ps1')
$IpedRoot = Resolve-WorkspaceIpedRoot -Explicit $IpedRoot -RepoRoot $repoRoot -PreferTestIped
$JavaFxLib = Resolve-JavaFxLib -Explicit $JavaFxLib -RepoRoot $repoRoot -IpedRoot $IpedRoot
$OpenCvJar = Resolve-OpenCvJar -Explicit $OpenCvJar -RepoRoot $repoRoot -IpedRoot $IpedRoot
$JdkHome = Resolve-JdkHome -Explicit $JdkHome -EnvNames @('JAVA26_HOME', 'JAVA21_HOME', 'JAVA11_HOME', 'JAVA_HOME') -MajorVersions @(26, 25, 24, 23, 22, 21, 17, 11) -RequiredTool 'bin\javac.exe'
$pluginJar = Join-Path $repoRoot 'instalar_captura_chat_zap\iped-viewers-impl-4.3.1.jar'
$verifierJar = Join-Path $repoRoot 'verificador-integridade\integridade-verifier.jar'
$out = Join-Path $repoRoot 'tmp\capture-header-mime-regression-java11'
$javac = Join-Path $JdkHome 'bin\javac.exe'
$java = Join-Path $JdkHome 'bin\java.exe'
foreach ($path in @($pluginJar, $verifierJar, $OpenCvJar, $javac, $java, $JavaFxLib, (Join-Path $IpedRoot 'lib'))) {
    if (!(Test-Path $path)) { throw "Dependência não encontrada: $path" }
}
New-Item -ItemType Directory -Force -Path $out | Out-Null
$compileCp = "$pluginJar;$verifierJar;$OpenCvJar;$(Join-Path $IpedRoot 'lib\*')"
$source = Join-Path $repoRoot 'src\iped\viewers\HtmlLinkViewer.java'
$test = Join-Path $PSScriptRoot 'iped\viewers\CaptureHeaderEmbeddedImageMimeTest.java'
& $javac -source 11 -target 11 --module-path $JavaFxLib --add-modules javafx.controls,javafx.web,javafx.swing -encoding UTF-8 -cp $compileCp -d $out $source $test
if ($LASTEXITCODE -ne 0) { throw "Compilação do teste falhou: $LASTEXITCODE" }
$runtimeCp = "$out;$pluginJar;$verifierJar;$OpenCvJar;$(Join-Path $IpedRoot 'lib\*')"
& $java "--module-path=$JavaFxLib" --add-modules javafx.controls,javafx.web,javafx.swing -cp $runtimeCp iped.viewers.CaptureHeaderEmbeddedImageMimeTest
if ($LASTEXITCODE -ne 0) { throw "Teste de regressão falhou: $LASTEXITCODE" }
