const fs=require('fs'),path=require('path');
const sharp=require('C:/Users/Administrator/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp');
const here=__dirname;
function ancestry(p){const names=new Map(p.groups.map(g=>[g.uuid,g.name])),out=new Map();function walk(items,anc=[]){for(const x of items)if(typeof x==='string')out.set(x,anc);else walk(x.children||[],anc.concat(names.get(x.uuid)));}walk(p.outliner);return out;}
async function verify(color){
 const old=JSON.parse(fs.readFileSync(path.join(here,`../fox_${color}_polished.bbmodel`),'utf8')),next=JSON.parse(fs.readFileSync(path.join(here,`fox_${color}_design.bbmodel`),'utf8'));
 const byId=new Map(next.elements.map(e=>[e.uuid,e])),anc=ancestry(old);let changed=0;
 const read=async p=>sharp(Buffer.from(p.textures[0].source.split(',')[1],'base64')).ensureAlpha().raw().toBuffer(),a=await read(old),b=await read(next);
 for(let y=0;y<512;y++)for(let x=0;x<512;x++)for(let k=0;k<4;k++)if(a[(y*512+x)*4+k]!==b[(y*1024+x)*4+k])throw Error('Old atlas overwritten');
 for(const e of old.elements){
  const n=byId.get(e.uuid);if(!n)throw Error('Missing cube');
  for(const field of ['from','to','origin','rotation'])if(JSON.stringify(e[field]||[])!==JSON.stringify(n[field]||[]))throw Error('Geometry changed');
  for(const [side,f] of Object.entries(e.faces))if(JSON.stringify(f.uv)!==JSON.stringify(n.faces[side].uv)){
   const names=anc.get(e.uuid)||[];if(!names.includes('LeftArm')||names.includes('LeftHand')||/GoldSeam/.test(e.name))throw Error('Unrelated face changed');changed++;
  }
 }
 if(old.elements.length!==next.elements.length||old.groups.length!==next.groups.length||old.animations.length!==next.animations.length)throw Error('Structure count changed');
 return {color,changedFaces:changed,geometryUnchanged:true,oldAtlasPreserved:true,scope:'Left sleeve only; remove scattered outer dark panels and wide gold bands, retain fine cuff gold seam'};
}
async function sheet(){
 const cards=[];
 for(const [row,color] of ['white','black'].entries())for(const [col,state] of ['after','design'].entries()){
  const data=fs.readFileSync(path.join(here,`${color}-${state}.png`)).toString('base64'),x=20+col*700,y=70+row*540;
  cards.push(`<rect x="${x}" y="${y}" width="680" height="520" rx="10" fill="#191920"/><text x="${x+18}" y="${y+30}" font-size="23" fill="#dddde5">${color.toUpperCase()} · ${state==='after'?'PREVIOUS':'REDESIGN'}</text><image href="data:image/png;base64,${data}" x="${x+5}" y="${y+42}" width="670" height="465" preserveAspectRatio="xMidYMid meet"/>`);
 }
 const svg=`<svg xmlns="http://www.w3.org/2000/svg" width="1420" height="1170"><rect width="1420" height="1170" fill="#101016"/><text x="30" y="44" font-size="28" fill="#eeeeef">Sleeve design · continuous fabric / turned cuff / inner lining</text>${cards.join('')}</svg>`;
 await sharp(Buffer.from(svg)).png().toFile(path.join(here,'design-comparison.png'));
}
(async()=>{const report=[];for(const c of ['white','black'])report.push(await verify(c));await sheet();fs.writeFileSync(path.join(here,'design-verification.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));})().catch(e=>{console.error(e);process.exitCode=1;});
