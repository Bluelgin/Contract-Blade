param([switch]$Half)
$ErrorActionPreference='Stop'
$tool=Join-Path $PSScriptRoot '../bb-mcp.ps1'
$name=if($Half){'half'}else{'full'}
$animation=if($Half){'combo_b_half_preview'}else{'combo_b_preview'}
$frames=Join-Path $PSScriptRoot "frames-$name"
New-Item -ItemType Directory -Force -Path $frames | Out-Null
& $tool -Tool risky_eval -Arguments (@{code="Animation.all.find(a=>a.name==='animation.contract_blade.$animation').select(); Timeline.pause(); 'Selected'"}|ConvertTo-Json -Compress) | Out-Null
$count=if($Half){40}else{74}
for($i=0;$i -lt $count;$i++) {
  $time=[Math]::Min($i/20, $(if($Half){1.6}else{3.3}))
  & $tool -Tool risky_eval -Arguments (@{code="Timeline.setTime($time);Animator.preview();'Frame $i'"}|ConvertTo-Json -Compress) | Out-Null
  & $tool -Tool capture_screenshot -ImagePath (Join-Path $frames ('frame-{0:d3}.png' -f $i)) | Out-Null
}
& ffmpeg -hide_banner -loglevel error -y -f lavfi -i 'color=c=0x202331:s=396x588:r=20' -framerate 20 -i "$frames/frame-%03d.png" -filter_complex '[0:v][1:v]overlay=shortest=1,split[a][b];[a]palettegen=stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=3' -loop 0 (Join-Path $PSScriptRoot "combo-b-$name.gif")
if($LASTEXITCODE -ne 0){throw 'GIF rendering failed'}
Write-Output "Rendered $name Combo B at 20 fps"
