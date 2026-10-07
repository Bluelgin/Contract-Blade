(() => {
const fs=require('fs'),source='E:/Little maid/art/akatsuki/midnight-b-full-robe/akatsuki-b-full-robe.bbmodel',dir='E:/Little maid/art/akatsuki/midnight-b-optimized';fs.mkdirSync(dir,{recursive:true});const old=JSON.parse(fs.readFileSync(source,'utf8'));Codecs.project.load(old,{path:source,no_file:true});Modes.options.edit.select();Project.name='赤月 · B 保形性能优化';Project.save_path=dir+'/akatsuki-b-optimized.bbmodel';globalThis.akatsukiOptimizeState={dir,source,old};return {project:Project.name,cubes:Cube.all.length,bones:Group.all.length};
})()
