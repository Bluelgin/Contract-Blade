(() => {
 const white=Project.name.includes('白狐');
 const tex=Texture.all[0],canvas=document.createElement('canvas');
 canvas.width=1024;canvas.height=1024;
 const ctx=canvas.getContext('2d');ctx.imageSmoothingEnabled=false;ctx.drawImage(tex.img,0,0);
 const original=ctx.getImageData(0,0,512,512);
 const data=original.data;
 function names(c){const a=[];for(let p=c.parent;p&&p.name;p=p.parent)a.push(p.name);return a;}
 const cubes=Cube.all.filter(c=>names(c).includes('LeftArm')&&!names(c).includes('LeftHand')&&!/GoldSeam/.test(c.name));
 function sample(x,y){x=Math.max(0,Math.min(511,Math.floor(x)));y=Math.max(0,Math.min(511,Math.floor(y)));const i=(y*512+x)*4;return Array.from(data.slice(i,i+4));}
 function material(rgb) {
   const [r,g,b,a]=rgb;if(!a)return 'empty';
   if(r-g>10&&g-b>25)return 'gold';
   if(white){if(r+g+b<260)return 'dark';if(b-r>3)return 'cool';return 'cream';}
   if(r+g+b<95)return 'dark';return 'purple';
 }
 const families=white?{cream:[[183,176,158],[242,235,217]],cool:[[203,208,207],[245,241,228]],dark:[[29,43,54],[59,78,89]]}:{purple:[[53,42,65],[121,102,134]],dark:[[26,24,37],[64,52,80]]};
 let cursorX=514,cursorY=2,rowHeight=0,faces=0,pixels=0;
 function allocate(w,h){if(cursorX+w+2>1022){cursorX=514;cursorY+=rowHeight;rowHeight=0;}if(cursorY+h+2>1022)throw new Error('Preview atlas exhausted');const result=[cursorX+1,cursorY+1];cursorX+=w+2;rowHeight=Math.max(rowHeight,h+2);return result;}
 function softLuma(x,y,family){
   const ix=Math.floor(x-.5),iy=Math.floor(y-.5),tx=x-.5-ix,ty=y-.5-iy;
   let sum=0,weights=0;
   for(let yy=0;yy<2;yy++)for(let xx=0;xx<2;xx++){
     const p=sample(ix+xx,iy+yy),weight=(xx?tx:1-tx)*(yy?ty:1-ty);
     if(material(p)===family){sum+=(p[0]*.3+p[1]*.59+p[2]*.11)*weight;weights+=weight;}
   }
   return weights?sum/weights:0;
 }
 cubes.forEach(c=>{
   let any=false;
   for(const [side,f] of Object.entries(c.faces)){
     if(f.texture===null)continue;
     const uv=f.uv.slice(),uw=Math.abs(uv[2]-uv[0]),uh=Math.abs(uv[3]-uv[1]);if(!uw||!uh)continue;
     let fabric=false;
     for(let y=Math.floor(Math.min(uv[1],uv[3]));y<Math.ceil(Math.max(uv[1],uv[3]))&&!fabric;y++)for(let x=Math.floor(Math.min(uv[0],uv[2]));x<Math.ceil(Math.max(uv[0],uv[2]));x++){const m=material(sample(x,y));if(m!=='gold'&&m!=='empty'){fabric=true;break;}}
     if(!fabric)continue;
     const w=Math.max(16,Math.min(56,Math.ceil(uw*4))),h=Math.max(16,Math.min(64,Math.ceil(uh*4)));
     const dst=allocate(w,h),patch=ctx.createImageData(w,h);
     for(let y=0;y<h;y++)for(let x=0;x<w;x++){
       const u=(x+.5)/w,v=(y+.5)/h,sx=uv[0]+(uv[2]-uv[0])*u,sy=uv[1]+(uv[3]-uv[1])*v;
       const rgb=sample(sx,sy),family=material(rgb);let out=rgb;
       if(family!=='gold'&&family!=='empty'){
         const base=families[family][0],light=families[family][1];
         const wy=side==='up'?c.to[1]:side==='down'?c.from[1]:c.to[1]-(c.to[1]-c.from[1])*v;
         const span=['east','west'].includes(side)?c.to[2]-c.from[2]:c.to[0]-c.from[0];
         const along=(['east','west'].includes(side)?c.from[2]:c.from[0])+span*u;
         const towardsCuff=Math.max(0,Math.min(1,(30-wy)/15));
         const fold=Math.cos(along*1.3+.5)*.075+Math.cos(along*2.4-1)*.035;
         const feather=Math.sin(along*6.5+wy*.4)*.019+Math.sin(along*3.4-wy*.7)*.016;
         const luma=softLuma(sx,sy,family),mid=(base[0]+base[1]+base[2])/3;
         const retainedShade=Math.max(-.10,Math.min(.10,(luma-mid)/255));
         let t=Math.max(0,Math.min(1,.12+towardsCuff*.60+fold+feather+retainedShade));
         t=Math.round(t*9)/9;
         out=[...base.map((b,i)=>Math.round(b+(light[i]-b)*t)),rgb[3]];pixels++;
       }
       patch.data.set(out,(y*w+x)*4);
     }
     ctx.putImageData(patch,dst[0],dst[1]);
     ctx.drawImage(canvas,dst[0],dst[1],w,1,dst[0],dst[1]-1,w,1);
     ctx.drawImage(canvas,dst[0],dst[1]+h-1,w,1,dst[0],dst[1]+h,w,1);
     ctx.drawImage(canvas,dst[0],dst[1]-1,1,h+2,dst[0]-1,dst[1]-1,1,h+2);
     ctx.drawImage(canvas,dst[0]+w-1,dst[1]-1,1,h+2,dst[0]+w,dst[1]-1,1,h+2);
     f.uv=[dst[0],dst[1],dst[0]+w,dst[1]+h];faces++;any=true;
   }
   if(any)c.box_uv=false;
 });
 tex.width=tex.height=tex.uv_width=tex.uv_height=1024;
 Project.texture_width=Project.texture_height=1024;
 tex.fromDataURL(canvas.toDataURL('image/png'));tex.saved=false;Project.saved=false;
 Canvas.updateAll();
 return JSON.stringify({sample:'LeftArm only',cubes:cubes.length,faces,paintedFabricPixels:pixels,previewAtlas:[1024,1024],geometryChanged:false,goldPreserved:true});
})()
