// Art-only preview. No game resources are modified.
const path = require('node:path');
const sharp = require('sharp');
const root = path.resolve(__dirname, '../art/black_fox');
(async () => {
  const image = await sharp(path.join(root, 'parry_contact.svg')).png().toBuffer();
  await sharp({create:{width:256,height:256,channels:4,background:'#23313a'}})
    .composite([{input:image}]).png().toFile(path.join(root, 'parry_contact_preview.png'));
  await sharp(path.join(root, 'parry_contact.svg')).png().toFile(path.join(root, 'parry_contact_transparent.png'));
})().catch(error => { console.error(error); process.exitCode=1; });
