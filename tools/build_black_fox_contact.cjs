const fs = require('node:fs/promises');
const path = require('node:path');
const sharp = require('sharp');
const root = path.resolve(__dirname, '..');
(async () => {
  const target = path.join(root, 'src/main/resources/assets/maid_weapon/textures/particle/parry_contact.png');
  await fs.mkdir(path.dirname(target), {recursive:true});
  await sharp(path.join(root, 'art/black_fox/parry_contact.svg'))
    .resize(64,64,{kernel:'nearest'}).png().toFile(target);
})().catch(error => { console.error(error); process.exitCode=1; });
