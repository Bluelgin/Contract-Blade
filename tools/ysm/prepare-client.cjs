// Reuse the installed Forge libraries/assets read-only, with an isolated game directory.
const fs=require('node:fs'), path=require('node:path'), crypto=require('node:crypto');
const root=path.resolve(__dirname,'../..');
const gameDir=path.join(root,'run-ysm-test');
const mc='E:/pcl2/.minecraft', version='1.20.1-Forge_47.4.10';
const versionDir=path.join(mc,'versions',version);
const manifest=JSON.parse(fs.readFileSync(path.join(versionDir,version+'.json'),'utf8'));
const libraryDir=path.join(mc,'libraries');
const nativesDir=path.join(gameDir,'natives');
const required=f=>{if(!fs.existsSync(f))throw Error('Missing installed dependency: '+f);return f;};
function match(rule){
  if(rule.os){if(rule.os.name && rule.os.name!=='windows') return false;
    if(rule.os.arch && !['amd64','x86_64'].includes(rule.os.arch))return false;
    if(rule.os.version && !new RegExp(rule.os.version).test('10.0'))return false;}
  if(rule.features && Object.values(rule.features).some(Boolean))return false;
  return true;
}
function permitted(item){let allowed=!item.rules;for(const r of item.rules??[])if(match(r))allowed=r.action==='allow';return allowed;}
function mavenPath(name){const [group,artifact,v,classifier]=name.split(':');return group.replaceAll('.','/')+'/'+artifact+'/'+v+'/'+artifact+'-'+v+(classifier?'-'+classifier:'')+'.jar';}
const cp=manifest.libraries.filter(permitted).filter(l=>l.downloads?.artifact||!l.natives).map(l=>required(path.join(libraryDir,l.downloads?.artifact?.path??mavenPath(l.name))));
cp.push(required(path.join(versionDir,version+'.jar')));
fs.mkdirSync(nativesDir,{recursive:true});
fs.cpSync(required(path.join(versionDir,version+'-natives')),nativesDir,{recursive:true});
fs.mkdirSync(path.join(gameDir,'mods'),{recursive:true});
const ysm='ysm-2.6.5-forge+mc1.20.1-release.jar';
const ysmSource=required(path.join(root,'tmp/ysm-test',ysm));
const ysmHash=crypto.createHash('sha512').update(fs.readFileSync(ysmSource)).digest('hex');
if(ysmHash!=='f3b2c5e7a6928582c7af58c1011a7347558af221389d32d3727af93cdf21f8971de634be3ce62a03859f60f6272f767e037b5ae22918d6dcec37548f56664aa7')throw Error('Official YSM download checksum mismatch');
fs.copyFileSync(ysmSource,path.join(gameDir,'mods',ysm));
const username='FoxYSMTest';
const hash=crypto.createHash('md5').update('OfflinePlayer:'+username).digest();hash[6]=(hash[6]&15)|48;hash[8]=(hash[8]&63)|128;
const fields={auth_player_name:username,version_name:version,game_directory:gameDir,assets_root:required(path.join(mc,'assets')),assets_index_name:manifest.assetIndex.id,
  auth_uuid:hash.toString('hex'),auth_access_token:'0',clientid:'0',auth_xuid:'0',user_type:'legacy',version_type:'release',
  natives_directory:nativesDir,launcher_name:'ContractFox-YSM-LocalTest',launcher_version:'1.0',classpath:cp.join(';'),classpath_separator:';',library_directory:libraryDir};
required(path.join(mc,'assets/indexes',manifest.assetIndex.id+'.json'));
const substitute=s=>s.replace(/\$\{([^}]+)\}/g,(_,k)=>{if(!(k in fields))throw Error('Unknown launch field: '+k);return fields[k];});
function expand(items){return items.flatMap(x=>typeof x==='string'?[substitute(x)]:permitted(x)?[x.value].flat().map(substitute):[]);}
const args=['-Xms1G','-Xmx3G',...expand(manifest.arguments.jvm),manifest.mainClass,...expand(manifest.arguments.game),'--width','1280','--height','800'];
fs.mkdirSync(path.join(gameDir,'launcher'),{recursive:true});
const argFile=path.join(gameDir,'launcher/java-args.txt');
// Java argument-file quoting protects paths containing spaces and never invokes a shell.
fs.writeFileSync(argFile,args.map(s=>'"'+s.replaceAll('\\','\\\\').replaceAll('"','\\"')+'"').join('\n')+'\n');
const summary={minecraft:'1.20.1',forge:'47.4.10',ysm:'2.6.5',gameDir,libraries:cp.length,isolatedMods:fs.readdirSync(path.join(gameDir,'mods')).filter(n=>n.endsWith('.jar')).sort(),offlineLocalTestIdentity:username,argFile};
fs.writeFileSync(path.join(gameDir,'launcher/environment.json'),JSON.stringify(summary,null,2));
console.log(JSON.stringify(summary,null,2));
