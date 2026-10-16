$ErrorActionPreference = 'Stop'
$taskRoot = 'E:/Little maid'
$taskGame = Join-Path $taskRoot 'run-ysm-test'
$taskRunning = Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | Where-Object { $_.CommandLine -like '*run-ysm-test*' }
if ($taskRunning) { throw 'Close the YSM test client before replacing runtime files.' }
$taskBackup = Join-Path $taskGame ('launcher/backups/integration-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $taskBackup -Force | Out-Null
$taskMods = @(
    'build/libs/contract-blade-forge-1.20.1-1.1.1.jar',
    'libs/touhoulittlemaid-1.5.3-forge+mc1.20.1.jar',
    'tmp/compat-test/SlashBladeResharped-1.20.1-1.9.65.jar',
    'tmp/compat-test/blade_tetra-1.5.9.jar',
    'tmp/compat-test/tetra-6.3.0.jar',
    'tmp/compat-test/mutil-1.20.1-6.2.0.jar',
    'tmp/compat-test/L_Enders_Cataclysm-3.31.jar',
    'tmp/compat-test/lionfishapi-3.0.jar',
    'tmp/compat-test/curios-forge-5.14.1+1.20.1.jar',
    'tmp/compat-test/dummmmmmy-1.20-2.0.12-forge.jar',
    'tmp/compat-test/moonlight-1.20-2.16.37-forge.jar',
    'tmp/compat-test/mowziesmobs-1.8.2.jar',
    'tmp/compat-test/geckolib-forge-1.20.1-4.8.4.jar',
    'libs/configured-1.20.1-2.2.3.jar'
)
foreach ($taskRelative in $taskMods) {
    $taskSource = Join-Path $taskRoot $taskRelative
    if (-not (Test-Path -LiteralPath $taskSource)) { throw "Missing mod: $taskRelative" }
    $taskName = Split-Path $taskSource -Leaf
    $taskTarget = Join-Path $taskGame "mods/$taskName"
    if (Test-Path -LiteralPath $taskTarget) { Copy-Item -LiteralPath $taskTarget -Destination $taskBackup }
    Copy-Item -LiteralPath $taskSource -Destination $taskTarget
    if ((Get-FileHash -LiteralPath $taskSource).Hash -ne (Get-FileHash -LiteralPath $taskTarget).Hash) { throw "Copied mod mismatch: $taskName" }
}
foreach ($taskId in @('contract_akatsuki','contract_white_fox','contract_black_fox')) {
    $taskSource = Join-Path $taskRoot "dist/ysm/1.1.0/models/$taskId"
    $taskTarget = Join-Path $taskGame "config/yes_steve_model/custom/$taskId"
    if (Test-Path -LiteralPath $taskTarget) { Copy-Item -LiteralPath $taskTarget -Destination $taskBackup -Recurse }
    New-Item -ItemType Directory -Path $taskTarget -Force | Out-Null
    Get-ChildItem -LiteralPath $taskSource | Copy-Item -Destination $taskTarget -Recurse -Force
    foreach ($taskFile in Get-ChildItem -LiteralPath $taskSource -Recurse -File) {
        $taskRelative = [IO.Path]::GetRelativePath($taskSource,$taskFile.FullName)
        if ((Get-FileHash -LiteralPath $taskFile.FullName).Hash -ne (Get-FileHash -LiteralPath (Join-Path $taskTarget $taskRelative)).Hash) { throw "Model copy mismatch: $taskId/$taskRelative" }
    }
}
Write-Output "Prepared YSM integration test; previous models backed up at $taskBackup"
Get-ChildItem -LiteralPath (Join-Path $taskGame 'mods') -Filter '*.jar' | Select-Object Name
