param([string]$Name = 'black')
$ErrorActionPreference = 'Stop'
$frameDirectory = Join-Path $PSScriptRoot "frames-$Name"
New-Item -ItemType Directory -Force -Path $frameDirectory | Out-Null
& "$PSScriptRoot/bb-mcp.ps1" -Tool risky_eval -Arguments '{"code":"Animation.all.find(a=>a.name===\"animation.contract_blade.swordplay_preview\").select(); Preview.selected.camera.zoom=0.145;Preview.selected.camera.updateProjectionMatrix();\"Ready to capture\""}' | Out-Null
for ($index = 0; $index -le 52; $index++) {
    $time = $index / 10
    $argsJson = @{code="Timeline.setTime($time); Animator.preview(); foxSwordPreview.update(); 'Frame $index'"} | ConvertTo-Json -Compress
    & "$PSScriptRoot/bb-mcp.ps1" -Tool risky_eval -Arguments $argsJson | Out-Null
    & "$PSScriptRoot/bb-mcp.ps1" -Tool capture_screenshot -ImagePath (Join-Path $frameDirectory ('frame-{0:d3}.png' -f $index)) | Out-Null
}
& ffmpeg -hide_banner -loglevel error -y -f lavfi -i 'color=c=0x202331:s=396x588:r=10' -framerate 10 -i "$frameDirectory/frame-%03d.png" -filter_complex '[0:v][1:v]overlay=shortest=1,split[a][b];[a]palettegen=stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=3' -loop 0 (Join-Path $PSScriptRoot "$Name-swordplay.gif")
if ($LASTEXITCODE -ne 0) { throw 'GIF rendering failed' }
Write-Output "Rendered $Name preview: 53 frames, 10 fps"
