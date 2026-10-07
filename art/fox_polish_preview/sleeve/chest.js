(() => {
 const tex=Texture.all[0],white=Project.name.includes('白狐');
 const canvas=document.createElement('canvas');canvas.width=canvas.height=1024;
 const ctx=canvas.getContext('2d');ctx.imageSmoothingEnabled=false;ctx.drawImage(tex.img,0,0);
 const source=ctx.getImageData(0,0,1024,1024).data,occupied=new Uint8Array(1024*1024);
 Cube.all.forEach(c=>Object.values(c.faces).forEach(f=>{if(f.texture===null)return;const u=f.uv;for(let y=Math.max(0,Math.floor(Math.min(u[1],u[3]))-1);y<Math.min(1024,Math.ceil(Math.max(u[1],u[3]))+1);y++)for(let x=Math.max(0,Math.floor(Math.min(u[0],u[2]))-1);x<Math.min(1024,Math.ceil(Math.max(u[0],u[2]))+1);x++)occupied[y*1024+x]=1;}));
 let search=0;
 function allocate(){while(search<1521){const n=search++,x=(n%39)*26+1,y=Math.floor(n/39)*26+1;let free=true;for(let yy=y-1;yy<y+25&&free;yy++)for(let xx=x-1;xx<x+25;xx++)if(occupied[yy*1024+xx]){free=false;break;}if(free){for(let yy=y-1;yy<y+25;yy++)for(let xx=x-1;xx<x+25;xx++)occupied[yy*1024+xx]=1;return [x,y];}}throw new Error('No safe chest atlas space');}
 const selected=Cube.all.filter(c=>(c.name==='UpperBody'&&c.from[1]>24&&c.to[1]-c.from[1]>=1)||/^LayerFur/.test(c.name)||/^Shoulder(?:-?1)$/.test(c.name));
 const furCache=new Map();let faces=0;
 const cloth=white?[[214,209,190],[244,238,221]]:[[82,63,100],[127,105,143]];
 const binding=white?[[54,85,80],[101,131,117]]:[[49,34,67],[83,60,102]];
 const fur=white?[[201,207,202],[243,242,230]]:[[63,53,80],[100,85,120]];
 for(const c of selected){
  const furry=/^LayerFur/.test(c.name),lapel=c.name==='UpperBody'&&Math.abs(c.rotation[2])>35;
  for(const [side,f] of Object.entries(c.faces)){
   if(f.texture===null)continue;const uv=f.uv.slice();if(uv[0]===uv[2]||uv[1]===uv[3])continue;
   const furKey=furry?[side,/Tip/.test(c.name)?'tip':/Lower/.test(c.name)?'lower':'base'].join(':'):null;
   let dst=furKey?furCache.get(furKey):null;
   if(!dst){
    dst=allocate();const patch=ctx.createImageData(24,24);
    for(let y=0;y<24;y++)for(let x=0;x<24;x++){
     const u=(x+.5)/24,v=(y+.5)/24;
     let t=.42+.16*Math.cos((u-.5)*Math.PI)+.09*Math.sin(v*Math.PI),family=furry?fur:cloth;
     if(furry){t=.40+.23*(1-v)+.08*Math.cos(u*5);if(/Tip/.test(c.name))t+=.10;}
     const seam=lapel&&['north','south'].includes(side)&&v>.82;
     if(seam){family=binding;t=.35;}
     if(!furry&&!lapel){const wy=c.to[1]-(c.to[1]-c.from[1])*v;t-=Math.max(0,(27-wy))*.035;}
     const ripple=Math.sin(u*13+v*2)*.015;
     t=Math.round(Math.max(0,Math.min(1,t+ripple))*12)/12;
     let rgb=family[0].map((b,k)=>Math.round(b+(family[1][k]-b)*t));
     if(lapel&&!seam&&v>.73&&v<.78&&Math.floor(u*18)%3===0)rgb=rgb.map(a=>Math.round(a*.94));
     const sx=Math.max(0,Math.min(1023,Math.floor(uv[0]+(uv[2]-uv[0])*u))),sy=Math.max(0,Math.min(1023,Math.floor(uv[1]+(uv[3]-uv[1])*v)));
     const alpha=furry?255:source[(sy*1024+sx)*4+3];
     patch.data.set([...rgb,alpha],(y*24+x)*4);
    }
    ctx.putImageData(patch,dst[0],dst[1]);
    ctx.drawImage(canvas,dst[0],dst[1],24,1,dst[0],dst[1]-1,24,1);
    ctx.drawImage(canvas,dst[0],dst[1]+23,24,1,dst[0],dst[1]+24,24,1);
    ctx.drawImage(canvas,dst[0],dst[1]-1,1,26,dst[0]-1,dst[1]-1,1,26);
    ctx.drawImage(canvas,dst[0]+23,dst[1]-1,1,26,dst[0]+24,dst[1]-1,1,26);
    if(furKey)furCache.set(furKey,dst);
   }
   f.uv=[dst[0],dst[1],dst[0]+24,dst[1]+24];faces++;
  }
  c.box_uv=false;
 }
 tex.fromDataURL(canvas.toDataURL('image/png'));tex.saved=false;Project.saved=false;
 Project.name=(white?'白狐':'黑狐')+' · 交领与肩部优化';Canvas.updateAll();
 return JSON.stringify({chestShoulderCubes:selected.length,faces,geometryChanged:false,faceHairHandsUntouched:true});
})()
