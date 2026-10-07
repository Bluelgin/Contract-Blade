const fs = require('node:fs/promises');
const path = require('node:path');
const sharp = require('sharp');
const root = path.resolve(__dirname, '..');
// Approved concept redrawn on a fixed pixel grid, not downscaled from the concept bitmap.
let svg = '<svg xmlns="http://www.w3.org/2000/svg" width="214" height="96" shape-rendering="crispEdges">';
for (let row = 0; row < 3; row++) {
  const tint = ['#79558f', '#9f61bd', '#aaa0bc'][row];
  svg += `<g transform="translate(0 ${row*32})">
  <path fill="#100e18" d="M0 0h4v3h3v3h4v4h5v-1h182v-2h7v3h5v2h4v5h-8v9h-5v-9h-3v1H16v1H9v-3H6v-3H3V9H0Z"/>
  <path fill="${tint}" d="M1 1h2v4h4v3h4v3h4v4H9v-2H6v-3H3V7H1Z"/>
  <path fill="#43354e" d="M4 7h3v3h4v2h3v4H9v-2H6v-3H4Z"/>
  <path fill="#ad80c6" d="M1 1h2v4h3v2H3V5H1Z"/>
  <path fill="#282234" d="M9 12h5v5H9Z"/>
  <path fill="#4d3d5c" d="M16 9h182v9H16Z"/>
  <path fill="#9583a5" d="M17 10h180v1H17Z"/>
  <path fill="#100e18" d="M18 11h178v5H18Z"/>
  <path fill="#292135" d="M19 12h176v3H19Z"/>
  <path fill="${tint}" d="M199 8h3v3h-3Zm4 3h4v3h-4Zm-4 4h3v3h-3Zm-3-4h3v3h-3Zm7 6h2v8h-2Zm4-4h6v2h-6Z"/>
  <path fill="#bd91d3" d="M200 9h1v2h-1Zm4 3h2v1h-2Zm-7 0h2v1h-2Zm7 6h1v5h-1Z"/>
  </g>`;
}
svg += '</svg>';
(async () => {
  const source = path.join(root, 'art/black_fox/boss_presentation/black-fox-bar.svg');
  const target = path.join(root, 'src/main/resources/assets/maid_weapon/textures/gui/black_fox_bar.png');
  await fs.mkdir(path.dirname(target), {recursive: true});
  await fs.writeFile(source, svg);
  await sharp(Buffer.from(svg)).png().toFile(target);
  console.log('BLACK_FOX_BAR_ART_PASS: three 214x32 pixel-grid frames, transparent background');
})().catch(error => { console.error(error); process.exitCode = 1; });
