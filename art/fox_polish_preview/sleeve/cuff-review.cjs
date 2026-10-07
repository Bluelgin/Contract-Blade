const fs=require('fs'),path=require('path');
const sharp=require('C:/Users/Administrator/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp');
const here=__dirname;
const hands=new Set(['471fd222-f3c9-6f2f-d2cd-7a2c8fa7e99d','78183cc0-5413-05a7-9644-5cabed039756']);
const shells=new Set(['0e500371-a4a8-5497-f76d-620dd05db78c','b4a74a17-7070-e203-17f6-c9adf4fc904d','4733fc27-fe09-3ded-5a92-3853d85fa950','22240246-e2d0-ea29-41f7-26cebadbd145']);
async function verify(color){
 const old=JSON.parse(fs.readFileSync(path.join(here,`fox_${color}_hands.bbmodel`),'utf8')),next=JSON.parse(fs.readFileSync(path.join(here,`fox_${color}_cuff.bbmodel`),'utf8'));
 const byId=new Map(next.elements.map(e=>[e.uuid,e]));
 const raw=async p=>sharp(Buffer.from(p.textures[0].source.split(',')[1],'base64')).ensureAlpha().raw().toBuffer(),a=await raw(old),b=await raw(next);
 let faces=0,removed=0,skin=0;
 const changedHandPixels=new Set();
 for(const e of old.elements){
  const n=byId.get(e.uuid);if(!n)throw Error('Missing cube');
  for(const k of ['from','to','origin','rotation'])if(JSON.stringify(e[k]||[])!==JSON.stringify(n[k]||[]))throw Error('Geometry changed');
  for(const [side,f] of Object.entries(e.faces))if(JSON.stringify(f.uv)!==JSON.stringify(n.faces[side].uv)){
   if(!shells.has(e.uuid)&&!/^LayerCuff/.test(e.name))throw Error('Unrelated UV changed');faces++;
  }
  if(hands.has(e.uuid))for(const f of Object.values(e.faces)){
   const u=f.uv;
   for(let y=Math.min(u[1],u[3]);y<Math.max(u[1],u[3]);y++)for(let x=Math.min(u[0],u[2]);x<Math.max(u[0],u[2]);x++){
    const i=(y*1024+x)*4,gold=a[i+3]&&a[i]-a[i+1]>10&&a[i+1]-a[i+2]>25;
    if(gold){if(b[i]-b[i+1]>10&&b[i+1]-b[i+2]>25)throw Error('Bracelet remains');removed++;changedHandPixels.add(i);}
    else{for(let k=0;k<4;k++)if(a[i+k]!==b[i+k])throw Error('Non-bracelet hand skin changed');skin++;}
   }
  }
 }
 for(let y=0;y<650;y++)for(let x=0;x<1024;x++){const i=(y*1024+x)*4;if(!changedHandPixels.has(i))for(let k=0;k<4;k++)if(a[i+k]!==b[i+k])throw Error('Unrelated existing texel changed');}
 if(old.elements.length!==next.elements.length||old.groups.length!==next.groups.length||old.animations.length!==next.animations.length)throw Error('Structure count changed');
 return {color,braceletPixelsRemoved:removed,existingSkinTexelsPreserved:skin,cuffFacesChanged:faces,geometryUnchanged:true,unrelatedUVAndTexelsUnchanged:true};
}
async function sheet(){
 const cards=[];for(const [i,color] of ['white','black'].entries()){
  const image=fs.readFileSync(path.join(here,`${color}-cuff.png`)).toString('base64'),x=20+i*700;
  cards.push(`<rect x="${x}" y="70" width="680" height="530" rx="10" fill="#191920"/><text x="${x+18}" y="103" fill="#dddde5" font-size="24">${color.toUpperCase()} · FOLDED CUFF / NO BRACELET</text><image href="data:image/png;base64,${image}" x="${x+5}" y="118" width="670" height="475" preserveAspectRatio="xMidYMid meet"/>`);
 }
 await sharp(Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="1420" height="620"><rect width="1420" height="620" fill="#101016"/><text x="30" y="44" fill="#eeeef0" font-size="28">Fox sleeve preview · folded fabric edge and subtle stitching</text>${cards.join('')}</svg>`)).png().toFile(path.join(here,'cuff-preview.png'));
}
(async()=>{const report=[];for(const c of ['white','black'])report.push(await verify(c));await sheet();fs.writeFileSync(path.join(here,'cuff-verification.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));})().catch(e=>{console.error(e);process.exitCode=1;});
