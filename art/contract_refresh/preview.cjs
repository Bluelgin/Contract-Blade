// Preview-only pipeline: deliberately never writes into game resources.
const fs = require('node:fs/promises');
const path = require('node:path');
const sharp = require('sharp');
const directory = path.resolve(__dirname, process.argv[2] || '.');
const names = ['contract_interior_key','resonance_sword_tassel','guardian_ribbon','heartbound_knot'];
const labels = ['契约钥匙','共鸣剑穗','守护缎带','同心结'];
const size = 32, count = 32;
function animate(svg, frame) {
  const phase = frame / count * Math.PI * 2;
  // Bend only the loose ends, with a fixed attachment and a lagging tip.
  svg = svg.replace(/<g id="tail-(left|right)">([\s\S]*?)<\/g>/g, (_, side, shapes) => {
    let rows = '';
    for (let y = 15; y < 32; y++) {
      const depth = Math.max(0, (y - 21) / 10);
      const offset = Math.round(0.8 * depth * Math.sin(phase - depth * .45 - (side === 'right' ? .5 : 0)));
      const id = `${side}-${y}`;
      rows += `<defs><clipPath id="${id}"><rect width="32" height="1" y="${y}"/></clipPath></defs><g clip-path="url(#${id})"><g transform="translate(${offset} 0)">${shapes}</g></g>`;
    }
    return rows;
  });
  return svg.replace('<g id="glint"', `<g opacity="${frame >= 9 && frame <= 14 ? 1 : .35}" id="glint"`);
}
async function main() {
  const all = [];
  for (const name of names) {
    const source = await fs.readFile(path.join(directory, `${name}.svg`), 'utf8');
    const frames = [];
    for (let i=0;i<count;i++) frames.push(await sharp(Buffer.from(animate(source, i))).png().toBuffer());
    const strip = await sharp({create:{width:32,height:32*count,channels:4,background:'#00000000'}})
      .composite(frames.map((input,i)=>({input,left:0,top:i*32}))).png().toBuffer();
    const raw = await sharp(strip).resize(192,192*count,{kernel:'nearest'}).raw().toBuffer();
    await sharp(raw,{raw:{width:192,height:192*count,channels:4,pageHeight:192}})
      .gif({loop:0,delay:Array(count).fill(125),effort:7}).toFile(path.join(directory,`${name}_preview.gif`));
    all.push(frames);
  }
  const boardFrames=[];
  for(let i=0;i<count;i++) {
    const overlays=[];
    for(let j=0;j<4;j++) {
      overlays.push({input:await sharp(all[j][i]).resize(160,160,{kernel:'nearest'}).png().toBuffer(),left:j*200+20,top:35});
      overlays.push({input:all[j][i],left:j*200+84,top:235});
    }
    const labelSvg=`<svg width="800" height="300" xmlns="http://www.w3.org/2000/svg">${labels.map((s,j)=>`<text x="${j*200+100}" y="220" text-anchor="middle" font-family="Microsoft YaHei" font-size="17" fill="#e3dad4">${s}</text>`).join('')}<text x="400" y="290" text-anchor="middle" font-family="Microsoft YaHei" font-size="12" fill="#a9a2a1">上：5 倍放大　下：原始 32 像素　｜　轻微摆动 · 4 秒循环</text></svg>`;
    overlays.push({input:Buffer.from(labelSvg),left:0,top:0});
    boardFrames.push(await sharp({create:{width:800,height:300,channels:4,background:'#302c34'}}).composite(overlays).png().toBuffer());
  }
  await fs.writeFile(path.join(directory,'comparison.png'),boardFrames[0]);
  const board=await sharp({create:{width:800,height:300*count,channels:4,background:'#302c34'}})
    .composite(boardFrames.map((input,i)=>({input,left:0,top:300*i}))).raw().toBuffer();
  await sharp(board,{raw:{width:800,height:300*count,channels:4,pageHeight:300}})
    .gif({loop:0,delay:Array(count).fill(125),effort:7}).toFile(path.join(directory,'comparison.gif'));
}
main().catch(e=>{console.error(e);process.exitCode=1});
