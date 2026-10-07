const fs = require('fs');
const assert = require('assert/strict');
const sharp = require('sharp');
const crypto = require('crypto');
const folder = __dirname;
const sourcePath = 'E:/Akatsuki-Taisho-Wine-Fox/source/akatsuki-1.2.3.bbmodel';
const sourceBytes = fs.readFileSync(sourcePath);
const source = JSON.parse(sourceBytes);
const result = JSON.parse(fs.readFileSync(folder+'/akatsuki-clothing-polish-v1.bbmodel','utf8'));
const keys = ['uuid','type','name','from','to','origin','rotation','uv_offset','box_uv','inflate'];
for (const element of source.elements) {
  const next = result.elements.find(e=>e.uuid===element.uuid);
  assert.ok(next,element.uuid);
  for(const key of keys)assert.deepEqual(next[key],element[key],element.uuid+':'+key);
}
const flatten = nodes=>nodes.flatMap(n=>typeof n==='string'?[]:[n,...flatten(n.children||[])]);
const oldGroups=flatten(source.outliner),newGroups=flatten(result.outliner);
assert.equal(source.elements.length,result.elements.length);
assert.equal(oldGroups.length,newGroups.length);
for(const old of oldGroups){const next=newGroups.find(g=>g.uuid===old.uuid);for(const key of ['name','origin','rotation','visibility'])assert.deepEqual(next[key],old[key],old.name+':'+key);}
async function main(){
  const old=await sharp(Buffer.from(source.textures[0].source.split(',')[1],'base64')).ensureAlpha().raw().toBuffer({resolveWithObject:true});
  const next=await sharp(folder+'/akatsuki-clothing-polish-v1.png').ensureAlpha().raw().toBuffer({resolveWithObject:true});
  assert.deepEqual(next.info,old.info);
  let changed=0;
  for(let i=0;i<old.data.length;i+=4){assert.equal(next.data[i+3],old.data[i+3]);if(!next.data.subarray(i,i+3).equals(old.data.subarray(i,i+3)))changed++;}
  const embedded=await sharp(Buffer.from(result.textures[0].source.split(',')[1],'base64')).ensureAlpha().raw().toBuffer();
  assert.ok(embedded.equals(next.data));
  const report=JSON.parse(fs.readFileSync(folder+'/validation.json','utf8'));
  assert.equal(changed,report.changedTexels);
  report.independentValidation={pass:true,geometryAndGroupTransformsUnchanged:true,allGroupsRetained:true,alphaUnchanged:true,embeddedTextureMatches:true,changedTexels:changed,sourceSha256:crypto.createHash('sha256').update(sourceBytes).digest('hex')};
  fs.writeFileSync(folder+'/validation.json',JSON.stringify(report,null,2));
  console.log(JSON.stringify(report.independentValidation));
}
main().catch(e=>{console.error(e);process.exitCode=1;});
