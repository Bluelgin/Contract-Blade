$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$taskRoot = 'E:/Little maid'
$taskRelease = Join-Path $taskRoot 'dist/ysm/1.1.0'
$taskManifest = Get-Content -LiteralPath (Join-Path $taskRoot 'tmp/ysm-release-manifest.json') -Raw | ConvertFrom-Json
function Read-EntryHash($taskZip, $taskName) {
    $taskEntry = $taskZip.Entries | Where-Object { $_.FullName.Replace('\','/') -eq $taskName } | Select-Object -First 1
    if (-not $taskEntry) { throw "Missing entry: $taskName" }
    $taskStream = $taskEntry.Open()
    try { return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($taskStream)) } finally { $taskStream.Dispose() }
}
foreach ($taskModel in $taskManifest) {
    $taskOld = [IO.Compression.ZipFile]::OpenRead((Join-Path $taskRoot "dist/ysm/1.0.0/$($taskModel.id)-ysm-1.0.0.zip"))
    $taskNew = [IO.Compression.ZipFile]::OpenRead((Join-Path $taskRelease "$($taskModel.id)-ysm-1.1.0.zip"))
    try {
        foreach ($taskName in $taskModel.files) {
            $taskProtected = if ($taskModel.id -eq 'contract_akatsuki') { $taskName.StartsWith('animations/') } else { $taskName -match '^(models|textures|animations)/' }
            if ($taskProtected -and (Read-EntryHash $taskOld $taskName) -ne (Read-EntryHash $taskNew $taskName)) { throw "Unrequested appearance or animation change: $($taskModel.id)/$taskName" }
        }
        Write-Output "$($taskModel.id): prior animations preserved; black/white geometry and texture preserved."
    } finally { $taskOld.Dispose(); $taskNew.Dispose() }
}
$taskBundle = [IO.Compression.ZipFile]::OpenRead((Join-Path $taskRelease 'contract-fox-trio-ysm-1.1.0.zip'))
try {
    if ($taskBundle.Entries.Count -ne 4) { throw 'Unexpected trio bundle files' }
    foreach ($taskName in @('contract_akatsuki-ysm-1.1.0.zip','contract_white_fox-ysm-1.1.0.zip','contract_black_fox-ysm-1.1.0.zip','README.md')) {
        if ((Read-EntryHash $taskBundle $taskName) -ne (Get-FileHash -LiteralPath (Join-Path $taskRelease $taskName)).Hash) { throw "Bundle mismatch: $taskName" }
    }
} finally { $taskBundle.Dispose() }
Write-Output 'TRIO_UPDATE_PASS: three standalone models and player instructions match the bundle.'
