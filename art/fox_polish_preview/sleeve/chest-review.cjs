const fs=require('fs'),path=require('path');
const sharp=require('C:/Users/Administrator/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp');
const here=__dirname;
const allowed=e=>(e.name==='UpperBody'&&e.from[1]>24&&e.to[1]-e.from[1]>=1)||/^LayerFur/.test(e.name)||/^Shoulder(?:-?1)$/.test(e.name);
async function verify(color){
 const read=s=>JSON.parse(fs.readFileSync(path.join(here,`fox_${color}_${s}.bbmodel`),'utf8'));
 const a=read('blend'),b=read('chest'),byId=new Map(b.elements.map(e=>[e.uuid,e]));
 const pixels=async p=>sharp(Buffer.from(p.textures[0].source.split(',')[1],'base64')).ensureAlpha().raw().toBuffer();
 const pa=await pixels(a),pb=await pixels(b);let changed=0;
 for(const e of a.elements){const n=byId.get(e.uuid);if(!n)throw Error('Missing cube');
  for(const k of ['from','to','origin','rotation'])if(JSON.stringify(e[k]||[])!==JSON.stringify(n[k]||[]))throw Error('Geometry changed');
  for(const [side,f] of Object.entries(e.faces)){
   if(JSON.stringify(f.uv)!==JSON.stringify(n.faces[side].uv)){if(!allowed(e))throw Error('UV changed outside chest/shoulders');changed++;}
   if(f.texture==null)continue;const u=f.uv;
   for(let y=Math.floor(Math.min(u[1],u[3]));y<Math.ceil(Math.max(u[1],u[3]));y++)for(let x=Math.floor(Math.min(u[0],u[2]));x<Math.ceil(Math.max(u[0],u[2]));x++){
    const i=(y*1024+x)*4;for(let k=0;k<4;k++)if(pa[i+k]!==pb[i+k])throw Error('Existing referenced pixels overwritten');
   }
  }
 }
 if(a.elements.length!==b.elements.length||JSON.stringify(a.groups)!==JSON.stringify(b.groups)||JSON.stringify(a.animations)!==JSON.stringify(b.animations))throw Error('Structure or animations changed');
 return {color,changedChestShoulderFaces:changed,geometryAndAnimationsUnchanged:true,otherUVUnchanged:true,existingReferencedPixelsUnchanged:true};
}
async function sheet(){let cards=[];
 for(const [row,color] of ['white','black'].entries())for(const [col,state] of ['before','after'].entries()){
  const src=fs.readFileSync(path.join(here,`${color}-chest-${state}.png`)).toString('base64'),x=16+col*416,y=64+row*652;
  cards.push(`<rect x="${x}" y="${y}" width="400" height="636" rx="8" fill="#23232a"/><text x="${x+16}" y="${y+32}" fill="#eeeeef" font-size="20">${color.toUpperCase()} · ${state.toUpperCase()}</text><image href="data:image/png;base64,${src}" x="${x+2}" y="${y+48}" width="396" height="580" preserveAspectRatio="xMidYMid meet"/>`);
 }
 await sharp(Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="848" height="1376"><rect width="848" height="1376" fill="#121218"/><text x="24" y="40" fill="#eeeeef" font-size="24">Crossed collar &amp; shoulder texture preview</text>${cards.join('')}</svg>`)).png().toFile(path.join(here,'chest-preview.png'));
}
(async()=>{const report=[];for(const c of ['white','black'])report.push(await verify(c));await sheet();fs.writeFileSync(path.join(here,'chest-verification.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));})().catch(e=>{console.error(e);process.exitCode=1;});
