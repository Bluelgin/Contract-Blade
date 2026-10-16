$ErrorActionPreference = 'Stop'
$taskRoot = 'E:/Little maid'
$taskLines = [IO.File]::ReadAllLines((Join-Path $taskRoot 'tools/tlm-validator/bladesmith-runtime.args'))
$taskClasspath = $taskLines[2].Trim('"').Replace('\\','\')
$taskClasses = Join-Path $taskRoot 'tmp/ysm-test/tail-validator-classes'
[IO.Directory]::CreateDirectory($taskClasses) | Out-Null
& javac -encoding UTF-8 -cp $taskClasspath -d $taskClasses (Join-Path $taskRoot 'tools/ysm/TailAnimationValidator.java')
if ($LASTEXITCODE -ne 0) { throw 'Tail validator compilation failed' }
& java '-Djava.awt.headless=true' -cp "$taskClasses;$taskClasspath" TailAnimationValidator (Join-Path $taskRoot 'art/ysm-test')
if ($LASTEXITCODE -ne 0) { throw 'Tail expression validation failed' }
