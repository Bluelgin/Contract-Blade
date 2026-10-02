// Shared frame renderer: preview by default, approved game textures with --install.
const fs = require('node:fs/promises');
const path = require('node:path');
const sharp = require('sharp');
const directory = path.join(__dirname, 'motion');
const install = process.argv.includes('--install');
const gameTextures = path.resolve(__dirname, '../../../src/main/resources/assets/maid_weapon/textures/item');
const names = ['contract_interior_key', 'resonance_sword_tassel', 'guardian_ribbon', 'heartbound_knot'];
const labels = ['契约钥匙', '共鸣剑穗', '守护缎带', '同心结'];
const captions = ['偶尔掠过金属高光', '固定穗头 · 双穗错拍', '静止为主 · 末端微风', '结心不动 · 垂带轻摆'];
const count = 80, delay = 100;
const profiles = {
  resonance_sword_tassel: {start: 1.3, duration: 4.0, root: 24, tip: 30, amplitude: 1.2, lag: .28, cycles: 1.5},
  guardian_ribbon: {start: 3.0, duration: 2.8, root: 22, tip: 29, amplitude: .95, lag: .2, cycles: 1},
  heartbound_knot: {start: 4.1, duration: 3.1, root: 24, tip: 29, amplitude: 1.1, lag: .32, cycles: 1.2},
};
function displacement(profile, time, y, side) {
  const depth = Math.max(0, Math.min(1, (y - profile.root) / (profile.tip - profile.root)));
  // A breeze pulse fades in and out. Loose tips respond after the attachment;
  // the other strand follows later instead of mirroring the first strand.
  const progress = (time - profile.start - depth * .12 - (side === 'right' ? profile.lag : 0)) / profile.duration;
  if (progress <= 0 || progress >= 1 || depth === 0) return 0;
  const envelope = Math.sin(Math.PI * progress) ** 1.3;
  const swing = Math.sin(2 * Math.PI * profile.cycles * progress);
  return Math.max(-1, Math.min(1, Math.round(profile.amplitude * depth ** .8 * envelope * swing)));
}
function animate(source, name, frame) {
  const time = frame * delay / 1000;
  const profile = profiles[name];
  let svg = source;
  if (profile) {
    svg = svg.replace(/<g id="tail-(left|right)">([\s\S]*?)<\/g>/g, (_, side, shapes) => {
      let rows = '';
      for (let y = 15; y < 32; y++) {
        const dx = displacement(profile, time, y, side);
        const id = `${side}-${y}`;
        rows += `<defs><clipPath id="${id}"><rect width="32" height="1" y="${y}"/></clipPath></defs><g clip-path="url(#${id})"><g transform="translate(${dx} 0)">${shapes}</g></g>`;
      }
      return rows;
    });
  } else if (time >= 2.2 && time <= 3.4) {
    // Sweep across existing metal-edge pixels only: no floating sparkles,
    // no whole-object brightness pulse and no animation of the heart itself.
    const progress = (time - 2.2) / 1.2;
    const y = Math.round(3 + progress * 25);
    const edgePaths = [...source.matchAll(/<path fill="(?:#e3ccff|#b89ce5|#f0c36a|#f8cf7c)" d="([^"]+)"\/>/g)]
      .map(match => `<path fill="white" d="${match[1]}"/>`).join('');
    const opacity = (.75 * Math.sin(Math.PI * progress)).toFixed(3);
    const shine = `<defs><clipPath id="metal-edge">${edgePaths}</clipPath></defs><g clip-path="url(#metal-edge)" opacity="${opacity}"><rect x="0" y="${y}" width="32" height="2" fill="#fff9e8"/></g>`;
    svg = svg.replace('</svg>', `${shine}</svg>`);
  }
  return svg;
}
async function gif(frames, width, height, destination) {
  const raw = await sharp({create: {width, height: height * count, channels: 4, background: '#00000000'}})
    .composite(frames.map((input, i) => ({input, left: 0, top: height * i}))).raw().toBuffer();
  await sharp(raw, {raw: {width, height: height * count, channels: 4, pageHeight: height}})
    .gif({loop: 0, delay: Array(count).fill(delay), effort: 7}).toFile(destination);
}
async function main() {
  await fs.mkdir(directory, {recursive: true});
  const all = [];
  for (const name of names) {
    const source = await fs.readFile(path.join(__dirname, `${name}.svg`), 'utf8');
    const frames = [];
    for (let i = 0; i < count; i++) frames.push(await sharp(Buffer.from(animate(source, name, i))).png().toBuffer());
    if (!frames[0].equals(frames[count - 1])) throw new Error(`${name}: loop does not return to rest`);
    if (!frames.some(frame => !frame.equals(frames[0]))) throw new Error(`${name}: missing motion`);
    if (install) {
      await sharp({create: {width: 32, height: 32 * count, channels: 4, background: '#00000000'}})
        .composite(frames.map((input, i) => ({input, left: 0, top: 32 * i})))
        .png().toFile(path.join(gameTextures, `${name}.png`));
    }
    const enlarged = await Promise.all(frames.map(frame => sharp(frame).resize(192, 192, {kernel: 'nearest'}).png().toBuffer()));
    await gif(enlarged, 192, 192, path.join(directory, `${name}_preview.gif`));
    all.push(frames);
  }
  const boardFrames = [];
  const typography = Buffer.from(`<svg width="800" height="340" xmlns="http://www.w3.org/2000/svg">${labels.map((label, j) => `<text x="${j*200+100}" y="214" text-anchor="middle" font-family="Microsoft YaHei" font-size="17" fill="#e3dad4">${label}</text><text x="${j*200+100}" y="237" text-anchor="middle" font-family="Microsoft YaHei" font-size="12" fill="#b8b0b4">${captions[j]}</text>`).join('')}<text x="400" y="321" text-anchor="middle" font-family="Microsoft YaHei" font-size="12" fill="#a9a2a1">上：5 倍放大　下：原始 32 像素　｜　8 秒循环 · 含静止间隔</text></svg>`);
  for (let i = 0; i < count; i++) {
    const overlays = [];
    for (let j = 0; j < 4; j++) {
      overlays.push({input: await sharp(all[j][i]).resize(160, 160, {kernel: 'nearest'}).png().toBuffer(), left: j*200+20, top: 30});
      overlays.push({input: all[j][i], left: j*200+84, top: 267});
    }
    overlays.push({input: typography, left: 0, top: 0});
    boardFrames.push(await sharp({create: {width: 800, height: 340, channels: 4, background: '#302c34'}}).composite(overlays).png().toBuffer());
  }
  await gif(boardFrames, 800, 340, path.join(directory, 'comparison.gif'));
  await fs.writeFile(path.join(directory, 'comparison.png'), boardFrames[0]);
  // Contact sheet exposes the bend progression for visual QA, not game assets.
  const samples = [0, 20, 28, 36, 44, 52, 60, 79];
  const sampleFrames = await Promise.all(samples.map(i => sharp(boardFrames[i]).resize(600, 255, {kernel:'nearest'}).png().toBuffer()));
  await sharp({create: {width: 1200, height: 1020, channels: 4, background: '#302c34'}})
    .composite(sampleFrames.map((input, j) => ({input, left: (j%2)*600, top: Math.floor(j/2)*255})))
    .png().toFile(path.join(directory, 'motion_check.png'));
  console.log(`Four SVG-derived animations validated: visible motion, stable attachments, matching rest frames; ${install ? 'approved 80-frame game strips installed' : 'game resources untouched'}.`);
}
main().catch(error => {console.error(error); process.exitCode = 1;});
