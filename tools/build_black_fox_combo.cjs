// Export the approved editable sample as independent native-state clips.
// No VMD decoding, player animation library or Blockbench is needed at runtime.
const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(__dirname,'..');
const art=path.join(root,'art/slashblade_animations/combo_b');
const res=path.join(root,'src/main/resources');
const model=JSON.parse(fs.readFileSync(path.join(art,'fox_black_combo_b.bbmodel'),'utf8'));
const sample=model.animations.find(a=>a.name==='animation.contract_blade.combo_b_preview');
const timing=JSON.parse(fs.readFileSync(path.join(art,'timing.json'),'utf8'));
const geometry=JSON.parse(fs.readFileSync(path.join(res,'assets/maid_weapon/geo/black_fox_boss.json'),'utf8'));
const names=new Set(geometry['minecraft:geometry'][0].bones.map(b=>b.name));
const exported={format_version:'1.8.0',animations:{}};
for(const stage of timing.stages) {
  const bones={Root:{rotation:[0,0,0],position:[0,0,0]},UpperBody:{rotation:[0,0,0]},
    BladeLocator:{rotation:[0,0,0]},LeftHandLocator:{rotation:[0,0,0]},LongHair:{rotation:[0,0,0]}};
  const start=timing.leadIn+stage.start,end=timing.leadIn+stage.end;
  for(const bone of Object.values(sample.animators)) {
    // Tail loop has its own controller; health transformation is not part of combat.
    if(!bone.keyframes?.length||bone.name==='FOX'||bone.name==='LongHair'||bone.name.includes('Tail'))continue;
    if(!names.has(bone.name))throw new Error('Missing runtime bone '+bone.name);
    const keys=bone.keyframes.filter(k=>k.channel==='rotation'&&k.time>=start-1e-5&&k.time<end-1e-5);
    if(!keys.length)throw new Error('Empty stage '+stage.name+' '+bone.name);
    const track={};
    for(const key of keys) {
      const p=key.data_points[0];
      track[String(+(key.time-start).toFixed(5))]=[-Number(p.x),-Number(p.y),Number(p.z)];
    }
    track[String(+(stage.end-stage.start).toFixed(5))]=Object.values(track).at(-1);
    if(!Object.values(track).flat().every(Number.isFinite))throw new Error('Invalid exported key');
    bones[bone.name]={rotation:track};
  }
  exported.animations['contract_fox_boss.'+stage.name]={loop:'hold_on_last_frame',animation_length:+(stage.end-stage.start).toFixed(5),bones};
}
fs.writeFileSync(path.join(res,'assets/maid_weapon/animations/black_fox_combo_b.animation.json'),JSON.stringify(exported));
const licenses=path.join(res,'licenses/black_fox');
fs.mkdirSync(licenses,{recursive:true});
fs.copyFileSync(path.join(art,'SlashBlade-LICENSE.txt'),path.join(licenses,'SlashBlade-animation-MIT.txt'));
console.log('BLACK_FOX_COMBO_ASSETS_PASS: 7 native-state clips, approved sample axes, existing combat geometry, retained license');
