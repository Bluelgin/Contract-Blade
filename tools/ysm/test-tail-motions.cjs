const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../..');
const read=f=>JSON.parse(fs.readFileSync(f,'utf8'));
const source=read(path.join(root,'art/ysm-test/taisho-flight-source.json'));
const checkInstall=!process.argv.includes('--no-install');
const results=[];
for(const id of ['contract_akatsuki','contract_white_fox','contract_black_fox']){
  const dir=path.join(root,'art/ysm-test/models',id),installed=path.join(root,'run-ysm-test/config/yes_steve_model/custom',id);
  const bones=read(path.join(dir,'models/main.json'))['minecraft:geometry'][0].bones;
  const names=new Set(bones.map(b=>b.name));
  const animations=read(path.join(dir,'animations/main.animation.json')).animations;
  assert.deepEqual(animations.fly.bones.Tail,source.fly.bones.Tail);
  assert.equal(animations.fly.animation_length,source.fly.animation_length);
  assert.equal(animations.fly.loop,true);
  assert.ok(!animations.fly.bones.FOX && !animations.fly.bones.wb);
  assert.deepEqual(animations.fly.bones.LeftArm,source.fly.bones.LeftArm);
  for(const c of Object.values(animations))for(const name of Object.keys(c.bones??{}))assert.ok(names.has(name),name);
  if(id!=='contract_akatsuki'){
    const physics=animations.pre_parallel3;
    assert.equal(Object.keys(physics.bones).length,22);
    assert.ok(Object.keys(physics.bones).every(n=>n==='molang2'||/^(?:LeftSpirit_|RightSpirit_)?Tail[2-7]?$/.test(n)));
    const calls=[...JSON.stringify(physics).matchAll(/ysm\.second_order\('([^']+)'/g)].map(x=>x[1]);
    assert.equal(calls.length,32);assert.equal(new Set(calls).size,32);
    assert.ok(JSON.stringify(physics).includes("ysm.bone_pos('Root').y"),'tail must follow actual main-animation bounce');
    assert.ok(physics.bones.molang2.rotation[0].includes('ctrl.walk||ctrl.run'));
    for(const [prefix,offset] of [['',0],['LeftSpirit_',-0.14],['RightSpirit_',0.14]]){
      const frequencies=Array.from({length:7},(_,i)=>{
        const n=prefix+(i===0?'Tail':'Tail'+(i+1));
        const match=physics.bones[n].rotation[0].match(/pitch_\d',.+,([\d.]+),([\d.]+),0\)/);
        assert.ok(match,n+' pitch response');return Number(match[1]);
      });
      assert.deepEqual(frequencies,[3.6,3.4,3.1,2.8,2.5,2.2,2.0].map(v=>Number((v+offset).toFixed(2))));
    }
    assert.notDeepEqual(physics.bones.Tail,physics.bones.LeftSpirit_Tail);
    assert.notDeepEqual(physics.bones.LeftSpirit_Tail,physics.bones.RightSpirit_Tail);
    for(const key of ['idle','walk','run','jump'])assert.ok(!Object.keys(animations[key].bones).some(n=>/Tail/.test(n)),key+' overrides ordinary tail motion');
    const center=bones.find(b=>b.name==='Tail').rotation;
    for(const [prefix,phase]of [['LeftSpirit_',120],['RightSpirit_',240]]){
      const name=prefix+'Tail',rest=bones.find(b=>b.name===name).rotation,t=animations.fly.bones[name].rotation;
      const keys=Object.keys(t);
      for(let axis=0;axis<3;axis++){
        const start=t[keys[0]][axis]+rest[axis],expect=source.fly.bones.Tail.rotation['0.0'][axis]+center[axis]+(axis===2?phase:0);
        assert.ok(Math.abs(start-expect)<1e-7,'rest-angle compensation');
      }
      assert.ok(Math.abs(t[keys.at(-1)][2]-t[keys[0]][2]-1080)<1e-7,'unwrapped spin lost');
      for(const key of ['climb','climbing','swim','elytra_fly','riptide'])assert.deepEqual(animations[key].bones[name].rotation,[-42,0,0]);
    }
  }
  if(checkInstall)for(const relative of ['models/main.json','models/arm.json','textures/default.png','animations/main.animation.json','ysm.json'])assert.deepEqual(fs.readFileSync(path.join(dir,relative)),fs.readFileSync(path.join(installed,relative)),'install mismatch');
  results.push({id,originalFlyTailPreserved:true,noMissingBones:true,threeTailChecks:id!=='contract_akatsuki'?'passed':'not applicable',installedMatches:checkInstall?true:null});
}
fs.writeFileSync(path.join(root,'art/ysm-test/tail-motion-tests.json'),JSON.stringify({results,inGameVisualVerification:false},null,2));
console.log(JSON.stringify(results,null,2));
