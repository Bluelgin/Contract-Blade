param([Parameter(Mandatory = $true)][string]$SourceImage)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$coverRoot = Split-Path $PSScriptRoot
$coverDirectory = Join-Path $coverRoot 'art/maid_pack_icons'
[IO.Directory]::CreateDirectory($coverDirectory) | Out-Null
$coverOriginal = Join-Path $coverDirectory 'trio-pack-cover-v2.png'
Copy-Item -LiteralPath $SourceImage -Destination $coverOriginal
$coverInput = [Drawing.Image]::FromFile($coverOriginal)
try {
    if ($coverInput.Width -ne $coverInput.Height) { throw 'Pack cover must be square; do not hard-crop the characters.' }
    foreach ($coverSize in @(512, 256, 64, 32)) {
        $coverBitmap = [Drawing.Bitmap]::new($coverSize, $coverSize)
        $coverGraphics = [Drawing.Graphics]::FromImage($coverBitmap)
        $coverAttributes = [Drawing.Imaging.ImageAttributes]::new()
        try {
            $coverGraphics.CompositingQuality = [Drawing.Drawing2D.CompositingQuality]::HighQuality
            $coverGraphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $coverGraphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::HighQuality
            $coverGraphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::HighQuality
            $coverAttributes.SetWrapMode([Drawing.Drawing2D.WrapMode]::TileFlipXY)
            $coverRectangle = [Drawing.Rectangle]::new(0, 0, $coverSize, $coverSize)
            $coverGraphics.DrawImage($coverInput, $coverRectangle, 0, 0, $coverInput.Width, $coverInput.Height, [Drawing.GraphicsUnit]::Pixel, $coverAttributes)
            $coverBitmap.Save((Join-Path $coverDirectory "trio-pack-icon-$coverSize.png"), [Drawing.Imaging.ImageFormat]::Png)
        } finally { $coverAttributes.Dispose(); $coverGraphics.Dispose(); $coverBitmap.Dispose() }
    }
} finally { $coverInput.Dispose() }
Get-ChildItem -LiteralPath $coverDirectory -Filter '*.png' | Select-Object Name, Length
