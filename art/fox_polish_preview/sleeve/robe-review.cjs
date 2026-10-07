const fs=require('fs'),path=require('path'),sharp=require('C:/Users/Administrator/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp');
const here=__dirname;
(async()=>{
 const read=s=>JSON.parse(fs.readFileSync(path.join(here,`fox_white_${s}.bbmodel`),'utf8')),a=read('shoulder_complete'),b=read('robe');
 const pixel=async p=>sharp(Buffer.from(p.textures[0].source.split(',')[1],'base64')).ensureAlpha().raw().toBuffer(),pa=await pixel(a),pb=await pixel(b),byId=new Map(b.elements.map(e=>[e.uuid,e]));let changed=0;
 for(const e of a.elements){const n=byId.get(e.uuid);if(!n)throw Error('Missing cube');
  for(const k of ['from','to','origin','rotation'])if(JSON.stringify(e[k])!==JSON.stringify(n[k]))throw Error('Geometry changed');
  for(const [side,f] of Object.entries(e.faces)){
   if(JSON.stringify(f.uv)!==JSON.stringify(n.faces[side].uv)){if(!/^Merged(?:Front|Side)Robe/.test(e.name)&&!/^(?:[LR][FBM][123]?|B[LRM]2?|F[LRM]1?)$/.test(e.name))throw Error('UV changed outside robe');changed++;}
   if(f.texture==null)continue;const u=f.uv;
   for(let y=Math.floor(Math.min(u[1],u[3]));y<Math.ceil(Math.max(u[1],u[3]));y++)for(let x=Math.floor(Math.min(u[0],u[2]));x<Math.ceil(Math.max(u[0],u[2]));x++){const i=(y*1024+x)*4;for(let k=0;k<4;k++)if(pa[i+k]!==pb[i+k])throw Error('Original pixel overwritten');}
  }
 }
 if(a.elements.length!==b.elements.length||JSON.stringify(a.groups)!==JSON.stringify(b.groups)||JSON.stringify(a.animations)!==JSON.stringify(b.animations))throw Error('Structure changed');
 const report={color:'white',changedRobeFaces:changed,geometryAndAnimationsUnchanged:true,otherUVAndReferencedPixelsUnchanged:true};fs.writeFileSync(path.join(here,'robe-verification.json'),JSON.stringify(report,null,2));
 const cards=['before','after'].map((s,i)=>{const image=fs.readFileSync(path.join(here,`robe-overview-${s}.png`)).toString('base64'),x=16+i*416;return `<text x="${x+12}" y="38" fill="#eee" font-size="22">${s.toUpperCase()}</text><image href="data:image/png;base64,${image}" x="${x}" y="55" width="400" height="595" preserveAspectRatio="xMidYMid meet"/>`;});
 await sharp(Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="848" height="666"><rect width="848" height="666" fill="#181820"/>${cards.join('')}</svg>`)).png().toFile(path.join(here,'robe-preview.png'));console.log(report);
})().catch(e=>{console.error(e);process.exitCode=1;});
