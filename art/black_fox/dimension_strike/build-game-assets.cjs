// Rasterize the editable SVG art; the game uses ordinary transparent textures, not SVG/GIF playback.
const sharp = require('sharp');
const path = require('node:path');
const fs = require('node:fs/promises');
(async()=>{
 const target = path.resolve(__dirname,'../../../src/main/resources/assets/maid_weapon/textures/effect');
 await fs.mkdir(target,{recursive:true});
 for(const [source,name,width,height] of [
  ['rift-transparent.svg','black_fox_rift',128,320],
  ['slash-transparent.svg','black_fox_great_slash',512,192]
 ]) await sharp(path.join(__dirname,source)).trim().resize(width,height,{fit:'contain',background:'#00000000'})
   .png().toFile(path.join(target,name+'.png'));
 const labels=Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="800" height="450"><rect width="800" height="450" fill="#101c26"/><g fill="#e5d8f6" font-family="Microsoft YaHei,sans-serif"><text x="32" y="42" font-size="21">沉界破斩 · 游戏特效贴图</text><text x="62" y="420" font-size="17">次元裂隙</text><text x="350" y="420" font-size="17">破界大斩</text><text x="32" y="67" font-size="12" fill="#8394a4">透明贴图 / 独立绘制 / 展开、蓄光与消散由统一时间轴控制</text></g></svg>`);
 await sharp(labels).composite([
  {input:await sharp(path.join(target,'black_fox_rift.png')).resize(116,290).toBuffer(),left:76,top:92},
  {input:await sharp(path.join(target,'black_fox_great_slash.png')).resize(512,192).toBuffer(),left:255,top:155}
 ]).png().toFile(path.join(__dirname,'game-assets-preview.png'));
 console.log('Dimension strike: two transparent runtime textures exported from SVG.');
})().catch(e=>{console.error(e);process.exitCode=1});
