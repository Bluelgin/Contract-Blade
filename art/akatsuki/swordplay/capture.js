(() => {
const fs=require('fs'),s=globalThis.akatsukiSwordPreview,p=Preview.selected;
const size=[p.renderer.domElement.width,p.renderer.domElement.height],aspect=p.camPers.aspect,pos=p.camera.position.clone(),target=p.controls.target.clone(),zoom=p.camera.zoom;
fs.mkdirSync(s.dir+'/native-review',{recursive:true});
const samples=[['idle',0],['draw',.4],['ready',.6],['cut_1',.4],['cut_2',.4],['cut_3',.4],['sheathe',.6]];
for(const [motion,time]of samples)for(const [view,position]of [['front',[0,25,-75]],['angle',[48,25,-62]],['side',[75,25,0]]]){
  s.show(motion,time);p.camera.position.set(...position);p.controls.target.set(0,24,0);p.camera.zoom=.84;p.camPers.aspect=800/1000;p.camera.updateProjectionMatrix();p.controls.update();p.renderer.setSize(800,1000,false);
  Canvas.withoutGizmos(()=>{p.render();fs.writeFileSync(s.dir+'/native-review/'+motion+'-'+view+'.png',Buffer.from(p.canvas.toDataURL().split(',')[1],'base64'));});
}
p.renderer.setSize(...size,false);p.camPers.aspect=aspect;p.camera.position.copy(pos);p.controls.target.copy(target);p.camera.zoom=zoom;p.camera.updateProjectionMatrix();p.controls.update();s.show('ready',.6);
return 'Saved 21 previews using native TLM pose matrices; not AI concept renders.';
})()
