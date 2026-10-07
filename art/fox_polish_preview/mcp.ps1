param([string]$Tool = 'risky_eval', [string]$CodePath, [string]$Arguments = '{}', [string]$ImagePath)
$ErrorActionPreference = 'Stop'
if ($CodePath) { $Arguments = @{code=(Get-Content -LiteralPath $CodePath -Raw)} | ConvertTo-Json -Compress }
$bbPayload = @{jsonrpc='2.0';id=20;method='tools/call';params=@{name=$Tool;arguments=($Arguments | ConvertFrom-Json)}} | ConvertTo-Json -Depth 100 -Compress
$bbReply = Invoke-RestMethod -Uri 'http://127.0.0.1:3000/bb-mcp' -Method Post -Headers @{Accept='application/json, text/event-stream';'mcp-session-id'='5ce6cd20-cf10-4b95-a50c-f0eb20e5376d'} -ContentType 'application/json' -Body ([System.Text.Encoding]::UTF8.GetBytes($bbPayload))
if ($bbReply.result.isError) { throw ($bbReply | ConvertTo-Json -Depth 10) }
if ($ImagePath) {
    $bbImage = $bbReply.result.content | Where-Object {$_.type -eq 'image'} | Select-Object -First 1
    if (-not $bbImage) { throw ($bbReply | ConvertTo-Json -Depth 10) }
    [IO.File]::WriteAllBytes([IO.Path]::GetFullPath($ImagePath), [Convert]::FromBase64String($bbImage.data))
    Write-Output "Screenshot saved: $ImagePath"
} else { $bbReply | ConvertTo-Json -Depth 100 -Compress }
