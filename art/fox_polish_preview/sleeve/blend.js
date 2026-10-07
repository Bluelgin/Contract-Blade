(() => {
 const white=Project.name.includes('白狐'),tex=Texture.all[0];
 const canvas=document.createElement('canvas');canvas.width=canvas.height=1024;
 const ctx=canvas.getContext('2d');ctx.imageSmoothingEnabled=false;ctx.drawImage(tex.img,0,0);
 const source=ctx.getImageData(0,0,1024,1024).data;
 function names(c){const a=[];for(let p=c.parent;p&&p.name;p=p.parent)a.push(p.name);return a;}
 const hands=new Set(['471fd222-f3c9-6f2f-d2cd-7a2c8fa7e99d','78183cc0-5413-05a7-9644-5cabed039756']);
 const cubes=Cube.all.filter(c=>names(c).some(n=>n==='LeftArm'||n==='RightArm')&&!names(c).some(n=>n==='LeftHand'||n==='RightHand')&&!hands.has(c.uuid));
 const occupied=new Uint8Array(1024*1024);
 Cube.all.forEach(c=>Object.values(c.faces).forEach(f=>{if(f.texture===null)return;const u=f.uv;for(let y=Math.floor(Math.min(u[1],u[3]))-1;y<=Math.ceil(Math.max(u[1],u[3]));y++)for(let x=Math.floor(Math.min(u[0],u[2]))-1;x<=Math.ceil(Math.max(u[0],u[2]));x++)if(x>=0&&y>=0&&x<1024&&y<1024)occupied[y*1024+x]=1;}));
 let cursorX=2,cursorY=552,faces=0;
 function allocate(){const w=34,h=26;while(cursorY+h<1024){if(cursorX+w>=510){cursorX=2;cursorY+=h;continue;}let free=true;for(let y=cursorY;y<cursorY+h&&free;y++)for(let x=cursorX;x<cursorX+w;x++)if(occupied[y*1024+x]){free=false;break;}const a=[cursorX+1,cursorY+1];cursorX+=w;if(free){for(let y=a[1]-1;y<a[1]+25;y++)for(let x=a[0]-1;x<a[0]+33;x++)occupied[y*1024+x]=1;return a;}}throw new Error('No safe space for sleeve preview');}
 const anchors=white?[[229,223,204],[198,210,190],[121,166,149],[47,94,91]]:[[110,89,125],[100,79,116],[73,54,91],[43,30,58]];
 const cuff=white?[34,72,76]:[36,26,51],lip=white?[54,95,95]:[52,39,69],lining=white?[25,46,53]:[25,19,36];
 const gold=white?[192,164,96]:[156,128,83],thread=white?[176,153,101]:[115,90,136];
 function mix(a,b,t){return a.map((v,i)=>Math.round(v+(b[i]-v)*t));}
 for(const c of cubes){
  for(const [side,f] of Object.entries(c.faces)){
   if(f.texture===null)continue;const uv=f.uv.slice();if(uv[0]===uv[2]||uv[1]===uv[3])continue;
   const dst=allocate(),w=32,h=24,patch=ctx.createImageData(w,h);
   for(let y=0;y<h;y++)for(let x=0;x<w;x++){
    const u=(x+.5)/w,v=(y+.5)/h,wy=side==='up'?c.to[1]:side==='down'?c.from[1]:c.to[1]-(c.to[1]-c.from[1])*v;
    const axis=['east','west'].includes(side)?2:0,along=Math.abs(c.from[axis]+(c.to[axis]-c.from[axis])*u);
    const wave=Math.sin(along*1.35)*.15+Math.sin(along*4.1+wy*.34)*.055;
    let t=Math.max(0,Math.min(1,(24.6-wy+wave)/5.3));t=t*t*(3-2*t);t=Math.round(t*18)/18;
    const pos=t*3,index=Math.min(2,Math.floor(pos));let rgb=mix(anchors[index],anchors[index+1],pos-index);
    const fold=Math.cos(along*1.3)*2;rgb=rgb.map(a=>Math.max(0,Math.round(a+fold)));
    const shell=c.from[1]>17.1&&c.from[1]<17.4&&c.to[1]>19&&c.to[1]<19.5;
    if(shell)rgb=mix(cuff,lip,.22+.17*Math.sin(v*Math.PI));
    if(/UnderFold/.test(c.name)||side==='down'&&wy<18)rgb=lining;
    if(/TurnedLip/.test(c.name))rgb=lip;
    if(/GoldSeam/.test(c.name))rgb=gold;
    if(shell&&['north','south','east','west'].includes(side)&&(Math.floor(along*7)%4)<2&&((wy>18.94&&wy<19.02)||(wy>17.59&&wy<17.67)))rgb=thread;
    const sx=Math.max(0,Math.min(1023,Math.floor(uv[0]+(uv[2]-uv[0])*u))),sy=Math.max(0,Math.min(1023,Math.floor(uv[1]+(uv[3]-uv[1])*v)));
    patch.data.set([...rgb,source[(sy*1024+sx)*4+3]],(y*w+x)*4);
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
 tex.fromDataURL(canvas.toDataURL('image/png'));tex.saved=false;Project.saved=false;
 Project.name=white?'白狐 · 青金米白袖子':'黑狐 · 墨紫渐染袖子';Canvas.updateAll();
 return JSON.stringify({bothSleeves:true,faces,cubes:cubes.length,geometryChanged:false,handsExcluded:true});
})()
