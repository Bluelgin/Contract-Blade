$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$taskBase = 'E:/Little maid/art/ysm-test'
$taskResults = @()
foreach ($taskId in @('contract_akatsuki','contract_white_fox','contract_black_fox')) {
    $taskArchive = [IO.Compression.ZipFile]::OpenRead((Join-Path $taskBase ($taskId + '-ysm-test.zip')))
    try {
        foreach ($taskRelative in @('models/main.json','models/arm.json','textures/default.png')) {
            $taskEntry = $taskArchive.Entries | Where-Object { $_.FullName.Replace('\','/') -eq "$taskId/$taskRelative" } | Select-Object -First 1
            if (-not $taskEntry) { throw "Missing baseline resource: $taskId/$taskRelative" }
            $taskStream = $taskEntry.Open()
            try { $taskOldHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($taskStream)) } finally { $taskStream.Dispose() }
            $taskNewHash = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $taskBase "models/$taskId/$taskRelative")).Hash
            if ($taskOldHash -ne $taskNewHash) { throw "Protected model resource changed: $taskId/$taskRelative" }
        }
        $taskEntry = $taskArchive.Entries | Where-Object { $_.FullName.Replace('\','/') -eq "$taskId/animations/main.animation.json" } | Select-Object -First 1
        $taskReader = [IO.StreamReader]::new($taskEntry.Open())
        try { $taskOld = $taskReader.ReadToEnd() | ConvertFrom-Json -AsHashtable } finally { $taskReader.Dispose() }
        $taskNew = Get-Content -LiteralPath (Join-Path $taskBase "models/$taskId/animations/main.animation.json") -Raw | ConvertFrom-Json -AsHashtable
        $taskOldFlight = $taskOld.animations.fly | ConvertTo-Json -Depth 100 -Compress
        $taskNewFlight = $taskNew.animations.fly | ConvertTo-Json -Depth 100 -Compress
        if ($taskOldFlight -ne $taskNewFlight) { throw "Flight regression: $taskId" }
        if ($taskId -ne 'contract_akatsuki') {
            $taskOld.animations.Remove('pre_parallel3') | Out-Null
            $taskNew.animations.Remove('pre_parallel3') | Out-Null
        }
        if (($taskOld | ConvertTo-Json -Depth 100 -Compress) -ne ($taskNew | ConvertTo-Json -Depth 100 -Compress)) { throw "Unrelated animation changed: $taskId" }
        $taskResults += [ordered]@{ id = $taskId; geometry_arms_texture_unchanged = $true; full_flight_clip_unchanged = $true; unrelated_main_animations_unchanged = $true }
    } finally { $taskArchive.Dispose() }
}
$taskResults | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $taskBase 'tail-locomotion-regression.json') -Encoding utf8
$taskResults | ConvertTo-Json -Depth 10
