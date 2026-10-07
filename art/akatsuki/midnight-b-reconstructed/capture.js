(() => {
const fs=require('fs'),dir='E:/Little maid/art/akatsuki/midnight-b-reconstructed',p=Preview.selected,sz=[p.renderer.domElement.width,p.renderer.domElement.height],aspect=p.camPers.aspect,views=[['front',[0,25,-68]],['angle',[46,25,-55]],['side',[66,25,8]],['back',[0,25,70]]];
const previous=p.camera.position.clone(),target=p.controls.target.clone();
function shot(name,pos){const side=name.endsWith('side');p.camera.position.set(...pos);p.controls.target.set(0,24,side?4:0);p.camera.zoom=side?.86:1;p.camPers.aspect=800/1100;p.camera.updateProjectionMatrix();p.controls.update();p.renderer.setSize(800,1100,false);Canvas.withoutGizmos(()=>{p.render();fs.writeFileSync(dir+'/'+name+'.png',Buffer.from(p.canvas.toDataURL().split(',')[1],'base64'));});}
const prefix=globalThis.akatsukiCaptureStage||'preview';for(const [name,pos]of views)shot(prefix+'-'+name,pos);
p.renderer.setSize(...sz,false);p.camPers.aspect=aspect;p.camera.position.copy(previous);p.controls.target.copy(target);p.camera.updateProjectionMatrix();p.controls.update();return 'saved native previews';
})()
