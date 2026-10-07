// Derive editable model copies and renderer-neutral, bone-name based clips.
// Original projects and existing animations are never overwritten.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const here = __dirname;
const blackPath = path.join(here, 'fox_black_preview.bbmodel');
const black = JSON.parse(fs.readFileSync(blackPath, 'utf8'));
const sequence = black.animations.find(a => a.name === 'animation.contract_blade.swordplay_preview');
if (!sequence) throw new Error('MCP-authored sequence is missing');
const prefix = 'animation.contract_blade.';
const definitions = [
  ['ready', 0, 0.8, true],
  ['draw_slash', 0.8, 1.65, false],
  ['parry_return', 2.3, 3.25, false],
  ['sheath', 3.25, 5.2, false],
];
function sample(keys, t) {
  const before = [...keys].reverse().find(k => k.time <= t) || keys[0];
  const after = keys.find(k => k.time >= t) || keys.at(-1);
  const f = before.time === after.time ? 0 : (t-before.time)/(after.time-before.time);
  return Object.fromEntries(['x','y','z'].map(axis => [axis, String(Number(before.data_points[0][axis]) * (1-f) + Number(after.data_points[0][axis]) * f)]));
}
function clip(name, start, end, loop) {
  const result = {name:prefix+name,uuid:crypto.randomUUID(),loop:loop?'loop':'once',length:end-start,snapping:60,override:false,animators:{}};
  for (const [id, source] of Object.entries(sequence.animators)) {
    if (!source.keyframes?.length || source.name === 'FOX') continue;
    const animator = {...source,keyframes:[]};
    for (const channel of [...new Set(source.keyframes.map(k=>k.channel))]) {
      const keys = source.keyframes.filter(k=>k.channel===channel).sort((a,b)=>a.time-b.time);
      const times = [...new Set([start,...keys.filter(k=>k.time>start&&k.time<end).map(k=>k.time),end])];
      for (const time of times) animator.keyframes.push({channel,time:Number((time-start).toFixed(4)),uuid:crypto.randomUUID(),data_points:[sample(keys, loop?start:time)],color:-1,interpolation:'linear'});
    }
    result.animators[id] = animator;
  }
  return result;
}
const clips = definitions.map(args=>clip(...args));
black.animations = black.animations.filter(a=>!definitions.some(d=>a.name===prefix+d[0])).concat(clips);
fs.writeFileSync(blackPath, JSON.stringify(black));
const whiteSource = 'E:/刃录/art/characters/white_fox/fox_white.bbmodel';
const white = JSON.parse(fs.readFileSync(whiteSource,'utf8'));
const whiteIds = new Map(white.groups.map(g=>[g.name,g.uuid]));
for (const g of white.groups) if (g.name==='FOX'||g.name==='FoxMoonCrown') g.visibility=false;
const whiteAnimations = [sequence,...clips].map(a=>{
  const copy = structuredClone(a); copy.uuid=crypto.randomUUID(); copy.animators={};
  for (const bone of Object.values(a.animators)) {
    if (!bone.keyframes?.length) continue;
    const id=whiteIds.get(bone.name);
    if (!id) throw new Error('White fox missing bone '+bone.name);
    copy.animators[id]=structuredClone(bone);
    copy.animators[id].keyframes.forEach(k=>k.uuid=crypto.randomUUID());
  }
  return copy;
});
white.animations.push(...whiteAnimations);
fs.writeFileSync(path.join(here,'fox_white_preview.bbmodel'),JSON.stringify(white));
const exported = {format_version:'1.8.0',animations:{}};
for (const a of clips) {
  const bones={};
  for (const bone of Object.values(a.animators)) {
    bones[bone.name]={};
    for (const key of bone.keyframes) {
      const p=key.data_points[0];
      const vector=[Number(p.x),Number(p.y),Number(p.z)];
      // Blockbench authoring -> Bedrock/Gecko animation coordinate convention.
      if (key.channel==='rotation') {vector[0]*=-1;vector[1]*=-1;}
      if (key.channel==='position') vector[0]*=-1;
      (bones[bone.name][key.channel]??={})[String(key.time)]=vector;
    }
  }
  exported.animations[a.name]={loop:a.loop==='loop',animation_length:a.length,bones};
}
fs.writeFileSync(path.join(here,'contract_swordplay.animation.json'),JSON.stringify(exported,null,2));
// Validate remapping, finite keyframes and clip bounds.
for (const model of [black,white]) {
  const ids = new Set(model.groups.map(g=>g.uuid));
  for (const a of model.animations.filter(a=>a.name.startsWith(prefix))) {
    for (const [id,bone] of Object.entries(a.animators)) {
      if (!ids.has(id)) throw new Error('Unmapped bone');
      for (const k of bone.keyframes||[]) {
        if(k.time<0||k.time>a.length+0.0001) throw new Error('Key outside clip');
        if(!Object.values(k.data_points[0]).every(v=>Number.isFinite(Number(v)))) throw new Error('Invalid key');
      }
    }
  }
}
const blackSource='E:/刃录/art/characters/black_fox/fox_black.bbmodel';
const originalBlack=JSON.parse(fs.readFileSync(blackSource,'utf8'));
const originalWhite=JSON.parse(fs.readFileSync(whiteSource,'utf8'));
for (const [original,copy] of [[originalBlack,black],[originalWhite,white]]) {
  if (original.elements.length!==copy.elements.length) throw new Error('Preview changed geometry count');
  for (const a of original.animations) if (!copy.animations.some(c=>c.name===a.name)) throw new Error('Original animation lost');
}
fs.writeFileSync(path.join(here,'verification.json'),JSON.stringify({
  stage:'Blockbench preview only; in-game rendering not verified',
  clips:clips.map(a=>({name:a.name,length:a.length,bones:Object.keys(a.animators).length})),
  originals:[blackSource,whiteSource].map(file=>({file,sha256:crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex')})),
  geometryCountsPreserved:true, originalAnimationsPreserved:true, whiteBoneReferencesValid:true,
  temporaryEquipmentOverlayExported:false
},null,2));
console.log('Validated: 4 shared clips + sequence, black/white bone mapping. Preview only; not deployed.');
