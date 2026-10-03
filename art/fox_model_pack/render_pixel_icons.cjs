// Rasterize authored vector pixel art without smoothing or generated imagery.
const fs = require('node:fs/promises');
const path = require('node:path');
const sharp = require('sharp');
async function main() {
  const folder = __dirname;
  await sharp(path.join(folder, 'fox_pixel_icon.svg')).resize(64,64,{kernel:'nearest'}).png().toFile(path.join(folder,'fox_pixel_icon.png'));
  await sharp(path.join(folder, 'fox_pixel_icon.svg')).resize(256,256,{kernel:'nearest'}).png().toFile(path.join(folder,'fox_pixel_icon_preview.png'));
}
main().catch(e=>{console.error(e);process.exitCode=1;});
