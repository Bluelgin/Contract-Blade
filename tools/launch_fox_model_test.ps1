$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
$taskGradle = 'C:/Users/Administrator/.gradle/wrapper/dists/gradle-8.7-bin/bhs2wmbdwecv87pi65oeuq5iu/gradle-8.7/bin/gradle.bat'
Set-Location -LiteralPath $taskRoot
& $taskGradle runClient -PcompatTest=slashblade -PconfigScreens=true --offline -I tmp/shrine-cached-dependencies.init.gradle *> tmp/fox-polish-play-client.log
exit $LASTEXITCODE
