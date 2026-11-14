param(
    [string]$IpedRoot = '',
    [string]$JavaFxLib = '',
    [string]$JdkHome = ''
)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'resolve_test_env.ps1')
$IpedRoot = Resolve-WorkspaceIpedRoot -Explicit $IpedRoot -RepoRoot $repoRoot
$JavaFxLib = Resolve-JavaFxLib -Explicit $JavaFxLib -RepoRoot $repoRoot -IpedRoot $IpedRoot
$JdkHome = Resolve-JdkHome -Explicit $JdkHome -EnvNames @('JAVA26_HOME', 'JAVA21_HOME', 'JAVA11_HOME', 'JAVA_HOME') -MajorVersions @(26, 25, 24, 23, 22, 21, 17, 11) -RequiredTool 'bin\javac.exe'
$pluginJar = Join-Path $repoRoot 'instalar_captura_chat_zap\iped-viewers-impl-4.3.1.jar'
$javac = Join-Path $JdkHome 'bin\javac.exe'
$java = Join-Path $JdkHome 'bin\java.exe'
$testClasses = Join-Path $repoRoot 'tmp\svg-sanitizer-compatibility-test'
if (!(Test-Path $javac) -or !(Test-Path $java)) { throw "JDK não encontrado: $JdkHome" }
if (!(Test-Path (Join-Path $IpedRoot 'lib\xercesImpl-2.12.2.jar'))) { throw "Xerces do IPED 4.3.1 não encontrado em $IpedRoot" }
if (!(Test-Path $JavaFxLib)) { throw "JavaFX necessário para carregar HtmlLinkViewer não encontrado: $JavaFxLib" }
if (!(Test-Path $pluginJar)) { throw "JAR do plugin não encontrado: $pluginJar" }
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
$source = Join-Path $PSScriptRoot 'iped\viewers\CaptureSvgCompatibilityTest.java'
$compileCp = "$pluginJar;$(Join-Path $IpedRoot 'lib\*')"
& $javac -encoding UTF-8 -cp $compileCp -d $testClasses $source
if ($LASTEXITCODE -ne 0) { throw "javac falhou: $LASTEXITCODE" }
$runtimeCp = "$testClasses;$pluginJar;$(Join-Path $IpedRoot 'lib\*')"
& $java "--module-path=$JavaFxLib" --add-modules javafx.controls,javafx.web,javafx.swing '-Djavax.xml.parsers.DocumentBuilderFactory=org.apache.xerces.jaxp.DocumentBuilderFactoryImpl' -cp $runtimeCp iped.viewers.CaptureSvgCompatibilityTest
if ($LASTEXITCODE -ne 0) { throw "Teste de compatibilidade SVG falhou: $LASTEXITCODE" }
