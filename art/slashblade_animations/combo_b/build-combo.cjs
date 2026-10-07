const fs=require('node:fs');
const path=require('node:path');
const crypto=require('node:crypto');
const out=__dirname;
const source='E:/刃录/art/characters/black_fox/fox_black.bbmodel';
const motion=path.resolve(out,'../../../tmp/external/SlashBlade_2/src/main/resources/assets/slashblade/model/pa/player_motion.vmd');
const data=fs.readFileSync(motion), decoder=new TextDecoder('shift-jis');
const tracks=new Map();
for(let i=0,n=data.readUInt32LE(50);i<n;i++) {
  const p=54+i*111;
  const name=decoder.decode(data.subarray(p,p+15)).replace(/\0.*$/,'');
  const record={frame:data.readUInt32LE(p+15),q:Array.from({length:4},(_,k)=>data.readFloatLE(p+31+4*k)),curve:[3,7,11,15].map(k=>data[p+47+k]/127)};
  if(!tracks.has(name)) tracks.set(name,[]);
  tracks.get(name).push(record);
}
for(const keys of tracks.values()) keys.sort((a,b)=>a.frame-b.frame);
function bezier(t,[x1,y1,x2,y2]) {
  const value=(u,a,b)=>3*(1-u)*(1-u)*u*a+3*(1-u)*u*u*b+u*u*u;
  let lo=0,hi=1;
  for(let i=0;i<16;i++){const mid=(lo+hi)/2;if(value(mid,x1,x2)<t)lo=mid;else hi=mid;}
  return value((lo+hi)/2,y1,y2);
}
function slerp(a,b,t) {
  let dot=a.reduce((s,v,i)=>s+v*b[i],0);
  if(dot<0){b=b.map(v=>-v);dot=-dot;}
  if(dot>0.9995){const q=a.map((v,i)=>v+(b[i]-v)*t),n=Math.hypot(...q);return q.map(v=>v/n);}
  const angle=Math.acos(Math.max(-1,Math.min(1,dot))),s=Math.sin(angle);
  return a.map((v,i)=>(Math.sin((1-t)*angle)*v+Math.sin(t*angle)*b[i])/s);
}
function sample(name,frame) {
  const keys=tracks.get(name);
  const b=keys.find(k=>k.frame>=frame)||keys.at(-1);
  const a=[...keys].reverse().find(k=>k.frame<=frame)||keys[0];
  return slerp(a.q,b.q,a.frame===b.frame?0:bezier((frame-a.frame)/(b.frame-a.frame),b.curve));
}
function euler([x,y,z,w]) {
  const m11=1-2*(y*y+z*z),m21=2*(x*y+z*w),m31=2*(x*z-y*w),m32=2*(y*z+x*w),m33=1-2*(x*x+y*y);
  return [Math.atan2(m32,m33),Math.asin(Math.max(-1,Math.min(1,-m31))),Math.atan2(m21,m11)].map(v=>v*180/Math.PI);
}
function yaw([x,y,z,w]) {return Math.atan2(2*(w*y+x*z),1-2*(x*x+y*y))*180/Math.PI;}
function unwrap(values,last) {return values.map((v,i)=>{if(last)while(Math.abs(v-last[i])>180)v+=v>last[i]?-360:360;return v;});}
const model=JSON.parse(fs.readFileSync(source,'utf8'));
const ids=new Map(model.groups.map(g=>[g.name,g.uuid]));
const mapping={'AllBody':'body','Head':'head','RightArm':'right arm','LeftArm':'left arm'};
const stages=[{name:'combo_b1',start:0,end:.4,frame:700},...Array.from({length:5},(_,i)=>({name:'combo_b'+(i+2),start:.4+i*.2,end:.6+i*.2,frame:710})),{name:'combo_b7',start:1.4,end:2.5,frame:710}];
function sourceFrame(t,half) {
  const length=half?.8:2.5;
  if(t<.3)return 700;
  if(t>=.3+length)return null;
  const age=t-.3;
  const stage=stages.find(s=>age<s.end-1e-6)||stages.at(-1);
  return stage.frame+(age-stage.start)*30;
}
function build(name,half) {
  const length=half?1.6:3.3;
  const animation={uuid:crypto.randomUUID(),name:'animation.contract_blade.'+name,length,loop:'once',snapping:60,override:false,animators:{}};
  const bones=[...Object.keys(mapping),'RightForeArm','LeftForeArm','RightHand','LeftHand','LeftLeg','RightLeg','Tail','LeftSpirit_Tail','RightSpirit_Tail','LongHair','FOX'];
  for(const bone of bones) {
    if(!ids.has(bone))throw new Error('Missing bone '+bone);
    const animator={name:bone,type:'bone',rotation_global:false,quaternion_interpolation:false,keyframes:[]};
    let previous;
    for(let tick=0;tick<=Math.round(length*60);tick++) {
      const t=tick/60,frame=sourceFrame(t,half);
      const fade=frame===null?Math.min(1,(t-(half?1.1:2.8))/.4):0;
      const f=frame??(half?716:743);
      let v=[0,0,0];
      if(mapping[bone])v=euler(sample(mapping[bone],f));
      // Fox has long sleeves and a large tail: keep native arm rhythm, reduce body twist.
      const bodyTurn=22*Math.sin(yaw(sample('body',f))*Math.PI/180);
      if(bone==='AllBody')v=[2,bodyTurn,0];
      if(bone==='Head')v=[-2,-bodyTurn,0];
      if(bone==='RightArm')v[2]-=18;
      if(bone==='LeftArm')v[2]+=18;
      if(bone==='RightForeArm')v=[10,0,0];
      if(bone==='LeftForeArm')v=[8,0,0];
      if(bone==='LeftLeg')v=[-5,0,-3];
      if(bone==='RightLeg')v=[5,0,3];
      if(bone==='Tail'||bone.endsWith('Spirit_Tail'))v=[1.5*Math.sin(t*7),3*Math.sin((t-.06)*9),0];
      if(bone==='LongHair')v=[Math.sin(t*8),1.5*Math.sin((t-.04)*9),0];
      if(frame===null&&mapping[bone])v=v.map(x=>x*(1-fade));
      if(bone==='FOX')v=[0,0,0];
      v=unwrap(v,previous); previous=v;
      animator.keyframes.push({uuid:crypto.randomUUID(),channel:bone==='FOX'?'scale':'rotation',time:+t.toFixed(5),interpolation:'linear',color:-1,data_points:[Object.fromEntries(['x','y','z'].map((axis,i)=>[axis,String(+v[i].toFixed(4))]))]});
    }
    animation.animators[ids.get(bone)]=animator;
  }
  return animation;
}
const full=build('combo_b_preview',false),half=build('combo_b_half_preview',true);
model.animations.push(full,half);
for(const group of model.groups)if(['FOX','FoxMoonCrown'].includes(group.name))group.visibility=false;
fs.writeFileSync(path.join(out,'fox_black_combo_b.bbmodel'),JSON.stringify(model));
fs.writeFileSync(path.join(out,'timing.json'),JSON.stringify({source:motion,sha256:crypto.createHash('sha256').update(data).digest('hex'),note:'Native VMD arm rhythm retarget; body/head adapted. Visual prototype, not combat implementation.',sourceFps:30,samplingFps:60,leadIn:.3,stages,halfCutoff:.8,fullDuration:full.length,halfDuration:half.length},null,2));
for(const a of [full,half])for(const [id,bone] of Object.entries(a.animators)) {
  if(!model.groups.some(g=>g.uuid===id))throw new Error('Unmapped bone');
  for(const k of bone.keyframes) {
    if(k.time<0||k.time>a.length+1e-5)throw new Error('Key outside clip');
    if(!Object.values(k.data_points[0]).every(v=>Number.isFinite(+v)))throw new Error('Invalid key');
  }
}
fs.copyFileSync(path.resolve(out,'../../../tmp/external/SlashBlade_2/LICENSE'),path.join(out,'SlashBlade-LICENSE.txt'));
console.log('Created native-VMD-based B full/half previews, original geometry and animations retained');
