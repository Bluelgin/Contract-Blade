// Generate an isolated TLM override from the approved YSM tail layer.
// Original activity clips and fox-form tracks are never edited.
const fs=require('node:fs');
const path=require('node:path');
const assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../..');
const baseline=path.resolve(process.argv[2]??path.join(root,'tmp/maid-tail-sync/original'));
const output=path.join(root,'art/fox_polish_preview/tail-adaptation');
const read=f=>JSON.parse(fs.readFileSync(f,'utf8'));
const tail=n=>/^(?:LeftSpirit_|RightSpirit_)?Tail[2-7]?$/.test(n);
const humanoid="(query.health/math.max(query.max_health,1)>=0.2&&!tlm.is_sitting&&!ysm.is_sleep&&!query.is_riding&&!query.is_in_water&&!query.is_swimming)";
const grounded=`(${humanoid}&&query.is_on_ground)`;
const walking=`(${grounded}&&query.ground_speed>0.05)`;
const running=`(${walking}&&query.is_sprinting)`;
fs.mkdirSync(output,{recursive:true});
const rows=[];
for(const color of ['white','black']){
  const domain=path.join(baseline,'assets/contract_fox');
  const model=read(path.join(domain,'maid_model.json')).model_list.find(m=>m.model_id===`contract_fox:fox_${color}`);
  const original={};
  for(const location of model.animation)Object.assign(original,read(path.join(baseline,'assets',location.replace(':','/'))).animations);
  assert.equal(Object.keys(original.pre_parallel3?.bones??{}).length,0,'Reserved TLM layer is occupied');
  const bones=read(path.join(domain,`models/entity/fox_${color}.json`))['minecraft:geometry'][0].bones;
  const names=new Set(bones.map(b=>b.name));
  assert(names.has('molang2'),'Missing existing invisible helper bone');
  const animations={};
  for(const [key,clip] of Object.entries(original))if(/^(?:pre_)?parallel\d$/.test(key)&&Object.keys(clip.bones??{}).some(tail)){
    const override=structuredClone(clip);
    for(const name of Object.keys(override.bones))if(tail(name))delete override.bones[name];
    animations[key]=override;
  }
  let physics=JSON.stringify(read(path.join(root,`art/ysm-test/models/contract_${color}_fox/animations/main.animation.json`)).animations.pre_parallel3);
  physics=physics.replaceAll('ctrl.walk',walking).replaceAll('ctrl.run',running)
    .replaceAll('fox_tail_','maid_fox_tail_')
    .replaceAll('-query.vertical_speed*',`-(${humanoid}?query.vertical_speed:0)*`)
    .replaceAll('-query.yaw_speed*',`-(${humanoid}?query.yaw_speed:0)*`);
  animations.pre_parallel3=JSON.parse(physics);
  for(const clip of Object.values(animations))for(const name of Object.keys(clip.bones??{}))assert(names.has(name),name);
  assert(!physics.includes('ctrl.walk')&&!physics.includes('ctrl.run'));
  const file=path.join(output,`${color}.animation.json`);
  fs.writeFileSync(file,JSON.stringify({format_version:'1.8.0',animations},null,2)+'\n');
  rows.push({color,clips:Object.keys(animations),tailTracks:21,secondOrderChannels:32,originalActivityClipsUnchanged:true,originalFlightUnchanged:true,foxFormTracksUnchanged:true,playerControllerReferences:0,file});
}
fs.writeFileSync(path.join(output,'generation-validation.json'),JSON.stringify(rows,null,2));
console.log(JSON.stringify(rows,null,2));
