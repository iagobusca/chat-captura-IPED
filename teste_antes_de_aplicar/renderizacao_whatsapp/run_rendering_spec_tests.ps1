$ErrorActionPreference = 'Stop'
$python = (Get-Command python).Source
& $python (Join-Path $PSScriptRoot 'test_rendering_spec.py')
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $python (Join-Path $PSScriptRoot 'test_production_renderer_contract.py')
exit $LASTEXITCODE
