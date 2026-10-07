// SVG-authored concept only. Does not write runtime resources or change combat code.
const fs = require('node:fs/promises');
const path = require('node:path');
const sharp = require('sharp');
const {execFileSync} = require('node:child_process');
const out = __dirname;
const clamp = x => Math.max(0, Math.min(1,x));
const ease = x => {x=clamp(x);return x*x*(3-2*x)};
const svg = (body,w=800,h=450) => `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 800 450"><title>黑狐 · 沉界破斩 · 特效概念预览</title>${body}</svg>`;
const rift = (x,y,s,alpha,angle=0) => `<g transform="translate(${x} ${y}) rotate(${angle}) scale(${s})" opacity="${alpha}">
  <path fill="#482076" opacity=".35" d="M-28-96L-7-140 17-95 8-63 37-23 17 18 28 76 3 128-18 81-9 46-37 8-19-38Z"/>
  <path fill="#080a19" stroke="#7540b9" stroke-width="3" d="M-7-113L5-73-4-39 17-11 5 23 11 57-3 107-8 59-21 19-9-8-15-54Z"/>
  <path fill="none" stroke="#c18cf8" stroke-width="2" d="M-7-113L5-73-4-39 17-11 5 23 11 57-3 107M-4-39L-40-54-55-83M5 23L42 45 55 71"/>
  <path fill="none" stroke="#efe0ff" stroke-width="1" d="M-3-34L10-10 1 20"/>
  <g fill="#9e62e7"><path d="M-43-13l8-9 3 13-7 4ZM30-56l7-5 4 10-9 5ZM25 75l8 6-2 8-8-7Z"/></g>
</g>`;
const slash = (progress,alpha) => `<g opacity="${alpha}" transform="translate(484 245) rotate(5) scale(${.4+.6*progress})">
  <path fill="#562498" opacity=".32" d="M55-145Q-59-110-301 31L-365 100Q-138 58 18-43Z"/>
  <path fill="#9c58e0" d="M55-145Q-48-67-354 99Q-175 76 22-38Z"/>
  <path fill="#d9aaff" d="M55-145Q-48-67-354 99Q-132 20 15-46Z"/>
  <path fill="#fff0ff" d="M55-145Q-48-67-354 99Q-120-4 35-92Z"/>
  <path fill="#311340" d="M31-103Q-120-4-333 86Q-118-28 31-103Z"/>
  <path stroke="#b977f2" stroke-width="2" fill="none" d="M75-125Q-3-41-231 81M-30-108Q-139-8-373 71"/>
</g>`;
const fox = (x,y,alpha,scale=1) => `<g transform="translate(${x} ${y}) scale(${scale})" opacity="${alpha}">
  <g fill="#342447" stroke="#aa7ade" stroke-width="1.5"><path d="M-17-77L-24-111-7-99 14-99 27-111 20-76ZM-16-72L-30-26-16-20-10 17 0 23 10 17 16-20 30-26 16-72Z"/>
  <path d="M13-26Q64-33 59-75Q83-32 44-1L16 7ZM15-12Q76-2 81-37Q104 9 57 20L12 15ZM16 7Q57 27 66 11Q78 52 24 35L9 21Z"/></g>
  <path fill="#d5a9ff" d="M-11-84h6v3h-6ZM8-84h6v3H8Z"/>
  <path stroke="#cc92ff" stroke-width="2" d="M-13-44h27M19-36L54-12"/>
  <path fill="#211d30" d="M-12 19h10v24h-12ZM4 19h10v24H4Z"/>
</g>`;
function frame(t,labels=true) {
 let body=`<rect width="800" height="450" fill="#101c26"/><path d="M50 351H750" stroke="#273b48"/><ellipse cx="450" cy="350" rx="105" ry="12" fill="#162832"/>`;
 // Small target marker indicates the direction of the slash, not a proposed HUD.
 body+=`<path d="M145 319l12-18 12 18-12 18Z" fill="#547786" opacity=".6"/><path d="M139 346h36" stroke="#547786"/>`;
 let stage='沉入次元';
 if(t<.85){let p=ease(t/.75);body+=rift(453,341,1,.25+.7*Math.sin(p*Math.PI),90);body+=fox(446,279+p*95,1-p);}
 else if(t<1.65){stage='裂隙潜行';body+=rift(498,224,.13+.12*ease((t-1)/.55),.3+.5*ease((t-1)/.55),-18);}
 else if(t<2.2){stage='破界现身 · 蓄斩';let p=ease((t-1.65)/.45);body+=rift(498,226,.25+p*.8,1-p*.55,-18);body+=fox(500-p*24,264, p);body+=`<path d="M503 183L${503+p*42} ${183-p*34}" stroke="#e9ceff" stroke-width="${1+p*3}"/>`;}
 else if(t<2.62){stage='大斩 · 振刀窗口';let p=clamp((t-2.2)/.28);body+=fox(476,264,.8);body+=slash(p,Math.sin(Math.min(1,p)*Math.PI/2));body+=rift(292,252,.14,.75,-55);}
 else {stage='斩痕消散';let p=ease((t-2.62)/1.1);body+=fox(476,264,.8);body+=slash(1,(1-p)*.42);body+=`<path d="M192 312L485 154" stroke="#bc84ef" stroke-width="${1.8*(1-p)}" opacity="${1-p}"/>`;
   for(let i=0;i<13;i++){let u=i/12;let x=192+293*u;let y=312-158*u;body+=`<rect x="${x+Math.sin(i*4)*p*38}" y="${y-p*(15+i%4*9)}" width="${2+i%3}" height="${3+i%3}" fill="#c79aef" opacity="${(1-p)*.7}" transform="rotate(25 ${x} ${y})"/>`;}}
 if(labels)body+=`<text x="36" y="43" fill="#e5d8f6" font-family="Microsoft YaHei, sans-serif" font-size="21">黑狐 · 沉界破斩</text><text x="36" y="72" fill="#7f91a2" font-family="Microsoft YaHei, sans-serif" font-size="12">特效概念稿 / 轮廓仅示意黑狐的位置，不替换游戏模型</text><text x="36" y="404" fill="#c8a3ed" font-family="Microsoft YaHei, sans-serif" font-size="17">${stage}</text><rect x="36" y="420" width="728" height="2" fill="#273442"/><rect x="36" y="420" width="${728*t/4.4}" height="2" fill="#a276cb"/>`;
 return svg(body);
}
(async()=>{
 await fs.mkdir(path.join(out,'frames'),{recursive:true});
 const moments=[.32,1.4,1.99,2.4];
 for(let i=0;i<moments.length;i++){let text=frame(moments[i]);await fs.writeFile(path.join(out,`stage-${i+1}.svg`),text);await sharp(Buffer.from(text)).png().toFile(path.join(out,`stage-${i+1}.png`));}
 await fs.writeFile(path.join(out,'rift-transparent.svg'),svg(rift(400,225,1.25,1)));
 await fs.writeFile(path.join(out,'slash-transparent.svg'),svg(slash(1,1)));
 let thumbs=await Promise.all(moments.map(async(_,i)=>({input:await sharp(path.join(out,`stage-${i+1}.png`)).resize(400,225).toBuffer(),left:(i%2)*400,top:Math.floor(i/2)*225})));
 await sharp({create:{width:800,height:450,channels:4,background:'#101c26'}}).composite(thumbs).png().toFile(path.join(out,'contact-sheet.png'));
 for(let i=0;i<88;i++)await sharp(Buffer.from(frame(i/20))).png().toFile(path.join(out,'frames',`frame-${String(i).padStart(3,'0')}.png`));
 execFileSync('ffmpeg',['-hide_banner','-loglevel','error','-y','-framerate','20','-i',path.join(out,'frames/frame-%03d.png'),'-filter_complex','split[a][b];[a]palettegen=stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=3','-loop','0',path.join(out,'dimension-strike-preview.gif')]);
 console.log('SVG concept: 4 keyframes, transparent rift/slash sources, 20fps GIF. Runtime unchanged.');
})().catch(e=>{console.error(e);process.exitCode=1});
