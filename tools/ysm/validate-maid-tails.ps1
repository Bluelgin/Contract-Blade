$ErrorActionPreference = 'Stop'
$tailRoot = 'E:/Little maid'
$tailClasspath = [IO.File]::ReadAllLines((Join-Path $tailRoot 'tools/tlm-validator/bladesmith-runtime.args'))[2].Trim('"').Replace('\\','\')
$tailClasses = Join-Path $tailRoot 'tmp/maid-tail-sync/classes'
[IO.Directory]::CreateDirectory($tailClasses) | Out-Null
& javac -encoding UTF-8 -cp $tailClasspath -d $tailClasses (Join-Path $tailRoot 'tools/ysm/MaidTailValidator.java')
if ($LASTEXITCODE -ne 0) { throw 'Maid validator compilation failed' }
& java '-Djava.awt.headless=true' -cp "$tailClasses;$tailClasspath" MaidTailValidator (Join-Path $tailRoot 'tmp/maid-tail-sync/updated') (Join-Path $tailRoot 'art/fox_polish_preview/tail-adaptation/native-validation.json')
if ($LASTEXITCODE -ne 0) { throw 'Maid native validation failed' }
