const fs=require('node:fs');
const path=require('node:path');
const clone=v=>structuredClone(v);
const isTail=n=>/^(?:LeftSpirit_|RightSpirit_)?Tail[2-7]?$/.test(n);

function loadTaisho(root) {
  const archive=path.join(root,'art/ysm-test/taisho-flight-source.json');
  if(fs.existsSync(archive))return JSON.parse(fs.readFileSync(archive,'utf8'));
  const dir=path.join(root,'run-ysm-test/config/yes_steve_model/builtin/wine_fox/01_taisho_maid');
  const animations=JSON.parse(fs.readFileSync(path.join(dir,'animations/main.animation.json'),'utf8')).animations;
  const metadata=JSON.parse(fs.readFileSync(path.join(dir,'ysm.json'),'utf8')).metadata;
  if(!animations.fly?.bones?.Tail)throw Error('Missing readable original Taisho flight animation');
  const source={source:'YSM 2.6.5 builtin wine_fox/01_taisho_maid',metadata,fly:animations.fly};
  fs.writeFileSync(archive,JSON.stringify(source,null,2));
  return source;
}

// Bedrock rotation tracks contain additive Euler deltas; preserve unwrapped turns.
function offsetVector(value,offset) {
  if(Array.isArray(value))return value.map((x,i)=>typeof x==='number'?x+offset[i]:offset[i]===0?x:`(${x})+(${offset[i]})`);
  if(value && typeof value==='object')return Object.fromEntries(Object.entries(value).map(([k,v])=>[k,k==='lerp_mode'?v:offsetVector(v,offset)]));
  return value;
}

function applyTails(animations,bones,source,retarget) {
  const names=new Set(bones.map(b=>b.name));
  const byName=new Map(bones.map(b=>[b.name,b]));
  const chains=['','LeftSpirit_','RightSpirit_'].filter(p=>names.has(p+'Tail'));
  animations.fly=retarget(source.fly);
  for(let index=1;index<chains.length;index++) {
    const prefix=chains[index], base=byName.get('Tail').rotation??[0,0,0], rest=byName.get(prefix+'Tail').rotation??[0,0,0];
    for(let segment=1;segment<=7;segment++) {
      const original=segment===1?'Tail':'Tail'+segment, target=prefix+original;
      if(!names.has(target))continue;
      const t=clone(source.fly.bones[original]??{rotation:[0,0,0]});
      if(segment===1)t.rotation=offsetVector(t.rotation,base.map((x,i)=>x-rest[i]+(i===2?index*120:0)));
      animations.fly.bones[target]=t;
    }
  }
  // Do not import the hidden fox-form rotor or its sound/controller dependencies.
  delete animations.fly.bones.FOX;delete animations.fly.bones.wb;
  if(chains.length===1)return {taishoFlight:true,chains:1,spinDegreesPerLoop:1080,loopSeconds:source.fly.animation_length};

  // Remove old tiny tail curves only. Blinking, hair, face and ear motions stay intact.
  for(const [key,c] of Object.entries(animations)) if(/^(?:pre_)?parallel\d$/.test(key)) {
    for(const name of Object.keys(c.bones??{}))if(isTail(name))delete c.bones[name];
  }
  const tailClip=animations.pre_parallel3;
  if(Object.keys(tailClip?.bones??{}).length)throw Error('Reserved pre_parallel3 slot is no longer empty');
  const move='(v.fox_tail_move??0)', run='(v.fox_tail_run??0)';
  // Existing, non-rendered helper bone; cache the two locomotion blends once per frame.
  // Root is the actual body bounce from the playing main clip, sampled one frame late.
  const bob="math.clamp(-ysm.bone_pos('Root').y*1.5,-3,3)";
  const physics={loop:true,bones:{molang2:{rotation:[
    "v.fox_tail_move=math.clamp(ysm.second_order('fox_tail_move_blend',(ctrl.walk||ctrl.run)?math.clamp(query.ground_speed/3.2,0,1):0,2.4,0.85,0),0,1);",
    "v.fox_tail_run=math.clamp(ysm.second_order('fox_tail_run_blend',ctrl.run?1:0,2.2,0.85,0),0,1);",0
  ]}}};
  for(let index=0;index<chains.length;index++) {
    const prefix=chains[index], phase=[0,68,-76][index];
    const key=['center','left','right'][index];
    for(let segment=1;segment<=7;segment++) {
      const name=prefix+(segment===1?'Tail':'Tail'+segment);
      if(!names.has(name))continue;
      const delay=(segment-1)*17;
      // Keep the approved idle wave; locomotion gradually replaces it with body-driven lag.
      const wave=`query.anim_time*120+(${phase-delay})`;
      const frequency=Number(([3.6,3.4,3.1,2.8,2.5,2.2,2.0][segment-1]+[0,-0.14,0.14][index]).toFixed(2));
      const damping=segment===1?0.85:Number((0.72+[0,0.02,-0.02][index]).toFixed(2));
      const drag=segment===1?`-2.0*${move}-5.0*${run}`:`-0.25*${move}`;
      const bounce=((segment===1?0.55:0.55+segment*0.11)*[1,0.94,1.03][index]).toFixed(2);
      const vertical=(segment===1?0.35:0.1).toFixed(2);
      const pitchInput=`${drag}+${bob}*${move}*${bounce}+math.clamp(-query.vertical_speed*${vertical},-4,4)`;
      const springPitch=`ysm.second_order('fox_tail_${key}_pitch_${segment}',${pitchInput},${frequency},${damping},0)`;
      if(segment===1) {
        // Side roots converge toward the center only in locomotion; bind rotations stay intact.
        const inward=[0,-1,1][index];
        const idleYaw=index===0?3.0:4.5;
        physics.bones[name]={rotation:[
          `math.sin(${wave}+25)*(2.0-1.5*${move})+${springPitch}`,
          `math.sin(${wave})*(8.5-(8.5-${idleYaw})*${move})+(${inward})*(3.0*${move}+7.0*${run})+ysm.second_order('fox_tail_${key}_turn_1',math.clamp(-query.yaw_speed*0.025,-8,8),3.0,0.8,0)`,
          `math.sin(${wave}-65)*(4.5-3.0*${move})`
        ]};
      } else {
        const idleYaw=segment===2?3.2:Math.min(4.6,2.2+segment*0.35);
        const movingYaw=0.65+segment*0.12;
        const idlePitch=segment===2?1.6:0.7;
        // A second, slower turn response at the middle and tip gives a trailing arc.
        const turn=(segment===3||segment===6)?`+ysm.second_order('fox_tail_${key}_turn_${segment}',math.clamp(-query.yaw_speed*0.007,-2.2,2.2),${segment===3?2.3:1.8},0.72,0)`:'';
        physics.bones[name]={rotation:[
          `math.sin(${wave}+20)*(${idlePitch}-${(idlePitch-0.25).toFixed(2)}*${move})+${springPitch}`,
          `math.sin(${wave})*(${idleYaw.toFixed(2)}-${(idleYaw-movingYaw).toFixed(2)}*${move})+math.sin(query.anim_time*240+(${phase-delay-40}))*0.5*(1-0.6*${move})${turn}`,
          `math.sin(${wave}-65)*${(1.0+segment*0.16).toFixed(2)}*(1-0.5*${move})`
        ]};
      }
    }
  }
  // This is a low-priority layer: fly, prone/swim and other explicit poses can override it.
  animations.pre_parallel3=physics;
  return {taishoFlight:true,chains:chains.length,spinDegreesPerLoop:1080,loopSeconds:source.fly.animation_length,tailPhysicsSlot:'pre_parallel3',tailTracks:Object.keys(physics.bones).filter(isTail).length,secondOrderChannels:32,bodyBounceSource:'previous-frame Root translation',locomotionBlend:true,rootToTipFrequency:[3.6,3.4,3.1,2.8,2.5,2.2,2.0],chainFrequencyOffsets:[0,-0.14,0.14],restPoseModified:false};
}

module.exports={loadTaisho,applyTails};
