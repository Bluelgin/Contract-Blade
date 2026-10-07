const fs=require('fs'),assert=require('assert/strict'),sharp=require('sharp');
const folder=__dirname,old=JSON.parse(fs.readFileSync(folder+'/../polish-v1/akatsuki-clothing-polish-v1.bbmodel','utf8')),next=JSON.parse(fs.readFileSync(folder+'/akatsuki-clothing-v2.bbmodel','utf8'));
const report=JSON.parse(fs.readFileSync(folder+'/validation.json','utf8')),targets=new Set(report.targets);
assert.equal(old.elements.length,next.elements.length);
for(const c of old.elements){const n=next.elements.find(e=>e.uuid===c.uuid);assert.ok(n);for(const key of ['type','name','from','to','origin','rotation','inflate'])assert.deepEqual(n[key],c[key],c.uuid+':'+key);if(!targets.has(c.uuid)){assert.deepEqual(n.faces,c.faces,c.uuid+':faces');assert.equal(n.box_uv,c.box_uv);}}
const groups=a=>a.flatMap(n=>typeof n==='string'?[]:[n,...groups(n.children||[])]);
const a=groups(old.outliner),b=groups(next.outliner);assert.equal(a.length,b.length);
for(const g of a){const n=b.find(e=>e.uuid===g.uuid);for(const key of ['name','origin','rotation','visibility'])assert.deepEqual(n[key],g[key],g.name+':'+key);assert.deepEqual((n.children||[]).map(x=>typeof x==='string'?x:x.uuid),(g.children||[]).map(x=>typeof x==='string'?x:x.uuid));}
async function main(){const png=await sharp(folder+'/akatsuki-clothing-v2.png').ensureAlpha().raw().toBuffer({resolveWithObject:true});assert.equal(png.info.width,256);assert.equal(png.info.height,256);const embedded=await sharp(Buffer.from(next.textures[0].source.split(',')[1],'base64')).ensureAlpha().raw().toBuffer();assert.ok(png.data.equals(embedded));report.independentValidation={pass:true,geometryAndHierarchyUnchanged:true,unrelatedFacesAndUVsUnchanged:true,textureDimensions:[256,256],embeddedTextureMatches:true,cubes:next.elements.length,groups:b.length};fs.writeFileSync(folder+'/validation.json',JSON.stringify(report,null,2));console.log(JSON.stringify(report.independentValidation));}
main().catch(e=>{console.error(e);process.exitCode=1;});
