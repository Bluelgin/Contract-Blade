(() => {
const fs=require('fs'),out='E:/Little maid/art/akatsuki/polish-v2';
fs.mkdirSync(out,{recursive:true});
const sourcePath='E:/Little maid/art/akatsuki/polish-v1/akatsuki-clothing-polish-v1.bbmodel';
Codecs.project.load(JSON.parse(fs.readFileSync(sourcePath,'utf8')),{path:sourcePath,no_file:true});
Modes.options.edit.select();Project.name='赤月 · 连贯衣片 v2';
const tex=Texture.all[0],canvas=document.createElement('canvas');canvas.width=canvas.height=256;
const ctx=canvas.getContext('2d');ctx.imageSmoothingEnabled=false;ctx.drawImage(tex.img,0,0);
const old=ctx.getImageData(0,0,256,256).data,used=new Uint8Array(65536);
const names=c=>{const a=[];for(let p=c.parent;p&&p.name;p=p.parent)a.push(p.name);return a;};
const hands=new Set(['471fd222-f3c9-6f2f-d2cd-7a2c8fa7e99d','78183cc0-5413-05a7-9644-5cabed039756']);
const targets=Cube.all.filter(c=>!hands.has(c.uuid)&&!names(c).includes('FOX')&&(['UpperBody','LeftArm','RightArm','LeftForeArm','RightForeArm'].includes(c.parent?.name)||names(c).includes('clothe')));
for(const c of Cube.all)for(const f of Object.values(c.faces)){if(f.texture===null)continue;const uv=f.uv;for(let y=Math.max(0,Math.floor(Math.min(uv[1],uv[3]))-1);y<Math.min(256,Math.ceil(Math.max(uv[1],uv[3]))+1);y++)for(let x=Math.max(0,Math.floor(Math.min(uv[0],uv[2]))-1);x<Math.min(256,Math.ceil(Math.max(uv[0],uv[2]))+1);x++)used[y*256+x]=1;}
const originalUsed=used.slice();
function allocate(w,h){for(let y=1;y+h+1<256;y++)for(let x=1;x+w+1<256;x++){let free=true;for(let py=y-1;py<=y+h&&free;py++)for(let px=x-1;px<=x+w;px++)if(used[py*256+px]){free=false;break;}if(!free)continue;for(let py=y-1;py<=y+h;py++)for(let px=x-1;px<=x+w;px++)used[py*256+px]=1;return[x,y];}throw Error('No unused atlas space for '+w+'x'+h);}
const clamp=v=>Math.max(0,Math.min(255,Math.round(v)));
const tasks=[];
for(const c of targets)for(const [side,f]of Object.entries(c.faces)){const uv=f.uv.slice();if(f.texture===null||uv[0]===uv[2]||uv[1]===uv[3])continue;tasks.push({c,side,f,uv,w:Math.max(2,Math.ceil(Math.abs(uv[2]-uv[0])*2)),h:Math.max(2,Math.ceil(Math.abs(uv[3]-uv[1])*2))});}
tasks.sort((a,b)=>b.w*b.h-a.w*a.h);
let newPixels=0;
for(const task of tasks){const {c,side,f,uv,w,h}=task,dst=allocate(w,h),patch=ctx.createImageData(w,h),parents=names(c),skirt=parents.includes('clothe'),fore=c.parent?.name.includes('ForeArm'),upper=c.parent?.name==='UpperBody';
const center=skirt&&parents.some(n=>/^F(F?M|L|R)/.test(n)),inner=skirt&&parents.some(n=>/^FF?M/.test(n));
for(let y=0;y<h;y++)for(let x=0;x<w;x++){
const u=(x+.5)/w,v=(y+.5)/h,sx=Math.max(0,Math.min(255,Math.floor(uv[0]+(uv[2]-uv[0])*u))),sy=Math.max(0,Math.min(255,Math.floor(uv[1]+(uv[3]-uv[1])*v))),i=(sy*256+sx)*4;
const r=old[i],g=old[i+1],b=old[i+2],a=old[i+3];
const gold=r>g*1.13&&g>b*1.2&&g>65,ivory=r>160&&g>145&&b>135&&Math.max(r,g,b)-Math.min(r,g,b)<55;
const wy=side==='up'?c.to[1]:side==='down'?c.from[1]:c.to[1]-(c.to[1]-c.from[1])*v;
const axis=['east','west'].includes(side)?2:0,along=c.from[axis]+(c.to[axis]-c.from[axis])*u;
const fold=Math.round((Math.cos(along*1.4)*3+Math.cos(along*.68)*2)/2)*2;
const edge=Math.min(u,1-u),seam=edge<.08?-5:0;
let rgb=[r,g,b];
if(!gold&&a){
if(ivory){const light=fold*.6+seam+(v<.14?4:0);rgb=[233+light,224+light,206+light];}
else if(skirt){const base=inner?[112,27,40]:[30,24,29],light=fold+seam+(wy<13?-2:0);rgb=base.map((n,k)=>n+light*(k===0?1:.75));if(!inner&&edge<.07)rgb=[71+fold,22,33];}
else if(fore){const cuff=Math.max(0,Math.min(1,(23.5-wy)/6)),light=fold+seam;rgb=[119-cuff*29+light,29-cuff*10+light*.45,42-cuff*7+light*.65];if(side==='down')rgb=[35+fold,23,29];}
else if(r>g*1.55&&r>b*1.08){const light=fold+seam;rgb=[116+light,28+light*.45,41+light*.65];}
else if(r<80&&g<70&&b<95){const light=fold*.65+seam;rgb=[29+light,23+light,29+light];}
}
patch.data.set([...rgb.map(clamp),a],(y*w+x)*4);
}
ctx.putImageData(patch,dst[0],dst[1]);
ctx.drawImage(canvas,dst[0],dst[1],w,1,dst[0],dst[1]-1,w,1);ctx.drawImage(canvas,dst[0],dst[1]+h-1,w,1,dst[0],dst[1]+h,w,1);ctx.drawImage(canvas,dst[0],dst[1]-1,1,h+2,dst[0]-1,dst[1]-1,1,h+2);ctx.drawImage(canvas,dst[0]+w-1,dst[1]-1,1,h+2,dst[0]+w,dst[1]-1,1,h+2);
f.uv=[dst[0],dst[1],dst[0]+w,dst[1]+h];c.box_uv=false;newPixels+=w*h;
}
const updated=ctx.getImageData(0,0,256,256).data;
for(let p=0;p<65536;p++)if(originalUsed[p])for(let k=0;k<4;k++)if(updated[p*4+k]!==old[p*4+k])throw Error('Shared source texel changed');
const data=canvas.toDataURL('image/png');tex.fromDataURL(data);tex.name='akatsuki-clothing-v2.png';Canvas.updateAll();
fs.writeFileSync(out+'/akatsuki-clothing-v2.png',Buffer.from(data.split(',')[1],'base64'));
fs.writeFileSync(out+'/akatsuki-clothing-v2.bbmodel',Codecs.project.compile());
const report={source:sourcePath,targets:targets.map(c=>c.uuid),faces:tasks.length,newPixels,resolution:[256,256],originalReferencedTexelsUnchanged:true,geometryChanged:false,bonesChanged:false,design:'wine sleeves, dark outer robe, wine inner skirt; preserved bracelets, mask, skin and gold embroidery'};
fs.writeFileSync(out+'/validation.json',JSON.stringify(report,null,2));
for(const c of Cube.all)if(names(c).includes('FOX'))c.visibility=false;
Canvas.updateVisibility();const p=Preview.selected;p.controls.autoRotate=false;p.camera.position.set(0,25,-84);p.controls.target.set(0,21,0);p.camera.zoom=1;p.camera.updateProjectionMatrix();p.controls.update();Project.saved=false;
return JSON.stringify({...report,targets:targets.length});
})()
