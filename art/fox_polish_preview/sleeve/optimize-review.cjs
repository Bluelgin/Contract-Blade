const fs=require('fs'),path=require('path'),sharp=require('C:/Users/Administrator/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp');
const here=__dirname;
async function verify(color){
 const read=s=>JSON.parse(fs.readFileSync(path.join(here,`fox_${color}_${s}.bbmodel`),'utf8')),a=read('contact'),b=read('optimized'),byId=new Map(b.elements.map(e=>[e.uuid,e]));
 const raw=async p=>sharp(Buffer.from(p.textures[0].source.split(',')[1],'base64')).ensureAlpha().raw().toBuffer(),pa=await raw(a),pb=await raw(b),wa=a.textures[0].width,wb=b.textures[0].width;let removed=0,samples=0;
 if(a.elements.length!==b.elements.length||JSON.stringify(a.groups)!==JSON.stringify(b.groups)||JSON.stringify(a.outliner)!==JSON.stringify(b.outliner)||JSON.stringify(a.animations)!==JSON.stringify(b.animations))throw Error('Structure, bones or animations changed');
 for(const e of a.elements){const n=byId.get(e.uuid);for(const k of ['from','to','origin','rotation','inflate','visibility'])if(JSON.stringify(e[k])!==JSON.stringify(n[k]))throw Error('Geometry or visibility changed');
  for(const [side,f] of Object.entries(e.faces)){const nf=n.faces[side];if(f.texture==null){if(nf.texture!=null)throw Error('Unexpected face added');continue;}if(nf.texture==null){if(color!=='white')throw Error('Transparent face removed');removed++;continue;}
   if(f.rotation!==nf.rotation)throw Error('UV rotation changed');
   const u=f.uv,v=nf.uv,stepsX=Math.max(4,Math.ceil(Math.abs(u[2]-u[0]))*2),stepsY=Math.max(4,Math.ceil(Math.abs(u[3]-u[1]))*2);
   for(let y=0;y<stepsY;y++)for(let x=0;x<stepsX;x++){const tx=(x+.5)/stepsX,ty=(y+.5)/stepsY,idx=(uv,w)=>{const px=Math.max(0,Math.min(w-1,Math.floor(uv[0]+(uv[2]-uv[0])*tx))),py=Math.max(0,Math.min(w-1,Math.floor(uv[1]+(uv[3]-uv[1])*ty)));return (py*w+px)*4;},ia=idx(u,wa),ib=idx(v,wb);for(let k=0;k<4;k++)if(pa[ia+k]!==pb[ib+k])throw Error(`Sample mismatch ${color}: ${e.name}/${side}`);samples++;}
  }
 }
 return {color,textureBefore:wa,textureAfter:wb,baseRGBABytesBefore:wa*wa*4,baseRGBABytesAfter:wb*wb*4,removedFaces:removed,removedTriangles:removed*2,checkedTexelSamples:samples,texelSamplesIdentical:true,bonesAnimationsGeometryAndVisibilityUnchanged:true,projectBytesBefore:fs.statSync(path.join(here,`fox_${color}_contact.bbmodel`)).size,projectBytesAfter:fs.statSync(path.join(here,`fox_${color}_optimized.bbmodel`)).size};
}
async function sheet(){const cards=['white','black'].map((c,i)=>{const image=fs.readFileSync(path.join(here,`${c}-optimized-front.png`)).toString('base64'),x=16+i*416;return `<text x="${x+12}" y="38" fill="#eee" font-size="22">${c.toUpperCase()} · OPTIMIZED</text><image href="data:image/png;base64,${image}" x="${x}" y="55" width="400" height="595" preserveAspectRatio="xMidYMid meet"/>`;});await sharp(Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="848" height="666"><rect width="848" height="666" fill="#181820"/>${cards.join('')}</svg>`)).png().toFile(path.join(here,'optimized-preview.png'));}
(async()=>{const reports=[];for(const c of ['white','black'])reports.push(await verify(c));fs.writeFileSync(path.join(here,'optimized-verification.json'),JSON.stringify(reports,null,2));await sheet();console.log(reports);})().catch(e=>{console.error(e);process.exitCode=1;});
