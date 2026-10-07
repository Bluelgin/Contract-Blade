# Deterministic crop of the user's screenshot; never repaint the character.
param(
    [string]$Source = 'C:/Users/Administrator/Documents/Tencent Files/3308361662/nt_qq/nt_data/Pic/2026-10/Ori/da5b291d29c05cf4d5b76996012d4cce.png'
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$taskRoot = Split-Path $PSScriptRoot -Parent
$target = Join-Path $taskRoot 'src/main/resources/white-fox-mod-list.png'
$sourceImage = [System.Drawing.Bitmap]::new($Source)
try {
    # Keep ears, feet and the full sword; trim the empty sky and distant terrain.
    $crop = [System.Drawing.Rectangle]::new(215, 165, 485, 340)
    if ($crop.Right -gt $sourceImage.Width -or $crop.Bottom -gt $sourceImage.Height) {
        throw 'Screenshot dimensions no longer match the reviewed crop.'
    }
    $cropped = $sourceImage.Clone($crop, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try { $cropped.Save($target, [System.Drawing.Imaging.ImageFormat]::Png) }
    finally { $cropped.Dispose() }
} finally { $sourceImage.Dispose() }
Write-Output "MOD_LIST_IMAGE_PASS: 485x340 original-pixel crop saved to $target"
