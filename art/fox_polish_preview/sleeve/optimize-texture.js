(() => {
 const crypto=require('crypto'),tex=Texture.all[0],oldWidth=tex.width,oldHeight=tex.height,src=document.createElement('canvas');src.width=oldWidth;src.height=oldHeight;
 const sc=src.getContext('2d');sc.drawImage(tex.img,0,0);const pixels=sc.getImageData(0,0,oldWidth,oldHeight).data,patches=new Map(),faces=[];
 for(const c of Cube.all)for(const f of Object.values(c.faces)){
  if(f.texture===null)continue;const uv=f.uv.slice(),x=Math.floor(Math.min(uv[0],uv[2])),y=Math.floor(Math.min(uv[1],uv[3])),w=Math.max(1,Math.ceil(Math.max(uv[0],uv[2]))-x),h=Math.max(1,Math.ceil(Math.max(uv[1],uv[3]))-y),data=new Uint8ClampedArray(w*h*4);
  for(let yy=0;yy<h;yy++)for(let xx=0;xx<w;xx++){const sx=Math.max(0,Math.min(oldWidth-1,x+xx)),sy=Math.max(0,Math.min(oldHeight-1,y+yy));data.set(pixels.subarray((sy*oldWidth+sx)*4,(sy*oldWidth+sx)*4+4),(yy*w+xx)*4);}
  let constant=true;for(let i=4;i<data.length;i++)if(data[i]!==data[i%4]){constant=false;break;}
  const pw=constant?1:w,ph=constant?1:h,content=constant?data.slice(0,4):data,key=pw+':'+ph+':'+crypto.createHash('sha256').update(content).digest('hex');
  let patch=patches.get(key);if(!patch){patch={w:pw,h:ph,data:content};patches.set(key,patch);}
  faces.push({c,f,uv,x,y,w,h,constant,patch});
 }
 const sorted=[...patches.values()].sort((a,b)=>Math.max(b.w,b.h)-Math.max(a.w,a.h)||(b.w+2)*(b.h+2)-(a.w+2)*(a.h+2));
 function pack(size){let free=[{x:0,y:0,w:size,h:size}],result=new Map();
  for(const p of sorted){const w=p.w+2,h=p.h+2;let best=null;
   for(const r of free)if(w<=r.w&&h<=r.h){const score=[Math.min(r.w-w,r.h-h),Math.max(r.w-w,r.h-h)];if(!best||score[0]<best.score[0]||(score[0]===best.score[0]&&score[1]<best.score[1]))best={r,score};}
   if(!best)return null;const placed={x:best.r.x,y:best.r.y,w,h},next=[];
   for(const r of free){if(placed.x>=r.x+r.w||placed.x+w<=r.x||placed.y>=r.y+r.h||placed.y+h<=r.y){next.push(r);continue;}
    if(placed.x>r.x)next.push({x:r.x,y:r.y,w:placed.x-r.x,h:r.h});if(placed.x+w<r.x+r.w)next.push({x:placed.x+w,y:r.y,w:r.x+r.w-placed.x-w,h:r.h});
    if(placed.y>r.y)next.push({x:r.x,y:r.y,w:r.w,h:placed.y-r.y});if(placed.y+h<r.y+r.h)next.push({x:r.x,y:placed.y+h,w:r.w,h:r.y+r.h-placed.y-h});
   }
   free=next.filter((r,i)=>!next.some((s,j)=>i!==j&&r.x>=s.x&&r.y>=s.y&&r.x+r.w<=s.x+s.w&&r.y+r.h<=s.y+s.h&&(r.w*r.h<s.w*s.h||i>j)));
   result.set(p,{x:placed.x+1,y:placed.y+1});
  }return result;
 }
 let size=512,positions=pack(size);if(!positions){size=1024;positions=pack(size);}if(!positions)throw Error('Atlas cannot fit without resampling');
 const canvas=document.createElement('canvas');canvas.width=canvas.height=size;const ctx=canvas.getContext('2d');ctx.imageSmoothingEnabled=false;
 for(const p of sorted){const at=positions.get(p),img=ctx.createImageData(p.w+2,p.h+2);for(let y=0;y<p.h+2;y++)for(let x=0;x<p.w+2;x++){const sx=Math.max(0,Math.min(p.w-1,x-1)),sy=Math.max(0,Math.min(p.h-1,y-1));img.data.set(p.data.subarray((sy*p.w+sx)*4,(sy*p.w+sx)*4+4),(y*(p.w+2)+x)*4);}ctx.putImageData(img,at.x-1,at.y-1);}
 tex.width=tex.height=tex.uv_width=tex.uv_height=size;Project.texture_width=Project.texture_height=size;
 for(const item of faces){const {f,c,uv,x,y,constant,patch}=item,at=positions.get(patch);f.uv=constant?[at.x+(uv[0]<=uv[2]?0:1),at.y+(uv[1]<=uv[3]?0:1),at.x+(uv[0]<=uv[2]?1:0),at.y+(uv[1]<=uv[3]?1:0)]:[at.x+uv[0]-x,at.y+uv[1]-y,at.x+uv[2]-x,at.y+uv[3]-y];c.box_uv=false;}
 tex.fromDataURL(canvas.toDataURL('image/png'));tex.saved=false;Project.saved=false;Canvas.updateAll();return JSON.stringify({oldSize:[oldWidth,oldHeight],newSize:size,faces:faces.length,uniquePatches:patches.size,constantFaces:faces.filter(f=>f.constant).length,detailResampled:false});
})()
