$ErrorActionPreference = 'Stop'
$modelRoot = Split-Path $PSScriptRoot
$modelStage = & node (Join-Path $PSScriptRoot 'build_akatsuki_model_pack.cjs')
if ($LASTEXITCODE -ne 0) { throw 'Akatsuki pack assembly failed.' }
$modelStage = [IO.Path]::GetFullPath($modelStage.Trim())
if (-not $modelStage.StartsWith((Join-Path $modelRoot 'tmp/akatsuki-pack-'), [StringComparison]::OrdinalIgnoreCase)) { throw 'Unexpected stage path.' }
$modelTarget = Join-Path $modelRoot 'src/main/resources/modelpacks/contract-akatsuki-1.0.0.zip'
$modelTemporary = Join-Path $modelStage 'bundle.zip'
$modelZip = [IO.Compression.ZipFile]::Open($modelTemporary, [IO.Compression.ZipArchiveMode]::Create)
try {
    # Native TLM discovers namespaces through explicit assets/<namespace>/ directory entries.
    foreach ($modelDirectory in (Get-ChildItem -LiteralPath $modelStage -Recurse -Directory | Sort-Object FullName)) {
        $modelRelative = [IO.Path]::GetRelativePath($modelStage, $modelDirectory.FullName).Replace('\', '/') + '/'
        $modelEntry = $modelZip.CreateEntry($modelRelative)
        $modelEntry.LastWriteTime = [DateTimeOffset]::new(2026, 10, 10, 0, 0, 0, [TimeSpan]::Zero)
    }
    foreach ($modelFile in (Get-ChildItem -LiteralPath $modelStage -Recurse -File | Where-Object Name -ne 'bundle.zip' | Sort-Object FullName)) {
        $modelRelative = [IO.Path]::GetRelativePath($modelStage, $modelFile.FullName).Replace('\', '/')
        $modelEntry = $modelZip.CreateEntry($modelRelative, [IO.Compression.CompressionLevel]::Optimal)
        $modelEntry.LastWriteTime = [DateTimeOffset]::new(2026, 10, 10, 0, 0, 0, [TimeSpan]::Zero)
        $modelInput = [IO.File]::OpenRead($modelFile.FullName)
        $modelOutput = $modelEntry.Open()
        try { $modelInput.CopyTo($modelOutput) } finally { $modelOutput.Dispose(); $modelInput.Dispose() }
    }
} finally { $modelZip.Dispose() }
[IO.File]::Copy($modelTemporary, $modelTarget, $true)
Write-Output "Built native Akatsuki pack: $modelTarget"
Write-Output "Native validation stage: $modelStage"
Get-FileHash -LiteralPath $modelTarget -Algorithm SHA256
