const fs = require('node:fs/promises');
const path = require('node:path');
const sharp = require('sharp');
const root = path.resolve(__dirname, '..');
(async () => {
  const out = path.join(root, 'src/main/resources/assets/maid_weapon/textures/effect');
  await fs.mkdir(out, {recursive:true});
  for (const layer of ['core','band','halo','trail']) {
    await sharp(path.join(root, `art/black_fox/arc_slash/${layer}.svg`))
      .png().toFile(path.join(out, `black_fox_arc_${layer}.png`));
  }
})().catch(error => {console.error(error);process.exitCode=1;});
