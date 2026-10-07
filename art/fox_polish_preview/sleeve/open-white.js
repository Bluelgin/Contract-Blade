(() => {
 Codecs.project.load(JSON.parse(require('fs').readFileSync('E:/Little maid/art/fox_polish_preview/fox_white_polished.bbmodel','utf8')), {path:'E:/Little maid/art/fox_polish_preview/sleeve/fox_white_sleeve.bbmodel',no_file:true});
 Project.name='白狐 · 袖子晕染样片';
 return 'Opened isolated white sleeve sample';
})()
