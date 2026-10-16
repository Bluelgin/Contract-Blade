$ErrorActionPreference = 'Stop'
$releaseRoot = 'E:/Little maid'
& node (Join-Path $releaseRoot 'tools/ysm/package-release.cjs')
if ($LASTEXITCODE -ne 0) { throw 'Release staging failed' }
$releaseFolder = Join-Path $releaseRoot 'dist/ysm/1.1.0'
foreach ($releaseId in @('contract_akatsuki','contract_white_fox','contract_black_fox')) {
    # ysm.json must be at the archive root; no extra enclosing directory.
    Compress-Archive -Path (Join-Path $releaseFolder "models/$releaseId/*") -DestinationPath (Join-Path $releaseFolder "$releaseId-ysm-1.1.0.zip") -CompressionLevel Optimal
}
Compress-Archive -Path (Join-Path $releaseFolder '*-ysm-1.1.0.zip'),(Join-Path $releaseFolder 'README.md') -DestinationPath (Join-Path $releaseFolder 'contract-fox-trio-ysm-1.1.0.zip') -CompressionLevel Optimal
Get-ChildItem -LiteralPath $releaseFolder -Filter '*.zip' | Select-Object Name, Length
