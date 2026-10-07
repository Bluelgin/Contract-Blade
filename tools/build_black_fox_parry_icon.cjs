const fs = require('node:fs/promises');
const path = require('node:path');
const sharp = require('sharp');
const root = path.resolve(__dirname, '..');
(async () => {
  const svg = path.join(root, 'art/black_fox/parry_success.svg');
  const output = path.join(root, 'src/main/resources/assets/maid_weapon/textures/gui/parry_success.png');
  await fs.mkdir(path.dirname(output), {recursive: true});
  await sharp(svg).png().toFile(output);
  await sharp(svg).resize(256, 256, {kernel:'nearest'}).png().toFile(path.join(root, 'art/black_fox/parry_success_preview.png'));
})().catch(error => { console.error(error); process.exitCode = 1; });
