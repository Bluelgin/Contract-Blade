const fs=require('node:fs'),path=require('node:path'),cp=require('node:child_process');
const root=path.resolve(__dirname,'..'),out=path.join(root,'build/akatsuki-rig-validation');fs.mkdirSync(out,{recursive:true});
let nativeClasspath=process.argv[2]||process.env.AKATSUKI_VALIDATOR_CLASSPATH;
const cachedArgs=path.join(root,'tools/tlm-validator/bladesmith-runtime.args');
if(!nativeClasspath&&fs.existsSync(cachedArgs)){
  const quoted=fs.readFileSync(cachedArgs,'utf8').match(/(?:-cp|-classpath|--class-path)\s+"([^"]+)"/);
  if(quoted)nativeClasspath=quoted[1].replaceAll('\\\\','\\');
}
if(!nativeClasspath)throw new Error('Provide the mapped TLM/Minecraft classpath as an argument or AKATSUKI_VALIDATOR_CLASSPATH; local runtime caches are not published.');
const classpath=path.join(root,'build/classes/java/main')+path.delimiter+nativeClasspath;
for(const [exe,args] of [['javac',['-encoding','UTF-8','-cp',classpath,'-d',out,path.join(root,'tools/tlm-validator/AkatsukiRigValidator.java')]],
  ['java',['-cp',out+path.delimiter+classpath,'com.maidweapon.forge.compat.akatsuki.AkatsukiRigValidator',root]]]){
  const result=cp.spawnSync(exe,args,{cwd:root,encoding:'utf8'});process.stdout.write(result.stdout||'');process.stderr.write(result.stderr||'');if(result.status!==0)process.exit(result.status||1);
}
