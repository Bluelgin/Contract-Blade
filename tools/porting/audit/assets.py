#!/usr/bin/env python3
"""Inventory actual distribution entries. Literal references are candidates, not reachability proofs."""
import argparse,csv,hashlib,io,json,re,zipfile
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--master',type=Path,required=True);p.add_argument('--master-jar',type=Path,required=True);p.add_argument('--neo-jar',type=Path,required=True);p.add_argument('--release-jar',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();a.output.mkdir(parents=True,exist_ok=True)
M,N,R=[zipfile.ZipFile(x) for x in (a.master_jar,a.neo_jar,a.release_jar)]
texts=[]
for root in ('src/main/java','src/main/resources'):
 for f in (a.master/root).rglob('*'):
  if f.is_file() and f.suffix in ('.java','.json','.mcmeta'):
   texts.append((str(f.relative_to(a.master)),f.read_text(errors='replace')))
def rid(path):
 parts=path.split('/')
 return parts[1]+':'+ '/'.join(parts[2:]) if len(parts)>2 and parts[0] in ('assets','data') else path

def refs(path):
 id=rid(path);keys={id}
 if path.startswith('assets/'):
  if '/models/' in path:keys.add(id.replace('models/','',1).removesuffix('.json'))
  if '/textures/' in path:keys.add(id.replace('textures/','',1).removesuffix('.png'))
 if path.startswith('modelpacks/'):keys.add(path)
 result=[name for name,t in texts if name!='src/main/resources/'+path and any(k in t for k in keys)]
 return ';'.join(result) or '未找到完整字符串引用；需查动态拼接/框架约定'

def classify(path,shipped):
 if shipped:return '当前 master 分发内容'
 if 'epicbattle_j_' in path or 'black_fox_music' in path:return '已撤下音乐；不自动恢复'
 if path.startswith('modelpacks/'):return '未分发/旧包；不自动恢复'
 return '源文件存在但未分发；须追溯原因'

def criterion(path):
 if path.startswith('modelpacks/'):return 'TLM 模型 ID 可枚举；原版外观/动作实机对照；安装不覆盖用户包'
 if '/textures/' in path:return '无缺失贴图；透明/色彩/UV/像素尺寸对照；动画至少一完整周期'
 if '/shaders/' in path:return '真实 OpenGL 编译/链接与绘制；混合/深度/恢复渲染状态；资源重载'
 if '/animations/' in path or '/geo/' in path:return '骨骼/控制器/动作触发接通；移动/攻击/技能/装备组合实机验证'
 if '/patchouli_books/' in path:return '手册页面可打开；图标/链接/解锁条件与当前玩法一致'
 if '/structures/' in path or '/dimension' in path or '/worldgen/' in path:return '新世界载入；进入/离开/居民/重启与权限；无无界区块常驻'
 return '注册与引用闭合；对应玩家流程及资源重载实机验收'

def license_ref(path):
 if 'black_fox_music' in path or 'epicbattle_j_' in path:return '已撤下；历史 CC BY 4.0 / licenses/black_fox_music/CREDITS.md；不自动恢复'
 if 'contract-fox' in path or 'contract_fox' in path:return '随包来源标注 CC BY-NC-SA 4.0；licenses/fox_models/CREDITS.md + ASSET-LICENSE-NOTICE.md；不是新增授权'
 if 'contract-akatsuki' in path:return 'CC BY-NC-SA 4.0 来源记录；licenses/akatsuki/CREDITS.md；该独立包当前不分发'
 if path.endswith(('black_fox_combo_b.animation.json','black_fox_iaido.animation.json')):return 'SlashBlade 动作 MIT 及项目适配；licenses/black_fox/SlashBlade-animation-MIT.txt + CREDITS.md'
 if any(x in path for x in ('geo/black_fox_boss','textures/entity/black_fox_boss','animations/black_fox_boss')):return 'CC BY-NC-SA 4.0 来源记录；licenses/black_fox/CREDITS.md；保持署名与修改说明'
 if '/shaders/' in path:return '项目 Shader 代码；根 LICENSE(MIT)；所用贴图须按各自来源处理'
 return 'ASSET_SOURCES.md + 根 LICENSE；项目原创/依赖资源须按条目追溯，不能推断所有美术均为 MIT'
rows=[]
resource_root=a.master/'src/main/resources'
for f in sorted(resource_root.rglob('*')):
 if not f.is_file():continue
 name=f.relative_to(resource_root).as_posix();shipped=name in M.namelist();data=f.read_bytes()
 rows.append(dict(source='src/main/resources/'+name,resource_id=rid(name),master_shipped=shipped,release_1_0_6_shipped=name in R.namelist(),neo_shipped=name in N.namelist(),sha256=hashlib.sha256(data).hexdigest(),classification=classify(name,shipped),runtime_reference_candidates=refs(name),license=license_ref(name),status=('不属于当前分发迁移目标' if not shipped else '资源路径已打包；完整功能仍须验收' if name in N.namelist() else '未进入 NeoForge 分发'),gap=('保持排除；如恢复须明确产品决策及许可核验' if not shipped else '需逐项适配加载、注册/执行、网络与客户端渲染' if name not in N.namelist() else '不能以路径存在视为完整实现'),acceptance=criterion(name) if shipped else '保持分发排除；保留源素材与历史来源记录'))
 if name.startswith('modelpacks/') and name.endswith('.zip'):
  with zipfile.ZipFile(io.BytesIO(data)) as z:
   for nested in sorted(z.namelist()):
    if nested.endswith('/'):continue
    rows.append(dict(source='src/main/resources/'+name+'!/'+nested,resource_id=rid(nested),master_shipped=shipped,release_1_0_6_shipped=name in R.namelist(),neo_shipped=name in N.namelist(),sha256=hashlib.sha256(z.read(nested)).hexdigest(),classification=classify(name,shipped),runtime_reference_candidates='FoxModelPackBootstrap -> tlm_custom_pack -> maid_model.json（包内 animation/model/texture 引用）',license=license_ref(name),status='不属于当前分发迁移目标' if not shipped else '模型包未迁入 NeoForge' if name not in N.namelist() else '包存在；需模型 ID 与动画实机验证',gap='保持排除；不擅自恢复独立包/旧包' if not shipped else 'TLM 原生模型加载、动作与装备适配；保留模型 ID/署名与用户覆盖规则',acceptance=criterion(name) if shipped else '保持分发排除；保留历史记录'))
def distributed_hash(archive, source):
 name=source.removeprefix('src/main/resources/')
 outer,separator,nested=name.partition('!/')
 try:
  data=archive.read(outer)
  if separator:
   with zipfile.ZipFile(io.BytesIO(data)) as pack:data=pack.read(nested)
  return hashlib.sha256(data).hexdigest()
 except KeyError:return ''
for row in rows:
 row['master_entry_sha256']=distributed_hash(M,row['source'])
 row['neo_entry_sha256']=distributed_hash(N,row['source'])
 row['release_entry_sha256']=distributed_hash(R,row['source'])
 row['source_matches_master_entry']=row['sha256']==row['master_entry_sha256'] if row['master_entry_sha256'] else ''
with (a.output/'ASSET_INVENTORY.csv').open('w',newline='',encoding='utf-8-sig') as f:
 w=csv.DictWriter(f,fieldnames=list(rows[0]),lineterminator='\n');w.writeheader();w.writerows(rows)
# Authoring and history are inventoried separately and never copied into the runtime pack.
def authoring_class(path):
 name=path.as_posix()
 if 'black_fox/music' in name or '/retired-' in name or '/retired-runtime/' in name:return '已撤下内容/实现留档；不自动恢复'
 if '/akatsuki/' in name or '/contract_akatsuki/' in name:return '赤月源素材；当前独立外观不随正式包提供'
 if any(x in name for x in ('archived-releases','/before','revision-')):return '历史迭代/旧发布留档'
 if any(x in name for x in ('preview','/frames/')):return '预览/导出过程；不是运行资源'
 if '/ysm-test/' in name:return 'YSM 格式源/附件；不是 TLM 模组运行包'
 return '创作源文件/工具或说明；须追溯当前导出'
with (a.output/'AUTHORING_INVENTORY.csv').open('w',newline='',encoding='utf-8-sig') as f:
 w=csv.writer(f,lineterminator='\n');w.writerow(['source','sha256','classification','decision'])
 for path in sorted((a.master/'art').rglob('*')):
  if path.is_file():w.writerow([path.relative_to(a.master),hashlib.sha256(path.read_bytes()).hexdigest(),authoring_class(path.relative_to(a.master)),'结合 art/README.md 追溯当前分发资源；不整目录打包'])
# Generated geometry, screens and voice bridges also require platform adaptation.
with (a.output/'PROCEDURAL_VISUALS.csv').open('w',newline='',encoding='utf-8-sig') as f:
 w=csv.writer(f,lineterminator='\n');w.writerow(['source','sha256','literal_resource_candidates','gap','acceptance'])
 for path in sorted((a.master/'src/main/java').rglob('*.java')):
  relative=path.relative_to(a.master).as_posix()
  if not any(x in relative for x in ('/client/','/compat/')):continue
  if not re.search(r'Visual|Render|Painter|Wheel|Atmosphere|Camera|Particle|Shader|Voice|Screen|Overlay|Textures',path.stem):continue
  body=path.read_text();ids=sorted(set(re.findall(r'"(maid_weapon:[^"\s]+|textures/[^"\s]+|shaders/[^"\s]+)"',body)))
  w.writerow([relative,hashlib.sha256(path.read_bytes()).hexdigest(),';'.join(ids),'需迁移事件/Buffer/RenderType/实体与网络触发；旧源集不进入 NeoForge 编译','参照具体技能/GUI运行链及实机画面；文件存在不等于行为接通'])
meta={str(j):hashlib.sha256(j.read_bytes()).hexdigest() for j in (a.master_jar,a.neo_jar,a.release_jar)}
(a.output/'JAR_PROVENANCE.json').write_text(json.dumps(meta,indent=2)+'\n')
print(f'Wrote {len(rows)} entries (including nested model-pack entries); no completion percentage inferred.')
