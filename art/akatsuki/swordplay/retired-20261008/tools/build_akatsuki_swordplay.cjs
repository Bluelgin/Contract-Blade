// Independent cosmetic assets. Original Akatsuki pack and combat resources are read-only inputs.
const fs = require('node:fs'), path = require('node:path'), zlib = require('node:zlib');
const root = path.resolve(__dirname, '..');
const output = path.join(root, 'src/main/resources/assets/maid_weapon/akatsuki');
fs.mkdirSync(output, {recursive:true});
const source = JSON.parse(fs.readFileSync(path.join(root, 'src/main/resources/assets/maid_weapon/animations/black_fox_combo_b.animation.json')));
const ready = {UpperBody:[0,0,0],RightArm:[-8,0,12],RightForeArm:[-12,0,0],RightHand:[0,0,0],LeftArm:[0,0,0],LeftForeArm:[0,0,0],LeftHand:[0,0,0]};
const touch = {UpperBody:[0,-10,0],RightArm:[-49,-25,-42],RightForeArm:[-64,0,0],RightHand:[0,0,0],LeftArm:[-18,0,12],LeftForeArm:[-35,0,0],LeftHand:[0,0,0]};
const idle = Object.fromEntries(Object.keys(ready).map(name=>[name,[0,0,0]]));
const animations = {};
function frameClip(name, length, frames) {
  const bones = {};
  for (const bone of Object.keys(ready)) bones[bone] = {rotation:Object.fromEntries(frames.map(([time,pose])=>[time,pose[bone]||[0,0,0]]))};
  animations[name] = {animation_length:length,bones};
}
frameClip('ready', .4, [[0,ready],[.4,ready]]);
frameClip('draw', .4, [[0,idle],[.12,touch],[.24,{...touch,RightArm:[-58,-8,-12],RightForeArm:[-15,0,0]}],[.4,ready]]);
frameClip('sheathe', .7, [[0,ready],[.28,touch],[.48,touch],[.7,idle]]);
function sample(track,time) {
  if (Array.isArray(track)) return track;
  const keys=Object.entries(track).map(([t,v])=>[+t,v]).sort((a,b)=>a[0]-b[0]);
  for(let i=1;i<keys.length;i++)if(time<=keys[i][0]){
    const a=keys[i-1],b=keys[i],f=(time-a[0])/(b[0]-a[0]);return a[1].map((v,j)=>v+(b[1][j]-v)*f);
  }
  return keys.at(-1)[1];
}
// Reuse the approved first Combo B cut, not the spinning 360-degree repeats.
// Restrain amplitude for Akatsuki's sleeves; mirror timing/direction for the return cut.
for(let n=1;n<=3;n++){
  const original=source.animations['contract_fox_boss.combo_b1'];
  const frames=[];
  for(let i=0;i<=12;i++){
    const p=i/12, t=n===2?original.animation_length*(1-p):original.animation_length*p;
    const pose={...ready};
    for(const name of ['RightArm','RightForeArm']){
      let v=sample(original.bones[name].rotation,t);
      v=v.map((x,j)=>x*(name==='RightArm'?.55:.8)*(n===2&&j>0?-1:1));
      pose[name]=v;
    }
    pose.UpperBody=[0,sample(original.bones.AllBody.rotation,t)[1]*(n===2?-.3:.3),0];
    if(n===3){pose.RightArm=pose.RightArm.map((v,j)=>v*(j===2?.5:.8));pose.RightForeArm[0]-=8;}
    const settle=Math.max(0,(p-.68)/.32);
    for(const name of Object.keys(pose))pose[name]=pose[name].map((v,j)=>+(v+(ready[name][j]-v)*settle).toFixed(4));
    frames.push([+(p*.4).toFixed(5),pose]);
  }
  frameClip('cut_'+n,.4,frames);
}
fs.writeFileSync(path.join(output,'swordplay.json'),JSON.stringify({format_version:'1.8.0',animations}));
const parts={blade:[],sheath:[]};
const box=(part,from,to,color)=>parts[part].push({from,to,color});
const black='25212d',wine='7f223d',gold='c8a457',steel='87929f',edge='e9e7dc';
box('blade',[-.42,-.8,-.35],[.42,4,.35],black);
for(let i=0;i<6;i++){let y=i*.67;box('blade',[-.46,y,-.39],[.46,y+.32,.39],wine);}
box('blade',[-.5,3.65,-.42],[.5,4,.42],gold);
box('blade',[-1.05,-1.12,-.78],[.7,-.78,.78],gold);
box('blade',[-1.25,-.85,-.55],[-.85,-.2,.55],gold);
for(let i=0;i<5;i++){
  let y=-1.13-i*3.3,x=i*i*.035;
  box('blade',[x-.27,y-3.3,-.15],[x+.27,y,.15],steel);
  box('blade',[x+.27,y-3.3,-.155],[x+.45,y,.155],edge);
}
box('blade',[.65,-18.2,-.1],[1.15,-17.5,.1],edge);
for(let i=0;i<5;i++){
  let y=-1.16-i*3.35,x=i*i*.035;
  box('sheath',[x-.57,y-3.35,-.46],[x+.6,y,.46],black);
}
box('sheath',[-.61,-1.5,-.5],[.65,-1.1,.5],gold);
box('sheath',[-.64,-3.1,-.51],[.68,-2.6,.51],wine);
box('sheath',[.1,-6,-.54],[.36,-3,.54],wine);
box('sheath',[.54,-18.1,-.5],[1.21,-17.55,.5],gold);
const normals=[[0,0,-1],[0,0,1],[-1,0,0],[1,0,0],[0,1,0],[0,-1,0]];
for(const boxes of Object.values(parts))for(const b of boxes){
  b.faceMask=63;
  for(let f=0;f<6;f++){
    const axis=normals[f].findIndex(v=>v!==0),positive=normals[f][axis]>0,plane=positive?b.to[axis]:b.from[axis];
    const covered=boxes.some(other=>other!==b&&other.from.every((v,j)=>j===axis||v<=b.from[j]+1e-6)&&other.to.every((v,j)=>j===axis||v>=b.to[j]-1e-6)
      &&(positive?other.from[axis]<=plane+1e-6&&other.to[axis]>plane+1e-6:other.to[axis]>=plane-1e-6&&other.from[axis]<plane-1e-6));
    if(covered)b.faceMask&=~(1<<f);
  }
}
fs.writeFileSync(path.join(output,'katana.json'),JSON.stringify({units:'model_pixels',axis:'blade points along -Y, hand grip at origin',parts}));
const notices=path.join(root,'src/main/resources/licenses/akatsuki');fs.mkdirSync(notices,{recursive:true});
fs.copyFileSync(path.join(root,'art/slashblade_animations/combo_b/SlashBlade-LICENSE.txt'),path.join(notices,'SlashBlade-animation-MIT.txt'));
// New one-pixel neutral material, not an edit of an existing raster asset.
function crc(buf){let v=-1;for(const b of buf){v^=b;for(let i=0;i<8;i++)v=(v>>>1)^((v&1)?0xedb88320:0);}return (v^-1)>>>0;}
function chunk(type,data){const b=Buffer.concat([Buffer.from(type),data]),h=Buffer.alloc(4),c=Buffer.alloc(4);h.writeUInt32BE(data.length);c.writeUInt32BE(crc(b));return Buffer.concat([h,b,c]);}
const head=Buffer.alloc(13);head.writeUInt32BE(1);head.writeUInt32BE(1,4);head[8]=8;head[9]=6;
fs.writeFileSync(path.join(output,'white.png'),Buffer.concat([Buffer.from('89504e470d0a1a0a','hex'),chunk('IHDR',head),chunk('IDAT',zlib.deflateSync(Buffer.from([0,255,255,255,255]))),chunk('IEND',Buffer.alloc(0))]));
console.log('AKATSUKI_ASSETS_PASS: 6 upper-body clips; '+Object.values(parts).flat().length+' cosmetic cubes; original body/tail untouched');
