// Layout native Blockbench screenshots without repainting the rendered model.
const fs=require('fs'),path=require('path');
const sharp=require('C:/Users/Administrator/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp');
const here=__dirname;
async function sheet(name,inside) {
 const cards=[];
 for(const [row,color] of ['white','black'].entries())for(const [col,state] of ['before','after'].entries()) {
   const filename=`${color}-${inside?'inside-':''}${state}.png`;
   const data=fs.readFileSync(path.join(here,filename)).toString('base64');
   const x=20+col*590,y=80+row*455;
   cards.push(`<rect x="${x}" y="${y}" width="570" height="435" rx="10" fill="#17171e"/><text x="${x+18}" y="${y+29}" font-size="21" fill="#d6d9e3">${color.toUpperCase()} · ${state==='before'?'BEFORE':'AFTER'}</text><image href="data:image/png;base64,${data}" x="${x+5}" y="${y+42}" width="560" height="385" preserveAspectRatio="xMidYMid meet"/>`);
 }
 const svg=`<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="1000"><rect width="1200" height="1000" fill="#101016"/><text x="30" y="46" font-family="sans-serif" font-size="29" fill="#ececf2">${inside?'Hidden scalp · outer hair temporarily hidden':'Fox hair polish · native Blockbench preview'}</text>${cards.join('')}</svg>`;
 await sharp(Buffer.from(svg)).png().toFile(path.join(here,name+'.png'));
}
function ancestry(p) {
 const names=new Map(p.groups.map(g=>[g.uuid,g.name])),out=new Map();
 function walk(items,anc=[]) {for(const x of items)if(typeof x==='string')out.set(x,anc);else walk(x.children||[],anc.concat(names.get(x.uuid)));}
 walk(p.outliner);return out;
}
async function verify(color) {
 const old=JSON.parse(fs.readFileSync(`E:/刃录/art/characters/${color}_fox/fox_${color}.bbmodel`,'utf8'));
 const next=JSON.parse(fs.readFileSync(path.join(here,`fox_${color}_polished.bbmodel`),'utf8'));
 const byId=new Map(next.elements.map(e=>[e.uuid,e])),anc=ancestry(old);
 let changed=0;
 const read=async p=>sharp(Buffer.from(p.textures[0].source.split(',')[1],'base64')).ensureAlpha().raw().toBuffer();
 const originalPixels=await read(old),newPixels=await read(next);
 const mask=new Uint8Array(512*512);
 for(const e of old.elements) {
  const n=byId.get(e.uuid);if(!n)throw Error('Missing cube '+e.uuid);
  for(const field of ['from','to','origin','rotation'])if(JSON.stringify(e[field]||[])!==JSON.stringify(n[field]||[]))throw Error('Geometry changed '+field);
  const names=anc.get(e.uuid)||[];
  const permitted=(names.some(x=>/Hair|Bangs/.test(x))&&!names.some(x=>/Ornament/.test(x)))||(e.name==='Head'&&names.includes('MHead'));
  for(const [k,f] of Object.entries(e.faces)) {
   if(JSON.stringify(f.uv)!==JSON.stringify(n.faces[k].uv)) {if(!permitted)throw Error('Unrelated UV changed');changed++;}
   if(f.texture!=null) {
    const [u,v,u2,v2]=f.uv;
    for(let y=Math.floor(Math.min(v,v2));y<Math.ceil(Math.max(v,v2));y++)for(let x=Math.floor(Math.min(u,u2));x<Math.ceil(Math.max(u,u2));x++)mask[y*512+x]=1;
   }
  }
 }
 if(old.elements.length!==next.elements.length||old.groups.length!==next.groups.length)throw Error('Geometry count changed');
 for(let i=0;i<mask.length;i++)if(mask[i])for(let k=0;k<4;k++)if(originalPixels[i*4+k]!==newPixels[i*4+k])throw Error('Original used texel overwritten');
 const head=old.elements.find(e=>e.name==='Head'&&(anc.get(e.uuid)||[]).includes('MHead'));
 const target=byId.get(head.uuid);let skin=0,remainingRed=0;
 const hairColors=new Set(['171,54,78','211,105,119','121,24,53','73,12,34','54,8,26','235,149,55']);
 for(const [k,f] of Object.entries(head.faces)) {
  const a=f.uv,b=target.faces[k].uv;
  const dx=Math.round(b[0]-a[0]),dy=Math.round(b[1]-a[1]);
  for(let y=Math.min(a[1],a[3]);y<Math.max(a[1],a[3]);y++)for(let x=Math.min(a[0],a[2]);x<Math.max(a[0],a[2]);x++) {
   const i=(y*512+x)*4,j=((y+dy)*512+x+dx)*4;
   const oldRgb=[...originalPixels.subarray(i,i+3)].join(','),newRgb=[...newPixels.subarray(j,j+3)].join(',');
   if(hairColors.has(newRgb))remainingRed++;
   if(!hairColors.has(oldRgb)){if(oldRgb!==newRgb||originalPixels[i+3]!==newPixels[j+3])throw Error('Head skin changed');skin++;}
  }
 }
 if(remainingRed)throw Error('Residual scalp red');
 return {color,geometryUnchanged:true,originalUsedTexelsUnchanged:true,changedFaces:changed,headSkinTexelsPreserved:skin,residualOriginalHairColors:remainingRed,sourceAnimations:old.animations.length,previewAnimations:next.animations.length};
}
(async()=>{
 const results=[];for(const color of ['white','black'])results.push(await verify(color));
 await sheet('exterior-comparison',false);await sheet('inside-comparison',true);
 fs.writeFileSync(path.join(here,'verification.json'),JSON.stringify(results,null,2));
 console.log(JSON.stringify(results,null,2));
})().catch(e=>{console.error(e);process.exitCode=1;});
