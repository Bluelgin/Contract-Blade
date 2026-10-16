$ErrorActionPreference = 'Stop'
$taskJar = 'E:/Little maid/tmp/ysm-test/ysm-2.6.5-forge+mc1.20.1-release.jar'
$taskDestination = 'E:/Little maid/tmp/ysm-test/default'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$taskZip = [System.IO.Compression.ZipFile]::OpenRead($taskJar)
try {
    foreach ($taskEntry in $taskZip.Entries) {
        $taskPrefix = 'assets/yes_steve_model/builtin/default/'
        if (-not $taskEntry.FullName.StartsWith($taskPrefix) -or $taskEntry.FullName.EndsWith('/')) { continue }
        $taskRelative = $taskEntry.FullName.Substring($taskPrefix.Length)
        $taskTarget = [System.IO.Path]::GetFullPath((Join-Path $taskDestination $taskRelative))
        if (-not $taskTarget.StartsWith([System.IO.Path]::GetFullPath($taskDestination) + [System.IO.Path]::DirectorySeparatorChar)) { throw 'Unsafe archive path' }
        [System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($taskTarget)) | Out-Null
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($taskEntry, $taskTarget, $true)
    }
} finally { $taskZip.Dispose() }
Write-Output 'Extracted official readable default model templates.'
