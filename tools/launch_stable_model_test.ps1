$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
$taskGradle = 'C:/Users/Administrator/.gradle/wrapper/dists/gradle-8.7-bin/bhs2wmbdwecv87pi65oeuq5iu/gradle-8.7/bin/gradle.bat'
Set-Location -LiteralPath $taskRoot
& $taskGradle runClient '-PstableRelease=true' '-PcompatTest=slashblade' --offline -I tmp/shrine-cached-dependencies.init.gradle *> tmp/stable-release-1.1.1-client.log
exit $LASTEXITCODE
