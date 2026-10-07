const fs=require('fs'),path=require('path');
const sharp=require('C:/Users/Administrator/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp');
const here=__dirname,hands=new Set(['471fd222-f3c9-6f2f-d2cd-7a2c8fa7e99d','78183cc0-5413-05a7-9644-5cabed039756']);
function ancestry(p){const names=new Map(p.groups.map(g=>[g.uuid,g.name])),out=new Map();function walk(items,anc=[]){for(const x of items)if(typeof x==='string')out.set(x,anc);else walk(x.children||[],anc.concat(names.get(x.uuid)));}walk(p.outliner);return out;}
async function verify(color){
 const old=JSON.parse(fs.readFileSync(path.join(here,`fox_${color}_cuff.bbmodel`),'utf8')),next=JSON.parse(fs.readFileSync(path.join(here,`fox_${color}_blend.bbmodel`),'utf8'));
 const byId=new Map(next.elements.map(e=>[e.uuid,e])),anc=ancestry(old),raw=async p=>sharp(Buffer.from(p.textures[0].source.split(',')[1],'base64')).ensureAlpha().raw().toBuffer();
 const a=await raw(old),b=await raw(next);let changed=0;
 for(const e of old.elements){const n=byId.get(e.uuid);if(!n)throw Error('Missing cube');
  for(const k of ['from','to','origin','rotation'])if(JSON.stringify(e[k]||[])!==JSON.stringify(n[k]||[]))throw Error('Geometry changed');
  for(const [side,f] of Object.entries(e.faces)){
   if(JSON.stringify(f.uv)!==JSON.stringify(n.faces[side].uv)){
    const names=anc.get(e.uuid)||[];
    if(!names.some(n=>n==='LeftArm'||n==='RightArm')||names.some(n=>n==='LeftHand'||n==='RightHand')||hands.has(e.uuid))throw Error('UV change outside sleeves');changed++;
   }
   if(f.texture==null)continue;const u=f.uv;
   for(let y=Math.floor(Math.min(u[1],u[3]));y<Math.ceil(Math.max(u[1],u[3]));y++)for(let x=Math.floor(Math.min(u[0],u[2]));x<Math.ceil(Math.max(u[0],u[2]));x++){
    const i=(y*1024+x)*4;for(let k=0;k<4;k++)if(a[i+k]!==b[i+k])throw Error('Existing referenced pixel overwritten');
   }
  }
 }
 if(old.elements.length!==next.elements.length||old.groups.length!==next.groups.length||old.animations.length!==next.animations.length)throw Error('Structure changed');
 return {color,changedSleeveFaces:changed,geometryUnchanged:true,handsAndOtherUVUnchanged:true,existingReferencedPixelsUnchanged:true};
}
async function sheet(){
 const cards=[];for(const [i,color] of ['white','black'].entries()){
  const image=fs.readFileSync(path.join(here,`${color}-blend.png`)).toString('base64'),x=20+i*700;
  cards.push(`<rect x="${x}" y="70" width="680" height="530" rx="10" fill="#191920"/><text x="${x+18}" y="103" fill="#dddde5" font-size="24">${color==='white'?'WHITE · IVORY / TEAL / GOLD':'BLACK · GREY VIOLET / DEEP VIOLET'}</text><image href="data:image/png;base64,${image}" x="${x+5}" y="118" width="670" height="475" preserveAspectRatio="xMidYMid meet"/>`);
 }
 await sharp(Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="1420" height="620"><rect width="1420" height="620" fill="#101016"/><text x="30" y="44" fill="#eeeef0" font-size="28">Fox sleeve preview · soft colour transition and darker cuffs</text>${cards.join('')}</svg>`)).png().toFile(path.join(here,'blend-preview.png'));
}
(async()=>{const report=[];for(const c of ['white','black'])report.push(await verify(c));await sheet();fs.writeFileSync(path.join(here,'blend-verification.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));})().catch(e=>{console.error(e);process.exitCode=1;});
