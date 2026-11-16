$ErrorActionPreference = 'Stop'
Remove-Item Env:\PYTHONUTF8 -ErrorAction SilentlyContinue
$python = (Get-Command python).Source
& $python (Join-Path $PSScriptRoot 'test_html_message_spec.py')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $python (Join-Path $PSScriptRoot 'test_pipeline_acceptance.py')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $python (Join-Path $PSScriptRoot 'test_prototype_end_to_end.py')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $python (Join-Path $PSScriptRoot 'test_marker_boundary_contract.py')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $python -m unittest teste_antes_de_aplicar.legacy_migration.test_legacy_migration_spec -v
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $python -m unittest teste_antes_de_aplicar.legacy_migration.test_legacy_migration_production_contract -v
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $python (Join-Path $PSScriptRoot 'test_html_censorship_contract.py')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $python (Join-Path $PSScriptRoot 'test_fase4_spec_validation.py')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$repo = Split-Path -Parent $PSScriptRoot
. (Join-Path $repo 'tests\resolve_test_env.ps1')
$jdk8Home = Resolve-JdkHome -EnvNames @('JAVA8_HOME', 'JAVA_HOME') -MajorVersions @(8) -RequiredTool 'bin\javac.exe'
$javac8 = Join-Path $jdk8Home 'bin\javac.exe'
$java8 = Join-Path $jdk8Home 'bin\java.exe'
$jdkModernHome = Resolve-JdkHome -EnvNames @('JAVA26_HOME', 'JAVA21_HOME', 'JAVA11_HOME', 'JAVA_HOME') -MajorVersions @(26, 25, 24, 23, 22, 21, 17, 11) -RequiredTool 'bin\javac.exe'
$java26 = Join-Path $jdkModernHome 'bin\java.exe'
$javac26 = Join-Path $jdkModernHome 'bin\javac.exe'
$classes = Join-Path $repo 'tmp\verifier-html-v1'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
& $javac8 -source 1.8 -target 1.8 -encoding UTF-8 -cp (Join-Path $repo 'verificador-integridade\integridade-verifier.jar') -d $classes (Join-Path $repo 'verificador-integridade\src\IntegrityVerifier.java') (Join-Path $PSScriptRoot 'PublicManifestComparisonHarness.java')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$pluginJar = Join-Path $repo 'instalar_captura_chat_zap\iped-viewers-impl-4.3.1.jar'
$jsonJar = Join-Path $repo 'instalar_captura_chat_zap\dependencies\json-20260719.jar'
$pluginTestClasses = Join-Path $repo 'tmp\plugin-integrity-tests'
New-Item -ItemType Directory -Force -Path $pluginTestClasses | Out-Null
$pluginTestCp = "$pluginJar;$jsonJar"
& $javac26 -encoding UTF-8 -cp $pluginTestCp -d $pluginTestClasses (Join-Path $repo 'src\iped\viewers\CaptureIntegrity.java') (Join-Path $repo 'src\iped\viewers\CaptureCensorship.java') (Join-Path $repo 'src\iped\viewers\CaptureBoundaryResolver.java') (Join-Path $repo 'src\iped\viewers\VerifierIntegrity.java') (Join-Path $repo 'tests\iped\viewers\CaptureBoundaryResolverTest.java') (Join-Path $repo 'tests\iped\viewers\CaptureCensorshipTest.java') (Join-Path $repo 'tests\iped\viewers\VerifierIntegrityHashTest.java') (Join-Path $repo 'tests\iped\viewers\MigrationIntegrityHistoryHarness.java')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$pluginRuntimeCp = "$pluginTestClasses;$pluginTestCp"
& $java26 -cp $pluginRuntimeCp iped.viewers.CaptureCensorshipTest
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $java26 -cp $pluginRuntimeCp iped.viewers.CaptureBoundaryResolverTest
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $java26 -cp $pluginRuntimeCp iped.viewers.VerifierIntegrityHashTest
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $python (Join-Path $PSScriptRoot 'test_verifier_java_integration.py')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$ipedRoot = Resolve-WorkspaceIpedRoot -RepoRoot $repo
$javaFxLib = Resolve-JavaFxLib -RepoRoot $repo -IpedRoot $ipedRoot
$phase2Classes = Join-Path $repo 'tmp\phase2-plugin-tests'
if (Test-Path $phase2Classes) { Remove-Item -Recurse -Force $phase2Classes }
New-Item -ItemType Directory -Force -Path $phase2Classes | Out-Null
$phase2CompileCp = "$(Join-Path $ipedRoot 'lib\*');$pluginJar"
$phase2Sources = @(Get-ChildItem (Join-Path $repo 'src\iped\viewers') -Filter '*.java' | ForEach-Object { $_.FullName })
& $javac26 -encoding UTF-8 -source 11 -target 11 -Xlint:-options --module-path $javaFxLib --add-modules javafx.controls,javafx.web,javafx.swing -cp $phase2CompileCp -d $phase2Classes `
    $phase2Sources `
    (Join-Path $repo 'tests\iped\viewers\ChatAnnotationStoreRoundTripTest.java') `
    (Join-Path $repo 'tests\iped\viewers\SafeNameTest.java') `
    (Join-Path $repo 'tests\iped\viewers\PathContainmentTest.java') `
    (Join-Path $repo 'tests\iped\viewers\CsvSanitizationTest.java') `
    (Join-Path $repo 'tests\iped\viewers\SwingHtmlDisableTest.java') `
    (Join-Path $repo 'tests\iped\viewers\ChatAnnotationActionDialogTest.java') `
    (Join-Path $repo 'tests\iped\viewers\JsonParsingRegressionTest.java') `
    (Join-Path $repo 'tests\iped\viewers\UiAndAuxFolderRegressionTest.java') `
    (Join-Path $repo 'tests\iped\viewers\OpenCvLoaderTest.java') `
    (Join-Path $repo 'tests\iped\viewers\CaptureIntegrityTest.java') `
    (Join-Path $repo 'tests\iped\viewers\VerifierIntegrityHashTest.java')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$phase2RuntimeCp = "$phase2Classes;$phase2CompileCp"
foreach ($cls in @(
    'iped.viewers.ChatAnnotationStoreRoundTripTest',
    'iped.viewers.SafeNameTest',
    'iped.viewers.PathContainmentTest',
    'iped.viewers.CsvSanitizationTest',
    'iped.viewers.SwingHtmlDisableTest',
    'iped.viewers.ChatAnnotationActionDialogTest',
    'iped.viewers.JsonParsingRegressionTest',
    'iped.viewers.UiAndAuxFolderRegressionTest',
    'iped.viewers.CaptureIntegrityTest',
    'iped.viewers.VerifierIntegrityHashTest'
)) {
    & $java26 --module-path $javaFxLib --add-modules javafx.controls,javafx.web,javafx.swing -cp $phase2RuntimeCp $cls
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
& $java26 --module-path $javaFxLib --add-modules javafx.controls,javafx.web,javafx.swing -cp $phase2RuntimeCp iped.viewers.OpenCvLoaderTest (Join-Path $ipedRoot 'opencv_java4120.dll')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$jdk8Classes = Join-Path $repo 'tmp\phase2-verifier-tests'
if (Test-Path $jdk8Classes) { Remove-Item -Recurse -Force $jdk8Classes }
New-Item -ItemType Directory -Force -Path $jdk8Classes | Out-Null
$jdk8Deps = Join-Path $repo 'verificador-integridade\dependencies\*'
& $javac8 -encoding UTF-8 -cp $jdk8Deps -d $jdk8Classes `
    (Join-Path $repo 'verificador-integridade\src\IntegrityVerifier.java') `
    (Join-Path $repo 'verificador-integridade\tests\SecurityRegressionTest.java') `
    (Join-Path $repo 'verificador-integridade\tests\IndexHashParityTest.java')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $java8 -cp "$jdk8Classes;$jdk8Deps" SecurityRegressionTest
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $java8 -cp "$jdk8Classes;$jdk8Deps" IndexHashParityTest
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Remove-Item -Recurse -Force $phase2Classes, $jdk8Classes -ErrorAction SilentlyContinue
Write-Output 'ALL_RUN_ALL_TESTS_PASSED'
