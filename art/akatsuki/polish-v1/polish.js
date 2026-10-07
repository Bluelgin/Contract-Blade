(() => {
  const fs = require('fs');
  const out = 'E:/Little maid/art/akatsuki/polish-v1';
  fs.mkdirSync(out, {recursive:true});
  const sourceFile = 'E:/Akatsuki-Taisho-Wine-Fox/source/akatsuki-1.2.3.bbmodel';
  const source = JSON.parse(fs.readFileSync(sourceFile, 'utf8'));
  Codecs.project.load(source, {path:sourceFile,no_file:true});
  Project.name = 'Akatsuki clothing polish v1';
  Modes.options.edit.select();
  const atlas = Texture.all[0];
  const canvas = document.createElement('canvas');
  canvas.width = canvas.height = 256;
  const ctx = canvas.getContext('2d');
  ctx.drawImage(atlas.img,0,0);
  const pixels = ctx.getImageData(0,0,256,256);
  const original = new Uint8ClampedArray(pixels.data);
  const protectedIds = new Set(['471fd222-f3c9-6f2f-d2cd-7a2c8fa7e99d','78183cc0-5413-05a7-9644-5cabed039756']);
  const ancestry = c => {const names=[];for(let p=c.parent;p && p.name;p=p.parent)names.push(p.name);return names;};
  const isCloth = c => !protectedIds.has(c.uuid) && (['UpperBody','LeftArm','RightArm','LeftForeArm','RightForeArm'].includes(c.parent?.name) || ancestry(c).includes('clothe')) && !ancestry(c).includes('FOX');
  const protectedMask = new Uint8Array(65536);
  const contributions = new Map();
  function eachPixel(face, fn) {
    if(face.texture===null)return;
    const [u1,v1,u2,v2]=face.uv;
    const left=Math.max(0,Math.floor(Math.min(u1,u2))),right=Math.min(256,Math.ceil(Math.max(u1,u2)));
    const top=Math.max(0,Math.floor(Math.min(v1,v2))),bottom=Math.min(256,Math.ceil(Math.max(v1,v2)));
    for(let y=top;y<bottom;y++)for(let x=left;x<right;x++)fn(y*256+x,(x+.5-left)/Math.max(1,right-left),(y+.5-top)/Math.max(1,bottom-top),right-left,bottom-top);
  }
  for(const c of Cube.all)for(const [side,f] of Object.entries(c.faces)) {
    if(!isCloth(c)){eachPixel(f,i=>protectedMask[i]=1);continue;}
    eachPixel(f,(i,x,y,w,h)=>{
      const a=contributions.get(i)||[];
      a.push({x,y,w,h,side,skirt:ancestry(c).includes('clothe'),cuff:c.parent?.name.includes('ForeArm') && c.to[1]-c.from[1]<2.5});
      contributions.set(i,a);
    });
  }
  const clamp = v=>Math.max(0,Math.min(255,Math.round(v)));
  let changed=0,wine=0,ivory=0,dark=0;
  for(const [i,entries] of contributions) {
    if(protectedMask[i] || original[i*4+3]===0)continue;
    const offset=i*4, r=original[offset],g=original[offset+1],b=original[offset+2];
    const sample=entries[0];
    const edge=Math.min(sample.x,1-sample.x);
    const fold=Math.cos(sample.x*Math.PI*2)*2.2;
    let rgb;
    if(r>g*1.65 && r>b*1.12 && r>50) {
      // Retain existing embroidery and wine identity; soften the harsh red patches.
      const light=(r-103)*.66 + (sample.skirt?fold:fold*.6) + (sample.y-.5)*-3;
      rgb=[111+light,25+light*.34,46+light*.48];wine++;
    } else if(r>165 && g>150 && b>145 && Math.max(r,g,b)-Math.min(r,g,b)<45) {
      // Ivory fabric with warm highlights, cool recesses and readable rolled cuffs.
      const shade=(r+g+b)/3-218;
      const recess=edge<.16?-5:1;
      rgb=[234+shade*.65+recess,226+shade*.65+recess,211+shade*.62+recess];ivory++;
    } else if(r<65 && g<55 && b<75) {
      const light=(r+g+b)/3-29;
      rgb=[29+light*.74+fold*.7,24+light*.74+fold*.7,32+light*.74+fold*.7];dark++;
    } else continue;
    for(let k=0;k<3;k++)pixels.data[offset+k]=clamp(rgb[k]);
    if(rgb.some((v,k)=>clamp(v)!==original[offset+k]))changed++;
  }
  ctx.putImageData(pixels,0,0);
  const dataUrl=canvas.toDataURL('image/png');
  atlas.fromDataURL(dataUrl);
  atlas.name='akatsuki-clothing-polish-v1.png';
  Canvas.updateAll();
  fs.writeFileSync(out+'/akatsuki-clothing-polish-v1.png',Buffer.from(dataUrl.split(',')[1],'base64'));
  fs.writeFileSync(out+'/akatsuki-clothing-polish-v1.bbmodel',Codecs.project.compile());
  const report={source:sourceFile,cubes:Cube.all.length,groups:Group.all.length,resolution:[256,256],changedTexels:changed,materials:{wine,ivory,dark},protectedTexels:protectedMask.reduce((a,b)=>a+b,0),geometryChanged:false,bonesChanged:false,alphaChanged:false};
  fs.writeFileSync(out+'/validation.json',JSON.stringify(report,null,2));
  // Hide the alternate fox form in the review only; the saved model preserves it.
  for(const g of Group.all)if(g.name==='FOX')g.visibility=false;
  Canvas.updateVisibility();
  const p=Preview.selected;
  p.controls.autoRotate=false;
  p.camera.position.set(32,29,-93);
  p.controls.target.set(0,21,0);
  p.camera.zoom=1;
  p.camera.updateProjectionMatrix();p.controls.update();
  Project.saved=false;
  return JSON.stringify(report);
})()
