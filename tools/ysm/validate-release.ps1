param(
    [string]$ReleaseFolder = '',
    [string]$Version = '1.1.1',
    [switch]$CheckMaidRegression
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$releaseRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if (-not $ReleaseFolder) { $ReleaseFolder = Join-Path $releaseRoot "dist/ysm/$Version" }
$releaseManifest = Get-Content -LiteralPath (Join-Path $ReleaseFolder 'release-manifest.json') -Raw | ConvertFrom-Json
$releaseRows = @()
foreach ($releaseModel in $releaseManifest) {
    $releaseId = $releaseModel.id
    $releaseZip = Join-Path $ReleaseFolder "$releaseId-ysm-$Version.zip"
    $releaseArchive = [IO.Compression.ZipFile]::OpenRead($releaseZip)
    try {
        $releaseEntries = @{}
        foreach ($releaseEntry in $releaseArchive.Entries) {
            $releaseName = $releaseEntry.FullName.Replace('\','/')
            if ($releaseName.EndsWith('/')) { continue }
            if ($releaseName -match '\.\.|^[A-Za-z]:|^/' -or $releaseEntries.ContainsKey($releaseName)) { throw "Unsafe archive entry: $releaseName" }
            $releaseEntries[$releaseName] = $releaseEntry
        }
        if ($releaseEntries.Count -ne $releaseModel.files.Count) { throw "Unexpected release files: $releaseId" }
        foreach ($releaseName in $releaseModel.files) {
            if (-not $releaseEntries.ContainsKey($releaseName)) { throw "Missing $releaseName" }
            $releaseStream = $releaseEntries[$releaseName].Open()
            try { $releaseHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($releaseStream)) } finally { $releaseStream.Dispose() }
            $releaseStaged = Join-Path $ReleaseFolder "models/$releaseId/$releaseName"
            if ($releaseHash -ne (Get-FileHash -LiteralPath $releaseStaged).Hash) { throw "ZIP mismatch: $releaseName" }
            if ($releaseName -match '^(models|textures|animations)/') {
                if ($releaseHash -ne (Get-FileHash -LiteralPath (Join-Path $releaseRoot "art/ysm-test/models/$releaseId/$releaseName")).Hash) { throw "Approved model changed: $releaseName" }
            }
            if ($releaseName -in @('ysm.json','README.md','README.en.md','CREDITS.md')) {
                $releaseText = Get-Content -LiteralPath $releaseStaged -Raw
                if ($releaseText -match '(?i)((?<![A-Za-z])[A-Z]:[/\\]|local test|本地测试|开发者|second_order|bone_pos|RESOURCE_NOTES|\.bbmodel|retarget)') { throw "Developer-only text: $releaseName" }
            }
        }
        $releaseMetadata = Get-Content -LiteralPath (Join-Path $ReleaseFolder "models/$releaseId/ysm.json") -Raw | ConvertFrom-Json
        if ($releaseMetadata.metadata.license.type -ne 'CC BY-NC-SA 4.0') { throw "License mismatch" }
        $releaseGeo = Get-Content -LiteralPath (Join-Path $ReleaseFolder "models/$releaseId/models/main.json") -Raw | ConvertFrom-Json
        $releaseBoneNames = @($releaseGeo.'minecraft:geometry'[0].bones.name)
        $releaseClips = 0
        foreach ($releasePath in $releaseMetadata.files.player.animation.PSObject.Properties.Value) {
            $releaseAnimation = Get-Content -LiteralPath (Join-Path $ReleaseFolder "models/$releaseId/$releasePath") -Raw | ConvertFrom-Json
            foreach ($releaseClip in $releaseAnimation.animations.PSObject.Properties.Value) {
                $releaseClips++
                foreach ($releaseBone in $releaseClip.bones.PSObject.Properties.Name) {
                    if ($releaseBone -notin $releaseBoneNames) { throw "Unknown bone: $releaseBone" }
                }
            }
        }
        $releaseRows += [ordered]@{ id=$releaseId; runtime_resources_identical_to_approved_model=$true; player_facing_docs_only=$true; archive_root_metadata=$true; files=$releaseEntries.Count; clips=$releaseClips; bytes=(Get-Item -LiteralPath $releaseZip).Length }
    } finally { $releaseArchive.Dispose() }
}
$releaseRows | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $ReleaseFolder 'release-validation.json') -Encoding utf8
$releaseRows | ConvertTo-Json -Depth 8

# Check the maid pack against the captured pre-change runtime package.
if ($CheckMaidRegression) {
$releaseOldPack = Join-Path $releaseRoot 'tmp/maid-tail-sync/original'
$releaseNewPack = Join-Path $releaseRoot 'tmp/maid-tail-sync/updated'
foreach ($releaseColor in @('white','black')) {
    foreach ($releaseRelative in @("models/entity/fox_$releaseColor.json","textures/entity/fox_$releaseColor.png")) {
        $releaseOldPath = Join-Path $releaseOldPack "assets/contract_fox/$releaseRelative"
        $releaseNewPath = Join-Path $releaseNewPack "assets/contract_fox/$releaseRelative"
        if ((Get-FileHash -LiteralPath $releaseOldPath).Hash -ne (Get-FileHash -LiteralPath $releaseNewPath).Hash) { throw "Maid appearance changed: $releaseRelative" }
    }
}
foreach ($releaseOldFile in Get-ChildItem -LiteralPath (Join-Path $releaseOldPack 'assets/contract_fox/animation') -File) {
    $releaseNewFile = Join-Path $releaseNewPack "assets/contract_fox/animation/$($releaseOldFile.Name)"
    if ((Get-FileHash -LiteralPath $releaseOldFile.FullName).Hash -ne (Get-FileHash -LiteralPath $releaseNewFile).Hash) { throw "Original maid activity file changed: $($releaseOldFile.Name)" }
}
'MAID_REGRESSION_PASS: original geometry, textures and activity animation files are byte-identical.'
} else {
    'MAID_REGRESSION_NOT_RUN: use -CheckMaidRegression when the local before/after snapshots are available.'
}
