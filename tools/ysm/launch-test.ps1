$ErrorActionPreference = 'Stop'
$taskRoot = 'E:/Little maid'
$taskGame = Join-Path $taskRoot 'run-ysm-test'
$taskJava = 'C:/Program Files/Microsoft/jdk-17.0.20.101-hotspot/bin/java.exe'
if (-not (Test-Path -LiteralPath $taskJava)) { throw 'Java 17 is unavailable.' }
$taskExisting = Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | Where-Object { $_.CommandLine -like '*run-ysm-test*' }
if ($taskExisting) { Write-Output "YSM test client is already running: $($taskExisting.ProcessId)"; exit 0 }
& node (Join-Path $taskRoot 'tools/ysm/prepare-client.cjs')
if ($LASTEXITCODE -ne 0) { throw 'Client preparation failed.' }
$taskArguments = '"@' + (Join-Path $taskGame 'launcher/java-args.txt') + '"'
$taskProcess = Start-Process -FilePath $taskJava -ArgumentList $taskArguments -WorkingDirectory $taskGame -WindowStyle Hidden -RedirectStandardOutput (Join-Path $taskGame 'launcher/stdout.log') -RedirectStandardError (Join-Path $taskGame 'launcher/stderr.log') -PassThru
$taskProcess.Id | Set-Content -LiteralPath (Join-Path $taskGame 'launcher/pid.txt')
Write-Output "Launched isolated YSM client: PID $($taskProcess.Id)"
