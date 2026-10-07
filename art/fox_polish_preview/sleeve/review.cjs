const fs=require('fs'),path=require('path');
const sharp=require('C:/Users/Administrator/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp');
const here=__dirname;
function ancestry(p){const names=new Map(p.groups.map(g=>[g.uuid,g.name])),out=new Map();function walk(items,anc=[]){for(const x of items)if(typeof x==='string')out.set(x,anc);else walk(x.children||[],anc.concat(names.get(x.uuid)));}walk(p.outliner);return out;}
async function verify(color){
 const old=JSON.parse(fs.readFileSync(path.join(here,`../fox_${color}_polished.bbmodel`),'utf8'));
 const next=JSON.parse(fs.readFileSync(path.join(here,`fox_${color}_sleeve.bbmodel`),'utf8'));
 const byId=new Map(next.elements.map(e=>[e.uuid,e])),anc=ancestry(old);
 const pixels=async p=>sharp(Buffer.from(p.textures[0].source.split(',')[1],'base64')).ensureAlpha().raw().toBuffer();
 const a=await pixels(old),b=await pixels(next);let changed=0,gold=0;
 for(let y=0;y<512;y++)for(let x=0;x<512;x++)for(let k=0;k<4;k++)if(a[(y*512+x)*4+k]!==b[(y*1024+x)*4+k])throw Error('Existing atlas modified');
 for(const e of old.elements){
   const n=byId.get(e.uuid);if(!n)throw Error('Missing cube');
   for(const field of ['from','to','origin','rotation'])if(JSON.stringify(e[field]||[])!==JSON.stringify(n[field]||[]))throw Error('Geometry changed');
   const permitted=(anc.get(e.uuid)||[]).includes('LeftArm')&&!(anc.get(e.uuid)||[]).includes('LeftHand')&&!/GoldSeam/.test(e.name);
   for(const [side,f] of Object.entries(e.faces)){
     const dst=n.faces[side];if(JSON.stringify(f.uv)===JSON.stringify(dst.uv))continue;
     if(!permitted)throw Error('Unrelated face changed');changed++;
     const [x,y,x2,y2]=dst.uv;
     for(let py=y;py<y2;py++)for(let px=x;px<x2;px++){
       const u=(px-x+.5)/(x2-x),v=(py-y+.5)/(y2-y);
       const sx=Math.floor(f.uv[0]+(f.uv[2]-f.uv[0])*u),sy=Math.floor(f.uv[1]+(f.uv[3]-f.uv[1])*v),i=(sy*512+sx)*4,j=(py*1024+px)*4;
       if(a[i+3]&&a[i]-a[i+1]>10&&a[i+1]-a[i+2]>25){for(let k=0;k<4;k++)if(a[i+k]!==b[j+k])throw Error('Gold color changed');gold++;}
     }
   }
 }
 if(old.elements.length!==next.elements.length||old.groups.length!==next.groups.length||old.animations.length!==next.animations.length)throw Error('Structure count changed');
 return {color,changedFaces:changed,geometryUnchanged:true,existingAtlasUnchanged:true,unrelatedUVUnchanged:true,goldSamplesPreserved:gold,atlas:[1024,1024],scope:'Left sleeve sample only'};
}
async function sheet(){
 const cards=[];
 for(const [row,color] of ['white','black'].entries())for(const [col,state] of ['before','after'].entries()){
  const data=fs.readFileSync(path.join(here,`${color}-${state}.png`)).toString('base64'),x=20+col*700,y=70+row*540;
  cards.push(`<rect x="${x}" y="${y}" width="680" height="520" rx="10" fill="#191920"/><text x="${x+18}" y="${y+30}" font-size="23" fill="#dddde5">${color.toUpperCase()} · ${state.toUpperCase()}</text><image href="data:image/png;base64,${data}" x="${x+5}" y="${y+42}" width="670" height="465" preserveAspectRatio="xMidYMid meet"/>`);
 }
 const svg=`<svg xmlns="http://www.w3.org/2000/svg" width="1420" height="1170"><rect width="1420" height="1170" fill="#101016"/><text x="30" y="44" font-size="28" fill="#eeeeef">Sleeve colour wash · sample sleeve is on the right of each image</text>${cards.join('')}</svg>`;
 await sharp(Buffer.from(svg)).png().toFile(path.join(here,'comparison.png'));
}
(async()=>{const report=[];for(const c of ['white','black'])report.push(await verify(c));await sheet();fs.writeFileSync(path.join(here,'verification.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));})().catch(e=>{console.error(e);process.exitCode=1;});
