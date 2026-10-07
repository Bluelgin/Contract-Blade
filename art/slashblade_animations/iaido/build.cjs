const fs = require('node:fs'), path = require('node:path'), crypto = require('node:crypto');
const source = path.resolve(__dirname, '../../fox_polish_preview/sleeve/fox_black_optimized.bbmodel');
const model = JSON.parse(fs.readFileSync(source, 'utf8'));
const ids = new Map(model.groups.map(g => [g.name, g.uuid]));
const pose = {
  Root: {idle:[0,0,0], charge:[0,24,0], cut:[0,65,0], recover:[0,40,0]},
  UpperBody: {idle:[0,0,0], charge:[15,-8,-4], cut:[10,12,0], recover:[8,0,1]},
  Head: {idle:[0,0,0], charge:[-10,-16,3], cut:[-8,-55,0], recover:[-4,-32,-2]},
  RightArm: {idle:[0,0,0], charge:[28,-25,-30], cut:[20,-10,-65], recover:[28,-16,-44]},
  RightForeArm: {idle:[0,0,0], charge:[35,0,-32], cut:[4,0,-8], recover:[12,0,-10]},
  RightHand: {idle:[0,0,0], charge:[0,0,-6], cut:[0,0,8], recover:[0,0,4]},
  LeftArm: {idle:[0,0,0], charge:[22,15,40], cut:[18,15,20], recover:[18,10,18]},
  LeftForeArm: {idle:[0,0,0], charge:[30,5,40], cut:[24,0,16], recover:[24,0,20]},
  LeftHand: {idle:[0,0,0], charge:[0,0,0], cut:[0,0,0], recover:[0,0,0]},
  LeftLeg: {idle:[0,0,0], charge:[-12,10,-10], cut:[-18,8,-12], recover:[-10,4,-7]},
  RightLeg: {idle:[0,0,0], charge:[16,-12,10], cut:[22,-10,12], recover:[12,-6,7]},
  Tail: {idle:[0,0,0], charge:[-8,-14,4], cut:[-12,22,-6], recover:[-4,12,-3]},
  LeftSpirit_Tail: {idle:[0,0,0], charge:[-4,-8,3], cut:[-8,14,-4], recover:[-2,6,-2]},
  RightSpirit_Tail: {idle:[0,0,0], charge:[-6,-10,2], cut:[-10,18,-5], recover:[-3,8,-2]},
  LongHair: {idle:[0,0,0], charge:[3,0,0], cut:[-7,12,0], recover:[2,-4,0]},
  BladeLocator: {idle:[0,0,0], charge:[-143.1958,-2.673,46.7041], cut:[27.6382,-47.6496,-53.7835], recover:[-40,-25,0]},
  // Baked from the preview sheath's world orientation into the existing hand locator.
  LeftHandLocator: {idle:[0,0,0], charge:[-111.0531,-53.1019,33.5765],
    cut:[-95.9687,-40.683,69.7035], recover:[-80.5925,-41.8119,45.0601]}
};
const timeline = [[0,'idle'],[.30,'charge'],[1.20,'charge'],[1.32,'charge'],
  [1.40,'cut'],[1.52,'cut'],[1.82,'cut'],[2.10,'recover'],[2.40,'charge'],[2.65,'idle'],[2.80,'idle']];
const animation = {uuid:crypto.randomUUID(), name:'animation.contract_blade.iaido_preview',
  length:2.80, loop:'once', snapping:100, override:false, animators:{}};
function key(channel,time,v) {
  return {uuid:crypto.randomUUID(),channel,time,interpolation:'linear',color:-1,
    data_points:[{x:String(v[0]),y:String(v[1]),z:String(v[2])}]};
}
for (const [name, states] of Object.entries(pose)) {
  if (!ids.has(name)) throw new Error('Missing bone '+name);
  const keyframes=timeline.map(([t,p])=>key('rotation',t,states[p]));
  if(name==='Root') for(const [t,p] of timeline)
    keyframes.push(key('position',t,[0,p==='idle'?0:p==='charge'?-1.4:p==='cut'?-1.8:-1,0]));
  animation.animators[ids.get(name)]={name,type:'bone',rotation_global:false,keyframes};
}
animation.animators[ids.get('FOX')]={name:'FOX',type:'bone',keyframes:[key('scale',0,[0,0,0])]};
for(const g of model.groups) if(['FOX','FoxMoonCrown'].includes(g.name))g.visibility=false;
model.animations.push(animation);
model.name='Black Fox - Iaido study';
fs.writeFileSync(path.join(__dirname,'fox_black_iaido.bbmodel'),JSON.stringify(model));
const tracks={};
for(const b of Object.values(animation.animators)) {
  tracks[b.name]={};
  for(const channel of ['rotation','position','scale']) {
    const keys=b.keyframes.filter(k=>k.channel===channel);
    if(keys.length)tracks[b.name][channel]=Object.fromEntries(keys.map(k=>[String(k.time),
      ['x','y','z'].map(a=>Number(k.data_points[0][a]))]));
  }
}
fs.writeFileSync(path.join(__dirname,'iaido.blockbench-tracks.json'),JSON.stringify({
  note:'Blockbench axes, prototype only; not a game animation export.', length:animation.length, bones:tracks},null,2));
for(const b of Object.values(animation.animators))for(const k of b.keyframes)
  if(k.time>animation.length||!Object.values(k.data_points[0]).every(v=>Number.isFinite(+v)))throw new Error('Invalid frame');
console.log('IAIDO_ASSET_PASS: original optimized model retained, 18 tracks, charge 0.30-1.32s, draw 1.32-1.40s');
