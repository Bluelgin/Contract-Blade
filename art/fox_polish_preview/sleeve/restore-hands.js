(() => {
 const original=JSON.parse(require('fs').readFileSync('E:/Blade tetra/art/akatsuki-winefox/source/akatsuki-1.2.3.bbmodel','utf8'));
 const ids=['471fd222-f3c9-6f2f-d2cd-7a2c8fa7e99d','78183cc0-5413-05a7-9644-5cabed039756'];
 const tex=Texture.all[0],canvas=document.createElement('canvas');canvas.width=canvas.height=1024;
 const ctx=canvas.getContext('2d');ctx.imageSmoothingEnabled=false;ctx.drawImage(tex.img,0,0);
 if(!globalThis.taishoHandTexture){globalThis.taishoHandTexture=new Image();taishoHandTexture.src=original.textures[0].source;}
 const oldImage=taishoHandTexture;
 if(!oldImage.complete)throw new Error('Original texture still loading; retry restore-hands.js');
 let x=4,y=516,count=0;
 for(const id of ids){
  const e=original.elements.find(e=>e.uuid===id),c=Cube.all.find(c=>c.uuid===id);
  if(!e||!c)throw new Error('Missing original visible hand '+id);
  for(const [side,f] of Object.entries(e.faces)){
   const uv=f.uv.slice(),sx=Math.min(uv[0],uv[2]),sy=Math.min(uv[1],uv[3]),w=Math.abs(uv[2]-uv[0]),h=Math.abs(uv[3]-uv[1]);
   ctx.drawImage(oldImage,sx,sy,w,h,x,y,w,h);
   c.faces[side].uv=[uv[0]-sx+x,uv[1]-sy+y,uv[2]-sx+x,uv[3]-sy+y];
   c.faces[side].texture=tex.uuid;x+=w+4;count++;
  }
  c.box_uv=false;
 }
 tex.fromDataURL(canvas.toDataURL('image/png'));tex.saved=false;Project.saved=false;Canvas.updateAll();
 return JSON.stringify({restoredVisibleHands:2,faces:count,source:'original Akatsuki Taisho Wine Fox',geometryChanged:false});
})()
