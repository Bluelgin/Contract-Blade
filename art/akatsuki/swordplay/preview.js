(() => {
const fs=require('fs'),root='E:/Little maid',dir=root+'/art/akatsuki/swordplay';
const base=JSON.parse(fs.readFileSync(root+'/art/akatsuki/midnight-b-optimized/akatsuki-b-optimized.bbmodel','utf8'));
Codecs.project.load(base,{path:dir+'/akatsuki-swordplay-preview.bbmodel',no_file:true});Modes.options.edit.select();Project.name='赤月 · 专属佩刀动作预览';Project.save_path=dir+'/akatsuki-swordplay-preview.bbmodel';
const rows=JSON.parse(fs.readFileSync(dir+'/native-poses.json','utf8')),parts=JSON.parse(fs.readFileSync(root+'/src/main/resources/assets/maid_weapon/akatsuki/katana.json','utf8')).parts;
for(const c of Cube.all){let parent=c.parent;while(parent&&parent!=='root'){if(parent.name==='FOX'){c.visibility=false;break;}parent=parent.parent;}}
Canvas.updateAll();Canvas.scene.updateMatrixWorld(true);unselectAll();
const baseline=new Map(Cube.all.map(c=>[c.uuid,{world:c.mesh.matrixWorld.clone(),parentInverse:c.mesh.parent.matrixWorld.clone().invert(),bone:c.parent.name}]));
const swordScene=new THREE.Group();swordScene.name='Akatsuki native katana preview';Canvas.scene.add(swordScene);
const groups={};for(const [part,boxes]of Object.entries(parts)){
  const group=new THREE.Group();group.matrixAutoUpdate=false;groups[part]=group;swordScene.add(group);
  for(const box of boxes){const size=box.to.map((x,i)=>x-box.from[i]),center=box.to.map((x,i)=>(x+box.from[i])/2);
    const mesh=new THREE.Mesh(new THREE.BoxGeometry(...size),new THREE.MeshLambertMaterial({color:'#'+box.color,side:THREE.DoubleSide}));mesh.position.set(...center);group.add(mesh);}
}
const mirror=new THREE.Matrix4(),pixels=values=>{const a=values.slice();a[12]*=16;a[13]*=16;a[14]*=16;return new THREE.Matrix4().fromArray(a);};
function show(motion,fraction){
  const row=rows.find(r=>r.motion===motion&&Math.abs(r.fraction-fraction)<.001);if(!row)throw new Error('Missing native pose');
  for(const c of Cube.all){const b=baseline.get(c.uuid),d=row.boneDeltas[b.bone];if(!d)continue;
    const delta=mirror.clone().multiply(pixels(d)).multiply(mirror);c.mesh.matrixAutoUpdate=false;c.mesh.matrix.copy(b.parentInverse).multiply(delta).multiply(b.world);}
  for(const part of ['blade','sheath'])groups[part].matrix.copy(mirror).multiply(pixels(row[part+'Matrix']));
  Canvas.scene.updateMatrixWorld(true);Preview.selected.render();globalThis.akatsukiSwordPreview.current={motion,fraction};return {motion,fraction};
}
globalThis.akatsukiSwordPreview={dir,show,swordScene,baseline,rows};
const p=Preview.selected;p.camera.position.set(46,25,-55);p.controls.target.set(0,24,0);p.camera.zoom=.86;p.camera.updateProjectionMatrix();p.controls.update();
return show('ready',.6);
})()
