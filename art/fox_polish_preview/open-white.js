(() => {
 const fs = require('fs');
 const source = 'E:/刃录/art/characters/white_fox/fox_white.bbmodel';
 Codecs.project.load(JSON.parse(fs.readFileSync(source, 'utf8')), {path:'E:/Little maid/art/fox_polish_preview/fox_white_polished.bbmodel', no_file:true});
 Project.name = '白狐 · 发色优化预览';
 return 'Opened isolated source copy; original untouched';
})()
