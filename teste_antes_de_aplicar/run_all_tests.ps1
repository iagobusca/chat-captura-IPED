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
$repo = Split-Path -Parent $PSScriptRoot
$classes = Join-Path $repo 'tmp\verifier-html-v1'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
& 'C:\Program Files\Eclipse Adoptium\jdk-8.0.512.9-hotspot\bin\javac.exe' -source 1.8 -target 1.8 -encoding UTF-8 -cp (Join-Path $repo 'verificador-integridade\integridade-verifier.jar') -d $classes (Join-Path $repo 'verificador-integridade\src\IntegrityVerifier.java') (Join-Path $PSScriptRoot 'PublicManifestComparisonHarness.java')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$pluginJar = Join-Path $repo 'instalar_captura_chat_zap\iped-viewers-impl-4.3.1.jar'
$jsonJar = Join-Path $repo 'instalar_captura_chat_zap\dependencies\json-20260719.jar'
$pluginTestClasses = Join-Path $repo 'tmp\plugin-integrity-tests'
New-Item -ItemType Directory -Force -Path $pluginTestClasses | Out-Null
$pluginTestCp = "$pluginJar;$jsonJar"
& 'C:\Program Files\Eclipse Adoptium\jdk-26.0.2.101-hotspot\bin\javac.exe' -encoding UTF-8 -cp $pluginTestCp -d $pluginTestClasses (Join-Path $repo 'src\iped\viewers\CaptureIntegrity.java') (Join-Path $repo 'src\iped\viewers\CaptureCensorship.java') (Join-Path $repo 'src\iped\viewers\CaptureBoundaryResolver.java') (Join-Path $repo 'tests\iped\viewers\CaptureBoundaryResolverTest.java') (Join-Path $repo 'tests\iped\viewers\CaptureIntegrityTest.java') (Join-Path $repo 'tests\iped\viewers\CaptureCensorshipTest.java') (Join-Path $repo 'tests\iped\viewers\VerifierIntegrityHashTest.java') (Join-Path $repo 'tests\iped\viewers\MigrationIntegrityHistoryHarness.java')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$pluginRuntimeCp = "$pluginTestClasses;$pluginTestCp"
& 'C:\Program Files\Eclipse Adoptium\jdk-26.0.2.101-hotspot\bin\java.exe' -cp $pluginRuntimeCp iped.viewers.CaptureIntegrityTest
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& 'C:\Program Files\Eclipse Adoptium\jdk-26.0.2.101-hotspot\bin\java.exe' -cp $pluginRuntimeCp iped.viewers.CaptureCensorshipTest
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& 'C:\Program Files\Eclipse Adoptium\jdk-26.0.2.101-hotspot\bin\java.exe' -cp $pluginRuntimeCp iped.viewers.CaptureBoundaryResolverTest
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& 'C:\Program Files\Eclipse Adoptium\jdk-26.0.2.101-hotspot\bin\java.exe' -cp $pluginRuntimeCp iped.viewers.VerifierIntegrityHashTest
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $python (Join-Path $PSScriptRoot 'test_verifier_java_integration.py')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
