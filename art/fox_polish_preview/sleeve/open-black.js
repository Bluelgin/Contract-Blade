(() => {
 Codecs.project.load(JSON.parse(require('fs').readFileSync('E:/Little maid/art/fox_polish_preview/fox_black_polished.bbmodel','utf8')), {path:'E:/Little maid/art/fox_polish_preview/sleeve/fox_black_sleeve.bbmodel',no_file:true});
 Project.name='黑狐 · 袖子晕染样片';
 return 'Opened isolated black sleeve sample';
})()
