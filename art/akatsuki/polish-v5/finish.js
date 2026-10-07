(() => {
const fs=require('fs'),out='E:/Little maid/art/akatsuki/polish-v5';
const tex=Texture.all[0],fx=Texture.all.find(t=>t.name==='akatsuki-moon-aura.png'),moon=Cube.all.find(c=>c.name==='AkatsukiHairOrnament');
const canvas=document.createElement('canvas');canvas.width=canvas.height=512;const ctx=canvas.getContext('2d');ctx.imageSmoothingEnabled=false;ctx.drawImage(tex.img,0,0);ctx.drawImage(fx.img,256,0);
for(const side of ['north','south']){moon.faces[side].texture=tex.uuid;moon.faces[side].uv=side==='south'?[320,0,256,64]:[256,0,320,64];}
const central=Cube.all.filter(c=>['FM','FM1','FM2'].includes(c.parent?.name));
for(const c of central)for(const side of ['north','south']){const uv=c.faces[side].uv,x0=Math.min(uv[0],uv[2]),y0=Math.min(uv[1],uv[3]),w=Math.abs(uv[2]-uv[0]),h=Math.abs(uv[3]-uv[1]),patch=ctx.getImageData(x0,y0,w,h);for(let y=0;y<h;y++)for(let x=0;x<w;x++){const u=(x+.5)/w,v=(y+.5)/h,i=(y*w+x)*4,wy=c.to[1]-(c.to[1]-c.from[1])*v,wx=c.from[0]+(c.to[0]-c.from[0])*u;const t=Math.max(0,Math.min(1,(wy-8)/15)),spread=.35+t*.9,fold=-Math.exp(-1*((wx-spread)/.28)**2)*12-Math.exp(-1*((wx+spread*.8)/.26)**2)*9;const hem=c.parent.name==='FM2',bottom=v>.81,shade=Math.round((fold-Math.abs(wx)*2)/2)*2;let rgb=[120+shade,30+shade*.42,44+shade*.65];if(hem&&bottom){const end=.93-.018*Math.abs(wx);if(v>end)rgb=[40,25,30];else if(v>end-.045)rgb=[155,113,52];else rgb=[84+shade,25,36];}patch.data[i]=rgb[0];patch.data[i+1]=rgb[1];patch.data[i+2]=rgb[2];}ctx.putImageData(patch,x0,y0);}
tex.width=tex.height=tex.uv_width=tex.uv_height=512;Project.texture_width=Project.texture_height=512;tex.fromDataURL(canvas.toDataURL());fx.remove();Canvas.updateAll();
return JSON.stringify({moonTextureMerged:true,centralFoldDirection:'waist to narrowed lower folds',next:'lossless atlas packing'});
})()
