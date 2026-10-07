(() => {
 const white=Project.name.includes('白狐'),tex=Texture.all[0];
 const canvas=document.createElement('canvas');canvas.width=canvas.height=1024;
 const ctx=canvas.getContext('2d');ctx.imageSmoothingEnabled=false;ctx.drawImage(tex.img,0,0);
 const source=ctx.getImageData(0,0,512,512).data;
 function names(c){const a=[];for(let p=c.parent;p&&p.name;p=p.parent)a.push(p.name);return a;}
 const visibleHands=new Set(['471fd222-f3c9-6f2f-d2cd-7a2c8fa7e99d','78183cc0-5413-05a7-9644-5cabed039756']);
 const cubes=Cube.all.filter(c=>names(c).includes('LeftArm')&&!names(c).includes('LeftHand')&&!visibleHands.has(c.uuid)&&!/GoldSeam/.test(c.name));
 let cursorX=514,cursorY=2,rowHeight=0,faces=0;
 function allocate(w,h){if(cursorX+w+2>1022){cursorX=514;cursorY+=rowHeight;rowHeight=0;}if(cursorY+h+2>1022)throw new Error('Atlas exhausted');const p=[cursorX+1,cursorY+1];cursorX+=w+2;rowHeight=Math.max(rowHeight,h+2);return p;}
 const cloth=white?[[192,184,164],[241,235,219]]:[[55,40,69],[116,95,131]];
 const lining=white?[[36,49,60],[55,69,78]]:[[28,23,40],[49,38,65]];
 const turned=white?[[214,204,182],[245,238,222]]:[[86,67,101],[132,113,145]];
 for(const c of cubes){
   for(const [side,f] of Object.entries(c.faces)){
     if(f.texture===null)continue;
     const uv=f.uv.slice(),uw=Math.abs(uv[2]-uv[0]),uh=Math.abs(uv[3]-uv[1]);if(!uw||!uh)continue;
     const w=Math.max(16,Math.min(56,Math.ceil(uw*4))),h=Math.max(16,Math.min(64,Math.ceil(uh*4))),dst=allocate(w,h),patch=ctx.createImageData(w,h);
     for(let y=0;y<h;y++)for(let x=0;x<w;x++){
       const u=(x+.5)/w,v=(y+.5)/h;
       const sx=Math.max(0,Math.min(511,Math.floor(uv[0]+(uv[2]-uv[0])*u))),sy=Math.max(0,Math.min(511,Math.floor(uv[1]+(uv[3]-uv[1])*v)));
       const alpha=source[(sy*512+sx)*4+3];
       const wy=side==='up'?c.to[1]:side==='down'?c.from[1]:c.to[1]-(c.to[1]-c.from[1])*v;
       const axis=['east','west'].includes(side)?2:0,along=c.from[axis]+(c.to[axis]-c.from[axis])*u;
       const cuff=Math.max(0,Math.min(1,(30-wy)/15));
       const fold=Math.cos(along*1.2+.25)*.045+Math.cos(along*2.1-1)*.022;
       const feather=Math.sin(along*4.9+wy*.34)*.012;
       let t=.20+cuff*.53+fold+feather,family=cloth;
       if(/UnderFold/.test(c.name)){family=lining;t=.25+cuff*.25;}
       if(/TurnedLip/.test(c.name)){family=turned;t=.35+fold;}
       if(side==='down'&&wy<18){family=lining;t=.35;}
       t=Math.round(Math.max(0,Math.min(1,t))*11)/11;
       const rgb=family[0].map((b,i)=>Math.round(b+(family[1][i]-b)*t));
       patch.data.set([...rgb,alpha],(y*w+x)*4);
     }
     ctx.putImageData(patch,dst[0],dst[1]);
     ctx.drawImage(canvas,dst[0],dst[1],w,1,dst[0],dst[1]-1,w,1);
     ctx.drawImage(canvas,dst[0],dst[1]+h-1,w,1,dst[0],dst[1]+h,w,1);
     ctx.drawImage(canvas,dst[0],dst[1]-1,1,h+2,dst[0]-1,dst[1]-1,1,h+2);
     ctx.drawImage(canvas,dst[0]+w-1,dst[1]-1,1,h+2,dst[0]+w,dst[1]-1,1,h+2);
     f.uv=[dst[0],dst[1],dst[0]+w,dst[1]+h];faces++;
   }
   c.box_uv=false;
 }
 tex.width=tex.height=tex.uv_width=tex.uv_height=1024;Project.texture_width=Project.texture_height=1024;
 tex.fromDataURL(canvas.toDataURL('image/png'));tex.saved=false;Project.saved=false;Canvas.updateAll();
 Project.name=(white?'白狐':'黑狐')+' · 连贯衣片袖子';
 return JSON.stringify({design:'continuous outer fabric, lining at opening, turned cuff and single fine gold seam',scope:'left sleeve',faces,geometryChanged:false});
})()
