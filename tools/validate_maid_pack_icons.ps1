$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$checkRoot = Split-Path $PSScriptRoot
$checkExpected = [IO.File]::ReadAllBytes((Join-Path $checkRoot 'art/maid_pack_icons/trio-pack-icon-256.png'))
foreach ($checkPack in @(
    @{Before = 'contract-fox-before-cover.zip'; After = 'contract-fox-1.0.1.zip'; Namespace = 'contract_fox'},
    @{Before = 'contract-akatsuki-before-cover.zip'; After = 'contract-akatsuki-1.0.0.zip'; Namespace = 'akatsuki_winefox'},
    @{Before = 'contract-akatsuki-before-cover.zip'; After = 'contract-fox-1.0.1.zip'; Namespace = 'akatsuki_winefox'}
)) {
    $checkOld = [IO.Compression.ZipFile]::OpenRead((Join-Path $checkRoot "art/maid_pack_icons/before/$($checkPack.Before)"))
    $checkNew = [IO.Compression.ZipFile]::OpenRead((Join-Path $checkRoot "src/main/resources/modelpacks/$($checkPack.After)"))
    try {
        if (-not $checkNew.GetEntry("assets/$($checkPack.Namespace)/")) { throw 'Native loader namespace entry missing.' }
        $checkPreserved = 0
        foreach ($checkEntry in $checkOld.Entries) {
            if ($checkEntry.FullName -notmatch '/(models|animation|textures/entity)/' -or $checkEntry.Name -eq '') { continue }
            $checkReplacement = $checkNew.GetEntry($checkEntry.FullName)
            if (-not $checkReplacement) { throw "Removed character asset: $($checkEntry.FullName)" }
            $checkApproved = $null
            if ($checkPack.Namespace -eq 'akatsuki_winefox' -and $checkEntry.FullName.EndsWith('/models/entity/akatsuki.json')) { $checkApproved = Join-Path $checkRoot 'art/akatsuki/crimson-wrap/akatsuki-crimson-wrap.geo.json' }
            if ($checkPack.Namespace -eq 'akatsuki_winefox' -and $checkEntry.FullName.EndsWith('/textures/entity/akatsuki.png')) { $checkApproved = Join-Path $checkRoot 'art/akatsuki/crimson-wrap/akatsuki-crimson-wrap.png' }
            $checkOldStream = if ($checkApproved) { [IO.File]::OpenRead($checkApproved) } else { $checkEntry.Open() }
            $checkNewStream = $checkReplacement.Open()
            try {
                $checkOldHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($checkOldStream))
                $checkNewHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($checkNewStream))
                if ($checkOldHash -ne $checkNewHash) { throw "Unexpected character asset change: $($checkEntry.FullName)" }
            } finally { $checkOldStream.Dispose(); $checkNewStream.Dispose() }
            $checkPreserved++
        }
        foreach ($checkIconPath in @('pack.png', "assets/$($checkPack.Namespace)/textures/maid_icon.png")) {
            $checkStream = $checkNew.GetEntry($checkIconPath).Open()
            $checkBytes = [IO.MemoryStream]::new()
            try {
                $checkStream.CopyTo($checkBytes)
                if (-not [Linq.Enumerable]::SequenceEqual([byte[]]$checkExpected, [byte[]]$checkBytes.ToArray())) { throw "Wrong cover: $checkIconPath" }
            } finally { $checkStream.Dispose(); $checkBytes.Dispose() }
        }
        foreach ($checkArchive in @($checkOld, $checkNew)) {
            $checkMetadataEntry = $checkArchive.GetEntry("assets/$($checkPack.Namespace)/maid_model.json")
            if (-not $checkMetadataEntry) { $checkMetadataEntry = $checkArchive.GetEntry('assets/contract_fox/maid_model.json') }
            $checkReader = [IO.StreamReader]::new($checkMetadataEntry.Open())
            try { $checkMetadata = $checkReader.ReadToEnd() | ConvertFrom-Json } finally { $checkReader.Dispose() }
            $checkList = @($checkMetadata.model_list | Where-Object model_id -Like "$($checkPack.Namespace):*")
            # Presentation descriptions may change, but all renderer/animation bindings stay identical.
            $checkList | ForEach-Object { $_.PSObject.Properties.Remove('description') }
            $checkList = $checkList | ConvertTo-Json -Depth 30 -Compress
            if ($checkArchive -eq $checkOld) { $checkOldList = $checkList }
            elseif ($checkList -ne $checkOldList) { throw 'Model definitions or animation bindings changed.' }
        }
        Write-Output "$($checkPack.After): cover and $checkPreserved approved character resources match; renderer and animation bindings preserved."
    } finally { $checkOld.Dispose(); $checkNew.Dispose() }
}
$checkMixin = Get-Content -LiteralPath (Join-Path $checkRoot 'src/main/resources/maid_weapon.mixins.json') -Raw | ConvertFrom-Json
if ($checkMixin.client) { throw 'Unexpected client model-preview hook.' }
Write-Output 'Native 3D thumbnails: no client rendering hooks added.'
