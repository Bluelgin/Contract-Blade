// Packed SVG-derived masks: R=edge energy, G=soft halo, B=bright core, A=coverage.
const fs=require('node:fs/promises');
const path=require('node:path');
const sharp=require('sharp');
(async()=>{
 const dir=path.resolve(__dirname,'../../../src/main/resources/assets/maid_weapon/textures/effect');
 for(const part of ['rift','great_slash']){
  const {data,info}=await sharp(path.join(dir,`black_fox_${part}.png`))
    .extend({top:48,bottom:48,left:48,right:48,background:'#00000000'})
    .ensureAlpha().raw().toBuffer({resolveWithObject:true});
  const n=info.width*info.height,edge=Buffer.alloc(n),core=Buffer.alloc(n);
  for(let i=0;i<n;i++){
   const a=data[i*4+3]/255,l=(data[i*4]*.2126+data[i*4+1]*.7152+data[i*4+2]*.0722)*a;
   edge[i]=Math.min(255,Math.max(0,l-25)*1.25);core[i]=Math.min(255,Math.max(0,l-160)*2.8);
  }
  const raw={width:info.width,height:info.height,channels:1};
  const near=await sharp(edge,{raw}).blur(5).raw().toBuffer({resolveWithObject:true});
  const far=await sharp(edge,{raw}).blur(13).raw().toBuffer({resolveWithObject:true});
  const packed=Buffer.alloc(n*4);
  for(let i=0;i<n;i++){
   const halo=Math.min(255,near.data[i*near.info.channels]*1.55+far.data[i*far.info.channels]*.95);
   packed[i*4]=edge[i];packed[i*4+1]=halo;packed[i*4+2]=core[i];
   packed[i*4+3]=Math.min(255,Math.max(edge[i],halo*2,core[i]));
  }
  await sharp(packed,{raw:{width:info.width,height:info.height,channels:4}}).png()
    .toFile(path.join(dir,`black_fox_${part}_energy.png`));
 }
 console.log('Two packed SVG energy masks exported: edge, soft halo, bright core and coverage.');
})().catch(e=>{console.error(e);process.exitCode=1});
