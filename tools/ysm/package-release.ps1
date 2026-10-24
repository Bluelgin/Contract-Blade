param(
    [string]$ReleaseFolder = '',
    [string]$Version = '1.1.1'
)
$ErrorActionPreference = 'Stop'
$releaseRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if (-not $ReleaseFolder) { $ReleaseFolder = Join-Path $releaseRoot "dist/ysm/$Version" }
$releaseFolder = [IO.Path]::GetFullPath($ReleaseFolder)
& node (Join-Path $releaseRoot 'tools/ysm/package-release.cjs') $releaseFolder $Version
if ($LASTEXITCODE -ne 0) { throw 'Release staging failed' }
foreach ($releaseId in @('contract_white_fox','contract_black_fox')) {
    # ysm.json must be at the archive root; no extra enclosing directory.
    Compress-Archive -Path (Join-Path $releaseFolder "models/$releaseId/*") -DestinationPath (Join-Path $releaseFolder "$releaseId-ysm-$Version.zip") -CompressionLevel Optimal
}
Compress-Archive -Path (Join-Path $releaseFolder "contract_white_fox-ysm-$Version.zip"),(Join-Path $releaseFolder "contract_black_fox-ysm-$Version.zip"),(Join-Path $releaseFolder 'README.md'),(Join-Path $releaseFolder 'README.en.md') -DestinationPath (Join-Path $releaseFolder "contract-fox-duo-ysm-$Version.zip") -CompressionLevel Optimal
Get-ChildItem -LiteralPath $releaseFolder -Filter '*.zip' | Select-Object Name, Length
