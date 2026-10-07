const fs=require('fs'),assert=require('assert/strict'),sharp=require('sharp'),dir=__dirname;
const report=JSON.parse(fs.readFileSync(dir+'/validation.json','utf8'));
const old=JSON.parse(fs.readFileSync(report.source,'utf8')),next=JSON.parse(fs.readFileSync(dir+'/akatsuki-midnight-b.bbmodel','utf8'));
const changed=new Set(report.targets),geometry=new Set(report.changedGeometry),byId=new Map(next.elements.map(c=>[c.uuid,c]));
assert.equal(next.elements.length,407);assert.deepEqual(next.outliner,old.outliner);assert.deepEqual(next.animations,old.animations);
for(const c of old.elements){const n=byId.get(c.uuid);assert.ok(n);if(!geometry.has(c.uuid))for(const k of ['from','to','origin','rotation','inflate'])assert.deepEqual(n[k],c[k],c.uuid+':'+k);assert.equal(n.visibility,c.visibility);}
async function main(){
 const a=await sharp(Buffer.from(old.textures[0].source.split(',')[1],'base64')).ensureAlpha().raw().toBuffer({resolveWithObject:true});
 const b=await sharp(dir+'/akatsuki-midnight-b.png').ensureAlpha().raw().toBuffer({resolveWithObject:true});
 const embedded=await sharp(Buffer.from(next.textures[0].source.split(',')[1],'base64')).ensureAlpha().raw().toBuffer();assert.ok(b.data.equals(embedded));
 assert.equal(b.info.width,512);assert.equal(b.info.height,512);
 function sample(image,uv,u,v){const {width,height}=image.info;const x=Math.max(0,Math.min(width-1,Math.floor(uv[0]+(uv[2]-uv[0])*u))),y=Math.max(0,Math.min(height-1,Math.floor(uv[1]+(uv[3]-uv[1])*v))),i=(y*width+x)*4;return image.data.subarray(i,i+4);}
 let samples=0;
 for(const c of old.elements){if(changed.has(c.uuid))continue;const n=byId.get(c.uuid);for(const [side,f]of Object.entries(c.faces||{})){if(f.texture===null||!f.uv)continue;const nf=n.faces[side];assert.notEqual(nf.texture,null);for(let y=0;y<16;y++)for(let x=0;x<16;x++){const u=(x+.37)/16,v=(y+.41)/16;assert.ok(sample(a,f.uv,u,v).equals(sample(b,nf.uv,u,v)),c.uuid+':'+side+':'+x+','+y);samples++;}}}
 const clasp=byId.get('956776e2-aa27-d41b-cdb9-c7b5d83b0b96'),support=byId.get('f6ad4d26-132d-7072-8d48-cc0a8ff089ac');
 assert.ok(clasp.from[2]<support.from[2]&&clasp.to[2]>support.from[2]&&clasp.to[2]<support.to[2]);
 for(const i of [0,1])assert.ok(clasp.from[i]>=support.from[i]&&clasp.to[i]<=support.to[i]);
 report.independentValidation={pass:true,unrelatedTextureSamples:samples,unrelatedGeometryUnchanged:true,bonesAnimationsAndHierarchyUnchanged:true,headMoonUnchanged:true,claspEmbeddedInExistingSupport:true,cubes:407,texture:[512,512]};
 fs.writeFileSync(dir+'/validation.json',JSON.stringify(report,null,2));console.log(JSON.stringify(report.independentValidation));
}
main().catch(e=>{console.error(e);process.exitCode=1;});
