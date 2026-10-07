(() => {
 const white=Project.name.includes('白狐'),tex=Texture.all[0],canvas=document.createElement('canvas');canvas.width=canvas.height=1024;
 const ctx=canvas.getContext('2d');ctx.imageSmoothingEnabled=false;ctx.drawImage(tex.img,0,0);const source=ctx.getImageData(0,0,1024,1024).data,occupied=new Uint8Array(1024*1024);
 Cube.all.forEach(c=>Object.values(c.faces).forEach(f=>{if(f.texture===null)return;const u=f.uv;for(let y=Math.max(0,Math.floor(Math.min(u[1],u[3]))-1);y<Math.min(1024,Math.ceil(Math.max(u[1],u[3]))+1);y++)for(let x=Math.max(0,Math.floor(Math.min(u[0],u[2]))-1);x<Math.min(1024,Math.ceil(Math.max(u[0],u[2]))+1);x++)occupied[y*1024+x]=1;}));
 let search=0;
 function allocate(){while(search<1521){const n=search++,x=n%39*26+1,y=Math.floor(n/39)*26+1;let free=true;for(let yy=y-1;yy<y+25&&free;yy++)for(let xx=x-1;xx<x+25;xx++)if(occupied[yy*1024+xx]){free=false;break;}if(free){for(let yy=y-1;yy<y+25;yy++)for(let xx=x-1;xx<x+25;xx++)occupied[yy*1024+xx]=1;return [x,y];}}throw Error('No safe atlas space');}
 const selected=Cube.all.filter(c=>/^Merged(?:Front|Side)Robe/.test(c.name)||/^(?:[LR][FBM][123]?|B[LRM]2?|F[LRM]1?)$/.test(c.name));let faces=0;
 for(const c of selected)for(const [side,f] of Object.entries(c.faces)){
  if(f.texture===null||f.uv[0]===f.uv[2]||f.uv[1]===f.uv[3])continue;
  const dst=allocate(),uv=f.uv.slice(),patch=ctx.createImageData(24,24),outer=/^Merged/.test(c.name);
  for(let y=0;y<24;y++)for(let x=0;x<24;x++){
   const u=(x+.5)/24,v=(y+.5)/24,sx=Math.max(0,Math.min(1023,Math.floor(uv[0]+(uv[2]-uv[0])*u))),sy=Math.max(0,Math.min(1023,Math.floor(uv[1]+(uv[3]-uv[1])*v))),i=(sy*1024+sx)*4,old=Array.from(source.slice(i,i+4));
   const wy=side==='up'?c.to[1]:side==='down'?c.from[1]:c.to[1]-(c.to[1]-c.from[1])*v;
   const wx=side==='west'?c.from[0]:side==='east'?c.to[0]:c.from[0]+(c.to[0]-c.from[0])*(side==='south'?1-u:u);
   const ivory=!outer&&(white?(old[0]>115&&old[1]>110&&old[2]>95):(old[0]+old[1]+old[2]>285)),gold=!outer&&old[0]>110&&old[1]>80&&old[1]>old[2]*1.22&&old[0]>old[1]*1.04;
   const low=white?(ivory?[183,176,154]:[17,43,45]):(ivory?[78,67,91]:[29,19,42]),high=white?(ivory?[244,234,213]:[62,95,87]):(ivory?[149,132,160]:[79,57,92]);
   let t=(ivory?.59:.48)+(ivory?.12:.09)*Math.cos(wx*2.1)+.10*Math.sin((wy-10)/13*Math.PI)-.11*Math.exp(-Math.pow((wy-22.8)/1.0,2));
   if(side==='down')t-=.13;if(side==='east'||side==='west')t-=.055;t=Math.round(Math.max(0,Math.min(1,t))*16)/16;
   const rgb=gold?old.slice(0,3):low.map((a,k)=>Math.round(a+(high[k]-a)*t));patch.data.set([...rgb,old[3]],(y*24+x)*4);
  }
  ctx.putImageData(patch,dst[0],dst[1]);ctx.drawImage(canvas,dst[0],dst[1],24,1,dst[0],dst[1]-1,24,1);ctx.drawImage(canvas,dst[0],dst[1]+23,24,1,dst[0],dst[1]+24,24,1);ctx.drawImage(canvas,dst[0],dst[1]-1,1,26,dst[0]-1,dst[1]-1,1,26);ctx.drawImage(canvas,dst[0]+23,dst[1]-1,1,26,dst[0]+24,dst[1]-1,1,26);
  f.uv=[dst[0],dst[1],dst[0]+24,dst[1]+24];c.box_uv=false;faces++;
 }
 tex.fromDataURL(canvas.toDataURL('image/png'));tex.saved=false;Project.saved=false;Project.name=(white?'白狐':'黑狐')+' · 裙身布料预览';Canvas.updateAll();return JSON.stringify({robeCubes:selected.length,faces,geometryChanged:false});
})()
