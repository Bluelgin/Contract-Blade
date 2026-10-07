// Review-only SVG sequence art. Fixed canvases prevent frame-to-frame texture wobble.
const fs=require('node:fs/promises');
const path=require('node:path');
const sharp=require('sharp');
const {execFileSync}=require('node:child_process');
const out=__dirname;
const clamp=x=>Math.max(0,Math.min(1,x));
const smooth=x=>{x=clamp(x);return x*x*(3-2*x)};
const wrap=(body,w,h)=>`<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}">${body}</svg>`;
function rift(frame){
 const p=frame/23,open=smooth(p/.24)*(1-smooth((p-.78)/.22));
 if(frame===0||frame===23)return wrap('',128,320);
 const burst=smooth((p-.63)/.2),alpha=1-smooth((p-.84)/.16);
 const width=3+16*open;
 const points=[[-4,-125],[5,-88],[-3,-54],[8,-17],[1,21],[6,62],[-3,124]];
 const taper=i=>Math.sin(i/(points.length-1)*Math.PI);
 const left=points.map(([x,y],i)=>[x-width*taper(i)*(.45+.4*Math.sin(i*1.4+1)**2),y]);
 const right=points.map(([x,y],i)=>[x+width*taper(i)*(.4+.5*Math.cos(i*1.6)**2),y]).reverse();
 const d='M'+left.concat(right).map(v=>v.join(' ')).join('L')+'Z';
 const crack='M'+points.map(v=>v.join(' ')).join('L');
 let b=`<g transform="translate(64 160)" opacity="${alpha}">
 <path d="${d}" fill="#451b69" stroke="#623290" stroke-width="9" opacity=".35"/>
 <path d="${d}" fill="#080b16" stroke="#8c54cc" stroke-width="1.5"/>
 <path d="${crack}" fill="none" stroke="#b37aea" stroke-width="${1+open*1.3}"/>
 <path d="${crack}" fill="none" stroke="#ead4ff" stroke-width="1.4" stroke-dasharray="15 47" stroke-dashoffset="${-frame*15}"/>
 <path d="${crack}" fill="none" stroke="#854cd3" stroke-width="5" stroke-dasharray="3 54" stroke-dashoffset="${frame*11}" opacity=".5"/>
 <g stroke="#a46edf" stroke-width="1.4" fill="none" opacity="${open}"><path d="M-8-54L-30-72-40-96M8 22L33 40 44 69M-8 67L-31 84"/></g>`;
 // Inward attraction reverses into shards at breach. Fragments themselves move; the texture is not scaled.
 for(let i=0;i<9;i++){
  const cycle=((frame+i*2)%12)/12;
  const travel=burst>.2? .25+burst*.85:1-cycle*.8;
  const angle=i*2.4;
  const x=Math.cos(angle)*(24+18*(i%2))*travel;
  const y=(i-4)*25*travel;
  const a=open*(burst>.2?1-burst*.35:Math.sin(cycle*Math.PI));
  b+=`<path d="M-2-5L3-1 1 5-3 1Z" transform="translate(${x} ${y}) rotate(${i*31+frame*4})" fill="${i%3===0?'#ddbcff':'#9454db'}" opacity="${a}"/>`;
 }
 b+='</g>';return wrap(b,128,320);
}
const curve='M475 28Q354 102 42 170';
function slash(frame){
 if(frame===0||frame===15)return wrap('',512,192);
 const p=frame/15,spread=smooth(p/.28),breakup=smooth((p-.5)/.5);
 const body=`<path fill="#6628a6" opacity=".32" d="M481 17Q334 44 36 178Q262 171 435 69Z"/>
 <path fill="#9952de" d="M481 17Q362 102 36 178Q231 163 449 66Z"/>
 <path fill="#c389f0" d="M481 17Q362 102 36 178Q246 136 440 74Z"/>
 <path fill="#ecd1ff" d="M481 17Q362 102 36 178Q263 111 467 38Z"/>
 <path fill="#401954" d="M452 48Q248 122 58 172Q258 108 452 48Z"/>`;
 // Expanding aperture reveals the arc, then narrow moving slices interrupt it into individual ribbons.
 let clip='',cursor=26;
 const widths=[56,88,47,69,92,100];
 for(let i=0;i<widths.length;i++){
  const x=cursor;cursor+=widths[i];
  const gap=breakup*(4+i%3*4), shift=Math.sin(i*2.5)*breakup*10;
  const top=12+(1-spread)*83,bottom=top+170*spread,left=x+shift,right=left+widths[i]-gap;
  const lean=breakup*(i%2===0?70:-60),kink=breakup*(i%3===0?24:-15);
  clip+=`<path d="M${left} ${top}L${right} ${top} ${right+lean*.4+kink} ${top+75*spread} ${right+lean} ${bottom} ${left+lean} ${bottom} ${left+lean*.4+kink} ${top+75*spread}Z"/>`;
 }
 let b=`<defs><clipPath id="arc">${clip}</clipPath></defs><g clip-path="url(#arc)" opacity="${1-breakup*.9}">${body}
 <path d="${curve}" fill="none" stroke="#fcf1ff" stroke-width="3" stroke-dasharray="33 510" stroke-dashoffset="${-frame*38}"/>
 <path d="${curve}" fill="none" stroke="#a05bdf" stroke-width="7" stroke-dasharray="20 530" stroke-dashoffset="${-frame*38+12}" opacity=".5"/></g>`;
 if(frame<5)b+=`<path d="${curve}" fill="none" stroke="#e7bfff" stroke-width="${1+spread}" stroke-dasharray="${530*spread} 600" opacity="${1-p}"/>`;
 if(p>.4)for(let i=0;i<17;i++){
  const u=i/16,x=475-433*u,y=28+142*u-12*Math.sin(u*Math.PI);
  const travel=breakup*(14+i%5*9),a=breakup*(1-breakup)*3;
  b+=`<path d="M-9 0L5-2 10 0-5 2Z" fill="${i%4===0?'#ecd2ff':'#b67aef'}" opacity="${a}" transform="translate(${x+Math.sin(i*3)*travel} ${y-travel*.5}) rotate(${-22+i%3*8+breakup*17})"/>`;
 }
 return wrap(b,512,192);
}
async function atlas(images,columns,width,height,name){
 const rows=Math.ceil(images.length/columns);
 const inputs=images.map((input,i)=>({input,left:(i%columns)*width,top:Math.floor(i/columns)*height}));
 await sharp({create:{width:columns*width,height:rows*height,channels:4,background:'#00000000'}})
  .composite(inputs).png().toFile(path.join(out,name));
}
(async()=>{
 for(const d of ['rift','slash','preview-frames'])await fs.mkdir(path.join(out,d),{recursive:true});
 const rifts=[],slashes=[];
 for(const [type,count,make,images] of [['rift',24,rift,rifts],['slash',16,slash,slashes]]){
  for(let i=0;i<count;i++){
   const source=make(i),stem=path.join(out,type,'frame-'+String(i).padStart(2,'0'));
   await fs.writeFile(stem+'.svg',source);
   const png=await sharp(Buffer.from(source)).png().toBuffer();images.push(png);await fs.writeFile(stem+'.png',png);
  }
 }
 await atlas(rifts,6,128,320,'rift-atlas-candidate.png');
 await atlas(slashes,4,512,192,'slash-atlas-candidate.png');
 const seen=new Set();
 for(let i=0;i<80;i++){
  const t=i/20;
  const ri=Math.min(23,Math.floor(t/3.2*24));
  const si=Math.min(15,Math.max(0,Math.floor((t-.5)/2.5*16)));
  const label=ri<6?'裂纹撕开':ri<15?'流光内吸':ri<20?'破界迸开':'裂隙闭合';
  const slashLabel=si<4?'细线展开':si<8?'刀光扫过':si<14?'斩弧碎裂':'碎光消散';
  const background=wrap(`<rect width="800" height="450" fill="#101c26"/><g font-family="Microsoft YaHei,sans-serif"><text x="30" y="39" font-size="21" fill="#e5d8f6">沉界破斩 · 连续帧美术预览</text><text x="30" y="64" font-size="12" fill="#8195a4">内部流光与形状逐帧变化 · 慢放展示 · 尚未替换游戏资源</text><text x="79" y="419" font-size="16" fill="#bd91e9">${label}</text><text x="405" y="419" font-size="16" fill="#bd91e9">${slashLabel}</text></g>`,800,450);
  const file=path.join(out,'preview-frames','frame-'+String(i).padStart(3,'0')+'.png');
  await sharp(Buffer.from(background)).composite([{input:rifts[ri],left:97,top:83},{input:slashes[si],left:261,top:145}]).png().toFile(file);
  if([10,27,43,57].includes(i))await fs.copyFile(file,path.join(out,`keyframe-${i}.png`));
  seen.add(ri+','+si);
 }
 execFileSync('ffmpeg',['-hide_banner','-loglevel','error','-y','-framerate','20','-i',path.join(out,'preview-frames/frame-%03d.png'),'-filter_complex','split[a][b];[a]palettegen=stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=3','-loop','0',path.join(out,'animated-effects-preview.gif')]);
 // Corner transparency, consistent dimensions and non-identical frames are checked on exported PNGs.
 for(const [frames,w,h] of [[rifts,128,320],[slashes,512,192]]){
  for(const png of frames){const {data,info}=await sharp(png).ensureAlpha().raw().toBuffer({resolveWithObject:true});
   if(info.width!==w||info.height!==h||data[3]!==0)throw new Error('Invalid fixed-canvas transparent frame');}
  if(new Set(frames.map(p=>p.toString('base64'))).size<frames.length-1)throw new Error('Sequence contains repeated static frames');
 }
 await fs.writeFile(path.join(out,'manifest.json'),JSON.stringify({reviewOnly:true,rift:{frames:24,width:128,height:320,columns:6},slash:{frames:16,width:512,height:192,columns:4},previewFps:20,previewSlowMotion:true,runtimeModified:false},null,2));
 console.log('Animated SVG art PASS: 24 rift frames, 16 slash frames, transparent fixed canvases, two candidate atlases, 20fps review GIF. Runtime unchanged.');
})().catch(e=>{console.error(e);process.exitCode=1});
