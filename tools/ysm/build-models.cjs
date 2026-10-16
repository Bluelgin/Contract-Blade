// Local-only YSM conversion. Authoring models and maid packs are never overwritten.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '../..');
const template = path.join(root, 'tmp/ysm-test/default');
const output = path.join(root, 'art/ysm-test/models');
const install = path.join(root, 'run-ysm-test/config/yes_steve_model/custom');
const installClient = !process.argv.includes('--no-install');
const read = f => JSON.parse(fs.readFileSync(f, 'utf8'));
const write = (f, v) => { fs.mkdirSync(path.dirname(f), {recursive:true}); fs.writeFileSync(f, JSON.stringify(v, null, 2)); };
const copy = (a,b) => { fs.mkdirSync(path.dirname(b), {recursive:true}); fs.copyFileSync(a,b); };
const clone = v => structuredClone(v);
const {loadTaisho,applyTails}=require('./tail-motions.cjs');
const taisho=loadTaisho(root);
const specs = [
  {id:'contract_akatsuki', name:'赤月 · 粉白', bb:'art/akatsuki/crimson-wrap/akatsuki-crimson-wrap.bbmodel', geo:'art/akatsuki/crimson-wrap/akatsuki-crimson-wrap.geo.json', anim:'art/akatsuki/tlm-adaptation/effective-adapted.animation.json'},
  {id:'contract_white_fox', name:'白狐', bb:'art/fox_polish_preview/sleeve/fox_white_optimized.bbmodel', geo:'art/fox_polish_preview/sleeve/white-optimized.geo.json', anim:'tmp/ysm-test/white-native.animation.json'},
  {id:'contract_black_fox', name:'黑狐', bb:'art/fox_polish_preview/sleeve/fox_black_optimized.bbmodel', geo:'art/fox_polish_preview/sleeve/black-optimized.geo.json', anim:'tmp/ysm-test/black-native.animation.json'}
];
const aliases = {MAllBody:'MAllbody', Skirt:'clothe', LeftSkirt:'LeftClothe', RightSkirt:'RightClothe', FrontSkirt:'FrontClothe', BackSkirt:'BackClothe', RightEyesBase:'RightEyelidBase', LeftEyesBase:'LeftEyelidBase', RightPupil:'RightEyeDot', LeftPupil:'LeftEyeDot', Eyebrow:'EyeBrow', Collar:'lingzi'};
const report = [];
for (const spec of specs) {
  const bb = read(path.join(root,spec.bb)), geo = read(path.join(root,spec.geo));
  const g = geo['minecraft:geometry'][0];
  const hidden = new Set(['FOX','FoxMoonCrown']);
  for (const group of bb.groups ?? []) if(group.visibility === false || group.export === false) hidden.add(group.name);
  let changed;
  do { changed=false; for(const b of g.bones) if(hidden.has(b.parent) && !hidden.has(b.name)){hidden.add(b.name);changed=true;} } while(changed);
  g.bones = g.bones.filter(b => !hidden.has(b.name));
  // The CC0 template uses a non-rendered bone to evaluate animation variables.
  if(!g.bones.some(b=>b.name==='molang')) g.bones.push({name:'molang',parent:'Root',pivot:[0,0,0]});
  if(!g.bones.some(b=>b.name==='ViewLocator')) g.bones.push({name:'ViewLocator',parent:'Head',pivot:[0,34,0]});
  g.description.identifier='geometry.'+spec.id;
  g.description.visible_bounds_width=6; g.description.visible_bounds_height=6; g.description.visible_bounds_offset=[0,2,0];
  const names = new Set(g.bones.map(b=>b.name));
  assert.equal(names.size,g.bones.length,'duplicate bone names');
  for(const b of g.bones) assert.ok(!b.parent || names.has(b.parent),b.name+' parent');
  const arms = new Set(['LeftArm','RightArm']);
  do { changed=false; for(const b of g.bones) if(arms.has(b.parent) && !arms.has(b.name)){arms.add(b.name);changed=true;} } while(changed);
  const armGeo=clone(geo); const ag=armGeo['minecraft:geometry'][0];
  ag.description.identifier='geometry.'+spec.id+'.arm';
  ag.bones=ag.bones.filter(b=>arms.has(b.name));
  for(const b of ag.bones) if(b.name==='LeftArm'||b.name==='RightArm'){delete b.parent;b.rotation=[0,0,0];}
  const out=path.join(output,spec.id);
  write(path.join(out,'models/main.json'),geo); write(path.join(out,'models/arm.json'),armGeo);
  assert.equal(bb.textures.length,1,'single atlas required');
  const data=bb.textures[0].source;
  assert.ok(data.startsWith('data:image/png;base64,'));
  fs.mkdirSync(path.join(out,'textures'),{recursive:true});
  fs.writeFileSync(path.join(out,'textures/default.png'),Buffer.from(data.split(',')[1],'base64'));
  const source=read(path.join(root,spec.anim)).animations;
  const mappedName=n=> names.has(n)?n:(names.has(aliases[n])?aliases[n]:null);
  function retarget(clip, allowed=names) {
    const c=clone(clip); delete c.sound_effects; delete c.particle_effects;
    c.bones={};
    for(const [name,track] of Object.entries(clip.bones??{})) {
      const target=mappedName(name); if(target && allowed.has(target)) c.bones[target]=clone(track);
    }
    return c;
  }
  const files={}; let clips=0, tracks=0, tailMotions;
  const defaults=read(path.join(template,'ysm.json')).files.player.animation;
  const originalMain=['idle','walk','run','jump','attacked','swim','swim_stand','sleep','ladder_up','ladder_down','ladder_stillness'];
  for(const [category,relative] of Object.entries(defaults)) {
    const animations={};
    const allowed=category==='fp_arm'?new Set([...arms,'molang']):names;
    for(const [key,clip] of Object.entries(read(path.join(template,relative)).animations)) animations[key]=retarget(clip,allowed);
    if(category==='main') {
      for(const key of originalMain) if(source[key]) animations[key]=retarget(source[key]);
      // Keep the humanoid visible at low health; TLM fox-form controller is not a player state.
      if(source.parallel1) {
        animations.parallel0=retarget(source.parallel1);
        delete animations.parallel0.bones.AllBody;
      }
      if(source.pre_parallel1) animations.pre_parallel0=retarget(source.pre_parallel1);
      if(source.pre_parallel0) animations.pre_parallel1=retarget(source.pre_parallel0);
      for(const key of ['parallel2','parallel3']) if(source[key]) animations[key]=retarget(source[key]);
      delete animations.parallel1.bones?.Ribbon;
      // Mirror ordinary tail deltas onto the two additional spirit tails.
      for(const c of Object.values(animations)) for(const [n,t] of Object.entries(c.bones??{})) if(/^Tail[2-7]?$/.test(n)) {
        for(const side of ['LeftSpirit_','RightSpirit_']) if(names.has(side+n)&&!c.bones[side+n]) c.bones[side+n]=clone(t);
      }
      for(const key of ['climb','climbing','swim','elytra_fly','riptide']) if(animations[key]) {
        for(const n of ['Tail','LeftSpirit_Tail','RightSpirit_Tail']) if(names.has(n)) animations[key].bones[n]={rotation:[-42,0,0]};
        for(const n of ['Tail2','LeftSpirit_Tail2','RightSpirit_Tail2']) if(names.has(n)) animations[key].bones[n]={rotation:[-12,0,0]};
        for(const n of ['Tail3','LeftSpirit_Tail3','RightSpirit_Tail3']) if(names.has(n)) animations[key].bones[n]={rotation:[-8,0,0]};
      }
      tailMotions=applyTails(animations,g.bones,taisho,retarget);
    }
    if(category==='tlm') for(const key of Object.keys(animations)) if(source[key]) animations[key]=retarget(source[key]);
    for(const [key,c] of Object.entries(animations)) {
      assert.ok(c && typeof c==='object',key);
      for(const n of Object.keys(c.bones??{})) assert.ok(allowed.has(n),category+': '+n);
      clips++;tracks+=Object.keys(c.bones??{}).length;
    }
    const finalRelative='animations/'+category+'.animation.json';
    write(path.join(out,finalRelative),{format_version:'1.8.0',animations}); files[category]=finalRelative;
  }
  write(path.join(out,'ysm.json'),{
    spec:2,
    metadata:{name:spec.name,tips:'契约之刃 · 本地 YSM 玩家测试版（不包含 Boss 技能）',
      authors:[{name:'TartaricAcid / 酒石酸菌及 Wine Fox contributors',role:'原酒狐模型'}, {name:'Blade Tetra contributors / 用户提供角色包',role:'角色及模型适配'}, ...read(path.join(template,'ysm.json')).metadata.authors.map(a=>({name:a.name,role:'YSM CC0 基础动作 / '+a.role})),{name:'星屑海螺、祸御神、羊毛及大正酒狐动画贡献者',role:'CC BY-NC-SA 4.0 原版旋转尾巴飞行动作'}],
      license:{type:spec.id==='contract_akatsuki'?'原角色授权保留 · 本地测试':'CC BY-NC-SA 4.0',desc:'模型原授权保留；YSM 默认动作 CC0。详见 RESOURCE_NOTES.md，不统一改为 MIT。'}},
    properties:{height_scale:0.7,width_scale:0.7,preview_animation:'idle',default_texture:'default',free:true},
    files:{player:{model:{main:'models/main.json',arm:'models/arm.json'},animation:files,texture:['textures/default.png']}}
  });
  copy(path.join(root,'art/akatsuki/tlm-adaptation/RESOURCE_NOTES.md'),path.join(out,'AKATSUKI_SOURCE_NOTES.md'));
  fs.copyFileSync(path.join(root,'art/ysm-test/RESOURCE_NOTES.md'),path.join(out,'RESOURCE_NOTES.md'));
  write(path.join(out,'TAISHO_FLIGHT_CREDITS.json'),{source:taisho.source,metadata:taisho.metadata,reused:'fly animation only; no controller, music or texture imported',adaptation:'三尾展开相位补偿；新增低优先级错拍尾巴动作'});
  copy(path.join(root,'tmp/ysm-test/fox-notices/LICENSE-CC-BY-NC-SA-4.0.txt'),path.join(out,'LICENSE-CC-BY-NC-SA-4.0.txt'));
  if(spec.id!=='contract_akatsuki') {
    for(const f of ['LICENSE-CC-BY-NC-SA-4.0.txt','CREDITS.md']) {
      const from=path.join(root,'tmp/ysm-test/fox-notices',f);
      assert.ok(fs.existsSync(from),'Missing original notice: '+f);copy(from,path.join(out,f));
    }
  } else copy('E:/Akatsuki-Taisho-Wine-Fox/assets/akatsuki_winefox/maid_model.json',path.join(out,'original-maid-metadata.json'));
  const installed=path.join(install,spec.id);
  if(installClient){fs.mkdirSync(installed,{recursive:true}); fs.cpSync(out,installed,{recursive:true});}
  report.push({id:spec.id,bones:g.bones.length,cubes:g.bones.reduce((v,b)=>v+(b.cubes?.length??0),0),firstPersonArmBones:ag.bones.length,clips,tracks,hiddenBonesRemoved:hidden.size,unresolvedTracks:0,tailMotions,installed:installClient?installed:null});
}
write(path.join(root,'art/ysm-test/conversion-validation.json'),report);
console.log(JSON.stringify(report,null,2));
