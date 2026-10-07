(() => {
 const white=Project.name.includes('白狐'),tex=Texture.all[0],canvas=document.createElement('canvas');canvas.width=canvas.height=1024;
 const ctx=canvas.getContext('2d');ctx.imageSmoothingEnabled=false;ctx.drawImage(tex.img,0,0);
 const occupied=new Uint8Array(1024*1024);
 Cube.all.forEach(c=>Object.values(c.faces).forEach(f=>{if(f.texture===null)return;const u=f.uv;for(let y=Math.max(0,Math.floor(Math.min(u[1],u[3]))-1);y<Math.min(1024,Math.ceil(Math.max(u[1],u[3]))+1);y++)for(let x=Math.max(0,Math.floor(Math.min(u[0],u[2]))-1);x<Math.min(1024,Math.ceil(Math.max(u[0],u[2]))+1);x++)occupied[y*1024+x]=1;}));
 let search=0;
 function allocate(){while(search<1521){const n=search++,x=n%39*26+1,y=Math.floor(n/39)*26+1;let free=true;for(let yy=y-1;yy<y+25&&free;yy++)for(let xx=x-1;xx<x+25;xx++)if(occupied[yy*1024+xx]){free=false;break;}if(free){for(let yy=y-1;yy<y+25;yy++)for(let xx=x-1;xx<x+25;xx++)occupied[yy*1024+xx]=1;return [x,y];}}throw Error('No safe atlas space');}
 const selected=Cube.all.filter(c=>/^Shoulder(?:-?1)$/.test(c.name)||(/^(Left|Right)Arm$/.test(c.name)&&c.from[1]>29&&c.to[1]<31)),cache=new Map();let faces=0;
 for(const c of selected)for(const [side,f] of Object.entries(c.faces)){
  if(f.texture===null||f.uv[0]===f.uv[2]||f.uv[1]===f.uv[3])continue;
  const key=side+':'+Math.round(c.to[1]*10);let dst=cache.get(key);
  if(!dst){dst=allocate();const patch=ctx.createImageData(24,24);
   for(let y=0;y<24;y++)for(let x=0;x<24;x++){
    const u=(x+.5)/24,v=(y+.5)/24,wy=side==='up'?c.to[1]:side==='down'?c.from[1]:c.to[1]-(c.to[1]-c.from[1])*v;
    const fall=Math.max(0,Math.min(1,(30.25-wy)/2.4)),strand=Math.pow(Math.max(0,Math.cos((u+.035*Math.sin(v*2))*Math.PI*6)),8)*.055;
    let t=.26+.64*fall+strand;if(side==='down')t-=.24;if(side==='up')t+=.12;t=Math.round(Math.max(0,Math.min(1,t))*20)/20;
    const root=white?[165,185,188]:[35,25,52],tip=white?[245,247,241]:[110,94,126],rgb=root.map((a,k)=>Math.round(a+(tip[k]-a)*t));patch.data.set([...rgb,255],(y*24+x)*4);
   }
   ctx.putImageData(patch,dst[0],dst[1]);ctx.drawImage(canvas,dst[0],dst[1],24,1,dst[0],dst[1]-1,24,1);ctx.drawImage(canvas,dst[0],dst[1]+23,24,1,dst[0],dst[1]+24,24,1);ctx.drawImage(canvas,dst[0],dst[1]-1,1,26,dst[0]-1,dst[1]-1,1,26);ctx.drawImage(canvas,dst[0]+23,dst[1]-1,1,26,dst[0]+24,dst[1]-1,1,26);cache.set(key,dst);
  }
  f.uv=[dst[0],dst[1],dst[0]+24,dst[1]+24];c.box_uv=false;faces++;
 }
 tex.fromDataURL(canvas.toDataURL('image/png'));tex.saved=false;Project.saved=false;Project.name=(white?'白狐':'黑狐')+' · 完整肩饰分层预览';Canvas.updateAll();return JSON.stringify({shoulderCapCubes:selected.length,faces,geometryChanged:false});
})()
