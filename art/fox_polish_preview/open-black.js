(() => {
 const fs=require('fs');
 Codecs.project.load(JSON.parse(fs.readFileSync('E:/刃录/art/characters/black_fox/fox_black.bbmodel','utf8')), {path:'E:/Little maid/art/fox_polish_preview/fox_black_polished.bbmodel',no_file:true});
 Project.name='黑狐 · 发色优化预览';
 return 'Opened isolated black fox copy';
})()
