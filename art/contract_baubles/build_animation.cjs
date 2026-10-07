// Original editable SVGs -> Minecraft strips and enlarged animated previews.
const fs = require('node:fs/promises');
const path = require('node:path');
const sharp = require('sharp');
const output = path.resolve(__dirname, '../../src/main/resources/assets/maid_weapon/textures/item');
const phases = [0, 0, 1, 1, 0, 0, -1, -1];
const glow = ['#ba8dd2', '#ce9ee2', '#e1b8f3', '#f3dafa', '#fff0ff', '#f3dafa', '#e1b8f3', '#ce9ee2'];
// Bend SVG scanlines below the bindings. Roots stay fixed; tips lag and move more.
function bendSilk(svg, phase) {
  return svg.replace(/<g id="silk-(left|right)">([\s\S]*?)<\/g>/g, (_, side, shapes) => {
    const rows = [];
    for (let y = 21; y < 32; y++) {
      const depth = Math.max(0, (y - 22) / 9);
      const lag = side === 'right' ? 0.45 : 0;
      const offset = Math.round(1.7 * depth ** 1.35 * Math.sin(phase - lag - depth * 0.35));
      const id = `${side}-${y}`;
      rows.push(`<defs><clipPath id="${id}"><rect x="0" y="${y}" width="32" height="1"/></clipPath></defs>`
        + `<g clip-path="url(#${id})"><g transform="translate(${offset} 0)">${shapes}</g></g>`);
    }
    return rows.join('');
  });
}
async function build(name, previewOnly = false) {
  const source = await fs.readFile(path.join(__dirname, `${name}.svg`), 'utf8');
  const frames = [];
  const flexibleSilk = source.includes('id="silk-left"');
  const count = flexibleSilk ? 32 : phases.length;
  const delay = flexibleSilk ? 100 : 150;
  for (let i = 0; i < count; i++) {
    let svg = source.replace('<g id="silk">', `<g id="silk" transform="translate(${phases[i % phases.length]} 0)">`)
      .replace(/id="gem" fill="[^"]+"/, `id="gem" fill="${glow[Math.floor(i * 8 / count)]}"`);
    if (flexibleSilk) svg = bendSilk(svg, 2 * Math.PI * i / count);
    if (source.includes('id="shimmer"')) {
      const colors = ['#79acb7', '#8bbec3', '#9dd4d3', '#b5e8df', '#c8f2e7', '#b5e8df', '#9dd4d3', '#8bbec3'];
      svg = svg.replace(/id="shimmer" fill="[^"]+"/, `id="shimmer" fill="${colors[Math.floor(i * 8 / count)]}"`);
    }
    frames.push(await sharp(Buffer.from(svg)).png().toBuffer());
  }
  const strip = await sharp({create: {width: 32, height: 32 * count, channels: 4, background: '#00000000'}})
    .composite(frames.map((input, i) => ({input, left: 0, top: 32 * i}))).png().toBuffer();
  if (!previewOnly) await fs.writeFile(path.join(output, `${name}.png`), strip);
  const pixels = await sharp(strip).resize(256, 256 * count, {kernel: 'nearest'}).raw().toBuffer();
  await sharp(pixels, {raw: {width: 256, height: 256 * count, channels: 4, pageHeight: 256}})
    .gif({loop: 0, delay: Array(count).fill(delay), effort: 7})
    .toFile(path.join(__dirname, `${name}_preview.gif`));
}
const previewName = process.argv[2] === '--preview' ? process.argv[3] : null;
const jobs = previewName ? [build(previewName, true)]
  : ['resonance_sword_tassel', 'guardian_ribbon', 'heartbound_knot'].map(name => build(name));
Promise.all(jobs).catch(error => {
  console.error(error);
  process.exitCode = 1;
});
