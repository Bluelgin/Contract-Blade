(() => {
const fs=require('fs'),out='E:/Little maid/art/akatsuki/polish-v5',source='E:/Little maid/art/akatsuki/polish-v4/akatsuki-clothing-v4.bbmodel';fs.mkdirSync(out,{recursive:true});Codecs.project.load(JSON.parse(fs.readFileSync(source,'utf8')),{path:source,no_file:true});Modes.options.edit.select();
const plate=Cube.all.find(c=>c.name==='AkatsukiHairOrnament');if(!plate)throw Error('Hair ornament missing');
const canvas=document.createElement('canvas');canvas.width=canvas.height=64;const ctx=canvas.getContext('2d'),data=ctx.createImageData(64,64);
const clamp=n=>Math.max(0,Math.min(255,Math.round(n)));
for(let y=0;y<64;y++)for(let x=0;x<64;x++){
const u=(x+.5)/64,v=(y+.5)/64,r=Math.hypot(u-.48,v-.49),cut=Math.hypot(u-.60,v-.47),outer=.445,inner=.38;
const body=r<outer&&cut>inner,edge=Math.min(Math.abs(r-outer),Math.abs(cut-inner));let rgba=[0,0,0,0];
if(body){const bright=edge<.018;rgba=bright?[247,216,137,240]:[205+Math.round((1-v)*13),147+Math.round((1-v)*17),69,220];}
else if(r<outer+.047&&cut>inner-.037){const distance=r>outer?r-outer:inner-cut;const alpha=clamp(65*(1-distance/.047));rgba=[173,59,53,alpha];}
for(const [sx,sy,size] of [[.09,.69,.012],[.24,.94,.01],[.86,.19,.012],[.37,.04,.009]])if(Math.abs(u-sx)+Math.abs(v-sy)<size)rgba=[225,182,96,190];
data.data.set(rgba,(y*64+x)*4);
}
ctx.putImageData(data,0,0);const url=canvas.toDataURL('image/png'),fx=new Texture({name:'akatsuki-moon-aura.png',width:64,height:64,uv_width:64,uv_height:64}).fromDataURL(url).add(false);
plate.from=[-11.5,25.5,9];plate.to=[11.5,48.5,9.06];plate.origin=[0,37,9.03];plate.rotation=[0,0,0];plate.box_uv=false;plate.autouv=0;
for(const [side,face]of Object.entries(plate.faces)){face.texture=['north','south'].includes(side)?fx.uuid:null;face.uv=side==='south'?[64,0,0,64]:[0,0,64,64];}
Project.name='赤月 · 背月预览 v5';Canvas.updateAll();
fs.writeFileSync(out+'/akatsuki-moon-aura.png',Buffer.from(url.split(',')[1],'base64'));fs.writeFileSync(out+'/akatsuki-back-moon-v5.bbmodel',Codecs.project.compile());
const report={source,replacedCube:plate.uuid,cubes:Cube.all.length,groups:Group.all.length,baseTexture:[256,256],effectTexture:[64,64],bonesAndAnimationsChanged:false,stage:'static editable preview; no runtime emissive renderer',clothing:'v4 retained pending clarification'};fs.writeFileSync(out+'/validation.json',JSON.stringify(report,null,2));
const names=c=>{const a=[];for(let p=c.parent;p&&p.name;p=p.parent)a.push(p.name);return a;};for(const c of Cube.all)if(names(c).includes('FOX'))c.visibility=false;Canvas.updateVisibility();const p=Preview.selected;p.controls.autoRotate=false;p.camera.position.set(0,25,-90);p.controls.target.set(0,24,0);p.camera.zoom=1;p.camera.updateProjectionMatrix();p.controls.update();Project.saved=false;return JSON.stringify(report);
})()
