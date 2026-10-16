$ErrorActionPreference = 'Stop'
$taskRoot = 'E:/Little maid'
$taskGame = Join-Path $taskRoot 'run-ysm-test'
$taskSmoke = Join-Path $taskRoot ('tmp/phantom-production-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$taskJava = 'C:/Program Files/Microsoft/jdk-17.0.20.101-hotspot/bin/java.exe'
$taskWorld = Join-Path $taskRoot 'run/fox-minimal-dialogue-standalone-20261011'
if (-not (Test-Path -LiteralPath (Join-Path $taskWorld 'level.dat'))) { throw 'Missing isolated fixture world.' }
New-Item -ItemType Directory -Path (Join-Path $taskSmoke 'mods') -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $taskSmoke 'saves') -Force | Out-Null
Get-ChildItem -LiteralPath (Join-Path $taskGame 'mods') -Filter '*.jar' | Copy-Item -Destination (Join-Path $taskSmoke 'mods')
Copy-Item -LiteralPath (Join-Path $taskRoot 'build/libs/contract-blade-forge-1.20.1-1.1.1.jar') -Destination (Join-Path $taskSmoke 'mods/contract-blade-forge-1.20.1-1.1.1.jar') -Force
Copy-Item -LiteralPath $taskWorld -Destination (Join-Path $taskSmoke 'saves/PhantomSmoke') -Recurse
& node (Join-Path $taskRoot 'tools/ysm/confirm-phantom-smoke.cjs') (Join-Path $taskSmoke 'saves/PhantomSmoke/level.dat')
if ($LASTEXITCODE -ne 0) { throw 'Isolated world confirmation failed.' }
$taskArgs = [Collections.Generic.List[string]]::new()
$taskArgs.Add('"-Dcontractblade.blackFoxPhantom.validation=true"')
foreach ($taskLine in [IO.File]::ReadAllLines((Join-Path $taskGame 'launcher/java-args.txt'))) { $taskArgs.Add($taskLine) }
$taskIndex = $taskArgs.IndexOf('"--gameDir"')
if ($taskIndex -lt 0) { throw 'Missing game directory launch argument.' }
$taskArgs[$taskIndex + 1] = '"' + $taskSmoke.Replace('\','\\') + '"'
$taskArgs.Add('"--quickPlaySingleplayer"'); $taskArgs.Add('"PhantomSmoke"')
$taskArgFile = Join-Path $taskSmoke 'java-args.txt'
[IO.File]::WriteAllLines($taskArgFile, $taskArgs)
$taskProcess = Start-Process -FilePath $taskJava -ArgumentList ('"@' + $taskArgFile + '"') -WorkingDirectory $taskSmoke -WindowStyle Hidden -RedirectStandardOutput (Join-Path $taskSmoke 'stdout.log') -RedirectStandardError (Join-Path $taskSmoke 'stderr.log') -PassThru
Write-Output "Production phantom smoke test PID $($taskProcess.Id) in $taskSmoke"
Write-Output 'Only the copied fixture world is opened; player saves are untouched.'
