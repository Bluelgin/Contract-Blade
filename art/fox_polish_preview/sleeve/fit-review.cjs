const fs=require('fs'),path=require('path'),sharp=require('C:/Users/Administrator/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/sharp');
const here=__dirname,contact=process.argv.includes('--contact');
const geometryAllowed=e=>contact?(/^(RefCord|RefKnot|RefMagatama|RefCharmSuspension)/.test(e.name)||/^Covenant(?:Obi|WaistCord)$/.test(e.name)):(/^(RefCord|RefKnot|RefMagatama)/.test(e.name)||/^Layer(?:Continuous|FacingBend|VerticalOuterGold|DiagonalOuterGold|VerticalFacingShadow|DiagonalFacingShadow|RobeFold)/.test(e.name));
const uvAllowed=e=>/^LayerFur/.test(e.name)||/^Shoulder(?:-?1)$/.test(e.name)||(/^(Left|Right)Arm$/.test(e.name)&&e.from[1]>29&&e.to[1]<31)||/^Merged(?:Front|Side)Robe/.test(e.name)||/^(?:[LR][FBM][123]?|B[LRM]2?|F[LRM]1?)$/.test(e.name);
async function verify(color){
 const read=s=>JSON.parse(fs.readFileSync(path.join(here,`fox_${color}_${s}.bbmodel`),'utf8')),a=read(contact?'fitted':color==='white'?'robe':'chest'),b=read(contact?'contact':'fitted'),byId=new Map(b.elements.map(e=>[e.uuid,e]));
 const pixels=async p=>sharp(Buffer.from(p.textures[0].source.split(',')[1],'base64')).ensureAlpha().raw().toBuffer(),pa=await pixels(a),pb=await pixels(b);let moved=0,uv=0;
 for(const e of a.elements){const n=byId.get(e.uuid);if(!n)throw Error('Missing cube');
  const change=['from','to','origin','rotation'].some(k=>JSON.stringify(e[k])!==JSON.stringify(n[k]));
  if(change){if(!geometryAllowed(e))throw Error('Unexpected geometry change: '+e.name);for(let axis=0;axis<3;axis++)if(Math.abs((e.to[axis]-e.from[axis])-(n.to[axis]-n.from[axis]))>1e-7)throw Error('Resized cube');moved++;}
  for(const [side,f] of Object.entries(e.faces)){
   if(JSON.stringify(f.uv)!==JSON.stringify(n.faces[side].uv)){if(contact||color==='white'||!uvAllowed(e))throw Error('Unexpected UV change');uv++;}
   if(f.texture==null||JSON.stringify(f.uv)!==JSON.stringify(n.faces[side].uv))continue;const u=f.uv;
   for(let y=Math.floor(Math.min(u[1],u[3]));y<Math.ceil(Math.max(u[1],u[3]));y++)for(let x=Math.floor(Math.min(u[0],u[2]));x<Math.ceil(Math.max(u[0],u[2]));x++){const i=(y*1024+x)*4;for(let k=0;k<4;k++)if(pa[i+k]!==pb[i+k])throw Error(`Original referenced pixel overwritten: ${color}, ${e.name}, ${x},${y}, channel ${k}, ${pa[i+k]} -> ${pb[i+k]}`);}
  }
 }
 if(a.elements.length!==b.elements.length||JSON.stringify(a.groups)!==JSON.stringify(b.groups)||JSON.stringify(a.animations)!==JSON.stringify(b.animations))throw Error('Structure or animation changed');
 return {color,fittedCubes:moved,repaintedFaces:uv,cubeSizesUnchanged:true,bonesAndAnimationsUnchanged:true,unrelatedGeometryUVAndPixelsUnchanged:true};
}
async function sheet(files,labels,out){const cards=files.map((file,i)=>{const src=fs.readFileSync(path.join(here,file)).toString('base64'),x=16+i*416;return `<text x="${x+12}" y="38" fill="#eee" font-size="22">${labels[i]}</text><image href="data:image/png;base64,${src}" x="${x}" y="55" width="400" height="595" preserveAspectRatio="xMidYMid meet"/>`;});await sharp(Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="848" height="666"><rect width="848" height="666" fill="#181820"/>${cards.join('')}</svg>`)).png().toFile(path.join(here,out));}
(async()=>{const reports=[];for(const color of ['white','black'])reports.push(await verify(color));const prefix=contact?'contact':'fit';fs.writeFileSync(path.join(here,prefix+'-verification.json'),JSON.stringify(reports,null,2));await sheet([`white-${prefix}-front.png`,`black-${prefix}-front.png`],['WHITE FOX','BLACK FOX'],prefix+'-preview.png');await sheet(contact?['white-fit-side-after.png','white-contact-side.png']:['white-fit-side-before.png','white-fit-side-after.png'],['SIDE · BEFORE','SIDE · AFTER'],prefix+'-side-preview.png');console.log(reports);})().catch(e=>{console.error(e);process.exitCode=1;});
