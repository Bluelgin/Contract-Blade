param([string]$SessionId)
$ErrorActionPreference='Stop'
$helper=Join-Path $PSScriptRoot '../bb-mcp.ps1'
$frames=Join-Path $PSScriptRoot 'frames'
New-Item -ItemType Directory -Force -Path $frames | Out-Null
for($i=0;$i -le 84;$i++) {
    $time=$i/30
    & $helper -SessionId $SessionId -Tool risky_eval -Arguments (@{code="Timeline.setTime($time);Animator.preview();iaidoEquipment.update();'Frame $i'"}|ConvertTo-Json -Compress) | Out-Null
    & $helper -SessionId $SessionId -Tool capture_screenshot -ImagePath (Join-Path $frames ('frame-{0:d3}.png' -f $i)) | Out-Null
}
& ffmpeg -hide_banner -loglevel error -y -framerate 30 -i "$frames/frame-%03d.png" -filter_complex 'crop=850:640:270:0,scale=680:512,split[a][b];[a]palettegen=stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=3' -loop 0 (Join-Path $PSScriptRoot 'black-fox-iaido.gif')
if($LASTEXITCODE -ne 0){throw 'Preview encoding failed'}
& $helper -SessionId $SessionId -Tool risky_eval -Arguments '{"code":"Timeline.setTime(.8);Animator.preview();iaidoEquipment.update();Timeline.start();\"Iaido preview playing\""}'
