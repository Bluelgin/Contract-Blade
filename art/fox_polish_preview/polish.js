(() => {
 const kind=Project.name.includes('白狐')?'white':'black';
 const tex=Texture.all[0], canvas=document.createElement('canvas');
 canvas.width=512;canvas.height=512;
 const ctx=canvas.getContext('2d');ctx.drawImage(tex.img,0,0);
 const source=ctx.getImageData(0,0,512,512);
 const occupied=new Uint8Array(512*512);
 function bounds(uv) {return [Math.floor(Math.min(uv[0],uv[2])),Math.floor(Math.min(uv[1],uv[3])),Math.ceil(Math.max(uv[0],uv[2])),Math.ceil(Math.max(uv[1],uv[3]))];}
 function fill(x,y,w,h) {for(let yy=Math.max(0,y);yy<Math.min(512,y+h);yy++)for(let xx=Math.max(0,x);xx<Math.min(512,x+w);xx++)occupied[yy*512+xx]=1;}
 Cube.all.forEach(c=>Object.values(c.faces).forEach(f=>{if(f.texture!==null){const [x,y,x2,y2]=bounds(f.uv);fill(x-1,y-1,x2-x+2,y2-y+2);}}));
 function allocate(w,h) {
   for(let y=140;y+h+2<480;y++)for(let x=0;x+w+2<512;x++) {
     let free=true;
     for(let yy=y;yy<y+h+2&&free;yy++)for(let xx=x;xx<x+w+2;xx++)if(occupied[yy*512+xx]){free=false;break;}
     if(free){fill(x,y,w+2,h+2);return [x+1,y+1];}
   }
   throw new Error('No unused atlas space; refusing to overwrite any existing UV');
 }
 function names(c){const a=[];for(let p=c.parent;p&&p.name;p=p.parent)a.push(p.name);return a;}
 const hair=Cube.all.filter(c=>names(c).some(n=>/Hair|Bangs/.test(n))&&!names(c).some(n=>/Ornament/.test(n)));
 const head=Cube.all.find(c=>c.name==='Head'&&names(c).includes('MHead'));
 if(!head)throw new Error('Missing base head');
 const palettes={white:[[158,168,178],[171,181,190],[183,193,201],[196,204,211],[209,215,220],[220,224,228],[231,233,234]],black:[[23,23,34],[28,27,39],[33,31,45],[39,35,51],[45,40,58],[52,46,65],[60,52,73]]};
 const palette=palettes[kind];
 const redHair=new Set(['171,54,78','211,105,119','121,24,53','73,12,34','54,8,26','235,149,55']);
 const cache=new Map();let patched=0,recolored=0;
 function copyFace(f,isHead) {
   if(f.texture===null)return;
   const uv=f.uv.slice(),[sx,sy,x2,y2]=bounds(uv),w=x2-sx,h=y2-sy;
   if(!w||!h)return;
   const key=[isHead?'head':'hair',sx,sy,w,h].join(':');
   let dst=cache.get(key);
   if(!dst) {
     dst=allocate(w,h);cache.set(key,dst);
     const patch=ctx.createImageData(w,h);
     for(let y=0;y<h;y++)for(let x=0;x<w;x++) {
       const i=((sy+y)*512+sx+x)*4,j=(y*w+x)*4;
       let rgb=Array.from(source.data.slice(i,i+3));const alpha=source.data[i+3];
       if(alpha&&(!isHead||redHair.has(rgb.join(',')))) {
         let t;
         if(isHead) {
           const l=(rgb[0]*.3+rgb[1]*.59+rgb[2]*.11)/255;
           t=Math.max(0,Math.min(1,(l-.08)/.46));
         } else {
           const l=(rgb[0]*.3+rgb[1]*.59+rgb[2]*.11);
           t=kind==='white'?(l-173)/62:(l-20)/34;
           t=Math.max(0,Math.min(1,t));
         }
         const strand=(Math.cos((sx+x)*.72)+Math.cos((sx+x)*.31+1.1))*.045;
         const taper=h>3?Math.sin((y+.5)/h*Math.PI)*.035:0;
         const level=Math.max(0,Math.min(6,Math.round((t*.82+strand+taper+.06)*6)));
         rgb=palette[level];recolored++;
       }
       patch.data.set([...rgb,alpha],j);
     }
     ctx.putImageData(patch,dst[0],dst[1]);
     ctx.drawImage(canvas,dst[0],dst[1],w,1,dst[0],dst[1]-1,w,1);
     ctx.drawImage(canvas,dst[0],dst[1]+h-1,w,1,dst[0],dst[1]+h,w,1);
     ctx.drawImage(canvas,dst[0],dst[1]-1,1,h+2,dst[0]-1,dst[1]-1,1,h+2);
     ctx.drawImage(canvas,dst[0]+w-1,dst[1]-1,1,h+2,dst[0]+w,dst[1]-1,1,h+2);
   }
   f.uv=[uv[0]-sx+dst[0],uv[1]-sy+dst[1],uv[2]-sx+dst[0],uv[3]-sy+dst[1]];
   patched++;
 }
 [head,...hair].forEach(c=>{c.box_uv=false;Object.values(c.faces).forEach(f=>copyFace(f,c===head));});
 tex.fromDataURL(canvas.toDataURL('image/png'));tex.saved=false;Project.saved=false;
 Canvas.updateAll();
 globalThis.foxPolishReport={kind,hairCubes:hair.length,patchedFaces:patched,uniquePatches:cache.size,recoloredPixels:recolored,palette,geometryChanged:false};
 return JSON.stringify(foxPolishReport);
})()
