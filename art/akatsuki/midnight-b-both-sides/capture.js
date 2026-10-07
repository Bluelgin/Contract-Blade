(() => {
const fs=require('fs'),dir='E:/Little maid/art/akatsuki/midnight-b-both-sides',p=Preview.selected,sz=[p.renderer.domElement.width,p.renderer.domElement.height],aspect=p.camPers.aspect,previous=p.camera.position.clone(),target=p.controls.target.clone(),zoom=p.camera.zoom;
const views=[['front',[0,25,-68],[0,24,0],1],['angle',[46,25,-55],[0,24,0],1],['other-angle',[-46,25,-55],[0,24,0],1],['side',[66,25,8],[0,24,4],.86],['other-side',[-66,25,8],[0,24,4],.86],['other-side-detail',[-55,19,2],[0,15,0],1.3],['side-detail',[55,19,2],[0,15,0],1.3]];
for(const [name,pos,t,z]of views){p.camera.position.set(...pos);p.controls.target.set(...t);p.camera.zoom=z;p.camPers.aspect=800/1100;p.camera.updateProjectionMatrix();p.controls.update();p.renderer.setSize(800,1100,false);Canvas.withoutGizmos(()=>{p.render();fs.writeFileSync(dir+'/preview-'+name+'.png',Buffer.from(p.canvas.toDataURL().split(',')[1],'base64'));});}
p.renderer.setSize(...sz,false);p.camPers.aspect=aspect;p.camera.position.copy(previous);p.controls.target.copy(target);p.camera.zoom=zoom;p.camera.updateProjectionMatrix();p.controls.update();return 'saved native previews';
})()
