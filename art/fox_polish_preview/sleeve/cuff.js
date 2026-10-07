(() => {
 const tex=Texture.all[0],white=Project.name.includes('白狐');
 const canvas=document.createElement('canvas');canvas.width=canvas.height=1024;
 const ctx=canvas.getContext('2d');ctx.imageSmoothingEnabled=false;ctx.drawImage(tex.img,0,0);
 const source=ctx.getImageData(0,0,1024,1024),pixels=source.data;
 const hands=['471fd222-f3c9-6f2f-d2cd-7a2c8fa7e99d','78183cc0-5413-05a7-9644-5cabed039756'];
 let removed=0;
 for(const id of hands){
  const c=Cube.all.find(c=>c.uuid===id);if(!c)throw new Error('Missing visible hand');
  for(const f of Object.values(c.faces)){
   const uv=f.uv,x1=Math.min(uv[0],uv[2]),x2=Math.max(uv[0],uv[2]),y1=Math.min(uv[1],uv[3]),y2=Math.max(uv[1],uv[3]);
   for(let y=y1;y<y2;y++)for(let x=x1;x<x2;x++){
    const i=(y*1024+x)*4,r=pixels[i],g=pixels[i+1],b=pixels[i+2];
    if(!pixels[i+3]||r-g<=10||g-b<=25)continue;
    const samples=[];
    for(const yy of [y-1,y+1])if(yy>=y1&&yy<y2){const j=(yy*1024+x)*4;if(pixels[j+3])samples.push(Array.from(pixels.slice(j,j+3)));}
    if(!samples.length)throw new Error('No neighboring skin for bracelet removal');
    for(let k=0;k<3;k++)source.data[i+k]=Math.round(samples.reduce((sum,v)=>sum+v[k],0)/samples.length);
    removed++;
   }
  }
 }
 ctx.putImageData(source,0,0);
 const shellIds=new Set(['0e500371-a4a8-5497-f76d-620dd05db78c','b4a74a17-7070-e203-17f6-c9adf4fc904d','4733fc27-fe09-3ded-5a92-3853d85fa950','22240246-e2d0-ea29-41f7-26cebadbd145']);
 const cuffs=Cube.all.filter(c=>shellIds.has(c.uuid)||/^LayerCuff/.test(c.name));
 let x=514,y=650,row=0;
 function allocate(w,h){if(x+w+2>1022){x=514;y+=row;row=0;}if(y+h+2>1022)throw new Error('Cuff atlas exhausted');const a=[x+1,y+1];x+=w+2;row=Math.max(row,h+2);return a;}
 const base=white?[218,207,183]:[91,71,106],light=white?[245,237,217]:[131,112,146];
 const lining=white?[145,143,126]:[46,35,60],thread=white?[198,186,157]:[137,114,151];
 let faces=0;
 for(const c of cuffs){
  for(const [side,f] of Object.entries(c.faces)){
   if(f.texture===null)continue;
   const w=48,h=24,dst=allocate(w,h),patch=ctx.createImageData(w,h);
   const under=/UnderFold/.test(c.name),lip=/TurnedLip/.test(c.name),seam=/GoldSeam/.test(c.name);
   for(let yy=0;yy<h;yy++)for(let xx=0;xx<w;xx++){
    const u=(xx+.5)/w,v=(yy+.5)/h;
    const wy=side==='up'?c.to[1]:side==='down'?c.from[1]:c.to[1]-(c.to[1]-c.from[1])*v;
    const axis=['east','west'].includes(side)?2:0,along=c.from[axis]+(c.to[axis]-c.from[axis])*u;
    let t=.38+.16*Math.sin(v*Math.PI)+.045*Math.cos(along*1.3),rgb=base.map((b,k)=>Math.round(b+(light[k]-b)*t));
    if(under||side==='down')rgb=lining;
    if(lip)rgb=base.map((b,k)=>Math.round(b+(light[k]-b)*.72));
    if(seam)rgb=base.map((b,k)=>Math.round(b*.7+thread[k]*.3));
    if(!under&&!lip&&!seam&&['north','south','east','west'].includes(side)){
      const stitch=(Math.floor(Math.abs(along)*7)%4)<2;
      if(stitch&&((wy>18.94&&wy<19.02)||(wy>17.59&&wy<17.67)))rgb=thread;
      if(wy>17.33&&wy<17.43)rgb=base.map(v=>Math.round(v*.93));
    }
    patch.data.set([...rgb,255],(yy*w+xx)*4);
   }
   ctx.putImageData(patch,dst[0],dst[1]);
   f.uv=[dst[0],dst[1],dst[0]+w,dst[1]+h];faces++;
  }
  c.box_uv=false;
 }
 tex.fromDataURL(canvas.toDataURL('image/png'));tex.saved=false;Project.saved=false;
 Project.name=(white?'白狐':'黑狐')+' · 无手环与翻折袖口';
 Canvas.updateAll();
 return JSON.stringify({braceletPixelsRemoved:removed,cuffCubes:cuffs.length,cuffFaces:faces,geometryChanged:false});
})()
