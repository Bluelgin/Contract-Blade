const fs=require('node:fs'), path=require('node:path');
const root=path.resolve(__dirname,'..');
const model=JSON.parse(fs.readFileSync(path.join(root,'art/slashblade_animations/iaido/fox_black_iaido.bbmodel'),'utf8'));
const sample=model.animations.find(a=>a.name==='animation.contract_blade.iaido_preview');
const geometry=JSON.parse(fs.readFileSync(path.join(root,'src/main/resources/assets/maid_weapon/geo/black_fox_boss.json'),'utf8'));
const names=new Set(geometry['minecraft:geometry'][0].bones.map(b=>b.name));
const bones={AllBody:{rotation:[0,0,0]}};
for(const b of Object.values(sample.animators)) {
  if(b.name==='FOX'||b.name.includes('Tail'))continue; // Existing tail controller remains sole owner.
  if(!names.has(b.name))throw new Error('Missing runtime bone '+b.name);
  const channels={};
  for(const channel of ['rotation','position']) {
    const keys=b.keyframes.filter(k=>k.channel===channel);
    if(keys.length)channels[channel]=Object.fromEntries(keys.map(k=>{
      const p=k.data_points[0],v=[Number(p.x),Number(p.y),Number(p.z)];
      if(!v.every(Number.isFinite))throw new Error('Invalid key');
      return [String(k.time),channel==='rotation'?[-v[0],-v[1],v[2]]:[-v[0],v[1],v[2]]];
    }));
  }
  bones[b.name]=channels;
}
const result={format_version:'1.8.0',animations:{'contract_fox_boss.iaido':{
  loop:'hold_on_last_frame',animation_length:sample.length,bones}}};
fs.writeFileSync(path.join(root,'src/main/resources/assets/maid_weapon/animations/black_fox_iaido.animation.json'),JSON.stringify(result));
console.log('BLACK_FOX_IAIDO_ASSETS_PASS: approved clip, existing skeleton, tails remain independent');
