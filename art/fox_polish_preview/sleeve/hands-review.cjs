const fs=require('fs'),path=require('path');
const sharp=require('C:/Users/Administrator/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp');
const here=__dirname,ids=new Set(['471fd222-f3c9-6f2f-d2cd-7a2c8fa7e99d','78183cc0-5413-05a7-9644-5cabed039756']);
const original=JSON.parse(fs.readFileSync('E:/Blade tetra/art/akatsuki-winefox/source/akatsuki-1.2.3.bbmodel','utf8'));
const raw=async p=>sharp(Buffer.from(p.textures[0].source.split(',')[1],'base64')).ensureAlpha().raw().toBuffer();
async function verify(color){
 const old=JSON.parse(fs.readFileSync(path.join(here,`fox_${color}_design.bbmodel`),'utf8')),next=JSON.parse(fs.readFileSync(path.join(here,`fox_${color}_hands.bbmodel`),'utf8'));
 const byId=new Map(next.elements.map(e=>[e.uuid,e])),a=await raw(original),b=await raw(next);let faces=0,matched=0;
 for(const e of old.elements){
  const n=byId.get(e.uuid);if(!n)throw Error('Missing cube');
  for(const k of ['from','to','origin','rotation'])if(JSON.stringify(e[k]||[])!==JSON.stringify(n[k]||[]))throw Error('Geometry modified');
  for(const [side,f] of Object.entries(e.faces))if(!ids.has(e.uuid)&&JSON.stringify(f.uv)!==JSON.stringify(n.faces[side].uv))throw Error('Unrelated UV modified');
 }
 for(const id of ids){const e=original.elements.find(e=>e.uuid===id),n=byId.get(id);
  for(const [side,f] of Object.entries(e.faces)){
   const u=f.uv,v=n.faces[side].uv,dx=v[0]-u[0],dy=v[1]-u[1];
   if(u[2]-u[0]!==v[2]-v[0]||u[3]-u[1]!==v[3]-v[1])throw Error('Hand UV orientation changed');
   for(let y=Math.min(u[1],u[3]);y<Math.max(u[1],u[3]);y++)for(let x=Math.min(u[0],u[2]);x<Math.max(u[0],u[2]);x++){
    const i=(y*256+x)*4,j=((y+dy)*1024+x+dx)*4;
    for(let k=0;k<4;k++)if(a[i+k]!==b[j+k])throw Error('Original hand texel not restored');matched++;
   }faces++;
  }
 }
 if(old.elements.length!==next.elements.length||old.groups.length!==next.groups.length||old.animations.length!==next.animations.length)throw Error('Structure changed');
 return {color,visibleHandsRestored:2,restoredFaces:faces,originalTexelsMatched:matched,otherUVUnchanged:true,geometryUnchanged:true};
}
async function sheet(){
 const cards=[];for(const [i,color] of ['white','black'].entries()){
  const image=fs.readFileSync(path.join(here,`${color}-hands.png`)).toString('base64'),x=20+i*700;
  cards.push(`<rect x="${x}" y="70" width="680" height="530" rx="10" fill="#191920"/><text x="${x+18}" y="103" fill="#dddde5" font-size="24">${color.toUpperCase()} · HANDS RESTORED</text><image href="data:image/png;base64,${image}" x="${x+5}" y="118" width="670" height="475" preserveAspectRatio="xMidYMid meet"/>`);
 }
 await sharp(Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="1420" height="620"><rect width="1420" height="620" fill="#101016"/><text x="30" y="44" fill="#eeeef0" font-size="28">Sleeve design · original Taisho Wine Fox hands restored</text>${cards.join('')}</svg>`)).png().toFile(path.join(here,'hands-preview.png'));
}
(async()=>{const report=[];for(const c of ['white','black'])report.push(await verify(c));await sheet();fs.writeFileSync(path.join(here,'hands-verification.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));})().catch(e=>{console.error(e);process.exitCode=1;});
