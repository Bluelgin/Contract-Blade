// Copy only runtime resources into a clean, player-facing release staging tree.
const fs=require('node:fs');
const path=require('node:path');
const assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../..');
const version='1.1.0';
const output=path.join(root,'dist/ysm',version);
const stage=path.join(output,'models');
const read=f=>JSON.parse(fs.readFileSync(f,'utf8'));
const write=(f,text)=>{fs.mkdirSync(path.dirname(f),{recursive:true});fs.writeFileSync(f,text);};
const flight=read(path.join(root,'art/ysm-test/taisho-flight-source.json'));
const template=read(path.join(root,'tmp/ysm-test/default/ysm.json')).metadata;
const players=read(path.join(root,'art/ysm-test/models/contract_white_fox/ysm.json')).metadata.authors.slice(0,2);
const instructions=`# 安装与使用

适用：Minecraft Java 1.20.1、Forge、Yes Steve Model（YSM）2.6.5。
这是玩家外观模型，不是女仆资源包，也不包含黑狐 Boss 的战斗技能。无需安装契约之刃或车万女仆。

## 单人游戏

1. 安装对应版本的 YSM，并启动游戏一次。
2. 把本模型 ZIP 直接放进游戏目录的 config/yes_steve_model/custom 文件夹。不要放进 mods，也不要重复安装同名模型的文件夹和 ZIP。
3. 重启游戏并进入世界。按 Alt + Y 打开模型选择菜单，选中角色；F5 可切换第三人称。
4. 已开启作弊时，也可以使用 /ysm model reload 重新加载，再打开菜单选择模型。首次加载请稍等片刻。

## 多人游戏

请由服主管理安装：客户端和服务端均安装匹配版本的 YSM，模型 ZIP 放入服务端同一位置的 custom 文件夹。由管理员执行 /ysm model reload，并等待同步后选择模型。只安装在自己客户端不能保证其他玩家看得到。

## 模型特色

- 赤月：粉白发色与同色狐尾，酒红叠裙搭配浅色内裙，保留原袜子。
- 白狐、黑狐：三条尾巴在走跑、转身和停步时有柔和的跟随感。
- 三款角色均有旋转尾巴的普通飞行动作，并提供第一人称手臂。
- 普通飞行与鞘翅飞行是不同动作；旋转尾巴对应普通飞行。

## 常见问题

找不到模型：确认 ZIP 位置、YSM 版本和是否存在重复安装；重启后等待模型加载完成。Alt + Y 无反应时，请检查控制设置中的快捷键冲突。
旧版本更新：先移走同名旧包或旧文件夹，再安装新版，避免重复。请保留自己的备份。
衣服或尾巴偶尔穿插：长发、长裙和三尾在极端姿势或其他模组动作下可能出现穿插。反馈时请说明角色、动作、游戏/YSM 版本，并附截图。

## 使用许可

模型及本次外观、动画改造按 CC BY-NC-SA 4.0 提供：可用于非商业用途；转载和修改请保留署名、许可及修改说明，衍生版本使用同一许可。详细来源见 CREDITS.md，完整条款见 LICENSE-CC-BY-NC-SA-4.0.txt。
YSM 默认基础动作原本为 CC0；这不改变角色模型和大正酒狐飞行动作的非商业许可。

官方帮助：https://yesstevemodel.github.io/wiki/faq/
许可说明：https://creativecommons.org/licenses/by-nc-sa/4.0/deed.zh-hans
`;
const rows=[];
for(const [id,name,description] of [
  ['contract_akatsuki','赤月 · 粉白','粉白长发与同色狐尾，奶白上衣搭配酒红叠裙与浅色裙褶。'],
  ['contract_white_fox','白狐','银白长发、朱色结饰与三条柔软的狐尾。'],
  ['contract_black_fox','黑狐','墨发、紫衣与三条柔软的狐尾，静静守望归途。']
]){
  const source=path.join(root,'art/ysm-test/models',id),dest=path.join(stage,id);
  // Refuse a dirty stage instead of accidentally shipping old development files.
  assert(!fs.existsSync(dest),`Release stage already exists: ${dest}`);
  const json=read(path.join(source,'ysm.json'));
  const files=json.files.player;
  const resources=[...Object.values(files.model),...Object.values(files.animation),...files.texture];
  for(const resource of resources){
    assert(!resource.includes('..')&&!path.isAbsolute(resource));
    const target=path.join(dest,resource);fs.mkdirSync(path.dirname(target),{recursive:true});
    fs.copyFileSync(path.join(source,resource),target);
  }
  json.metadata={name,tips:`${description}\n旋转尾巴飞行 · ${version}\n玩家外观模型，不包含 Boss 技能。`,
    authors:[...players,...template.authors.map(a=>({name:a.name,role:`YSM 基础动作 · ${a.role}`})),
      ...flight.metadata.authors.map(a=>({name:a.name,role:`大正酒狐来源 · ${a.role}`}))],
    license:{type:'CC BY-NC-SA 4.0',desc:'非商业使用；转载保留署名，修改版采用相同许可。完整来源与条款随包附带。'}};
  write(path.join(dest,'ysm.json'),JSON.stringify(json,null,2)+'\n');
  write(path.join(dest,'README.md'),`# ${name} · YSM ${version}\n\n${description}\n\n`+instructions);
  const credits=`# ${name} · 来源与署名

## 角色模型

原 Wine Fox / 酒狐角色与模型：TartaricAcid / 酒石酸菌及 Touhou Little Maid 模型包贡献者。
赤月大正酒狐 1.2.3 来源适配：Blade Tetra contributors。
项目改造：契约之刃（Contract Blade）；赤月粉白造型、黑白狐配色与三尾、服装细化和玩家动作适配。
上游来源：https://github.com/TartaricAcid/TouhouLittleMaid

此前改造包括人形及狐形配色、三尾、服装与饰件贴合、移除月冠，以及贴图整理。本 YSM 版本仅使用人形；保留当前外观，增加玩家状态、第一人称手臂及走跑尾巴跟随。没有附带女仆、Boss 逻辑或战斗音乐。
${id==='contract_akatsuki'?'1.1.0 外观更新：酒红立体叠裙、暖粉白内褶裙、加厚金白包边、赤金结饰及贴合衣料的垂绳；奶白上衣保留原设计。粉白发尾、脸、袜鞋及旋转尾巴飞行动作保留。':'1.1.0 合集打包：黑白狐外观与动作保持上一发布版不变。'}
${id==='contract_akatsuki'?'赤月改造版本的 CC BY-NC-SA 4.0 发布许可由本项目角色包提供者确认；原始来源与署名继续保留。':'黑白狐来源包附带 CC BY-NC-SA 4.0 许可；本次衍生继续遵守该许可。'}

## YSM 基础动作（CC0）

来源：Yes Steve Model 2.6.5 随附的 default 模型。原作者声明为 CC0；本包按角色骨架适配。
${template.authors.map(a=>`- ${a.name}：${a.role}`).join('\n')}

## 旋转尾巴飞行动作（CC BY-NC-SA 4.0）

来源：Yes Steve Model 2.6.5 随附的 Wine Fox / 大正酒狐（01_taisho_maid）。只使用普通飞行动作；黑白狐另适配三尾旋转方向和错相。不包含原包音乐、载具、声音或其他外观。
完整原包署名保留如下（角色标注为原包所载职责，不表示所有人均参与本次改造）：

${flight.metadata.authors.map(a=>`- ${a.name}：${a.role}${a.contact?'；'+Object.entries(a.contact).map(([label,url])=>`${label}: ${url}`).join('；'):''}`).join('\n')}

YSM 项目与文档：https://yesstevemodel.github.io/

## 许可与转载

角色模型、贴图及本次衍生动画：CC BY-NC-SA 4.0。请保留本署名、许可链接及修改记录；仅限非商业使用，衍生作品采用同一许可。上游的 CC0 基础动作仍保留其原有许可。
许可：https://creativecommons.org/licenses/by-nc-sa/4.0/
本项目不代表原作者背书。不附带商业授权；第三方资源不适用契约之刃的代码 MIT 许可。
`;
  write(path.join(dest,'CREDITS.md'),credits);
  fs.copyFileSync(path.join(source,'LICENSE-CC-BY-NC-SA-4.0.txt'),path.join(dest,'LICENSE-CC-BY-NC-SA-4.0.txt'));
  rows.push({id,name,version,resources:resources.length,files:[...resources,'ysm.json','README.md','CREDITS.md','LICENSE-CC-BY-NC-SA-4.0.txt']});
}
write(path.join(output,'README.md'),`# 三狐 YSM 模型 · ${version}\n\n合集 ZIP 需要先解压，再将里面的三个角色 ZIP 放入 config/yes_steve_model/custom；不要把合集 ZIP 整体放进去。每个角色 ZIP 是可单独安装的模型，也可同时安装三款。重启后即可选择。详细安装与许可说明位于各包 README.md 和 CREDITS.md。\n\n`+instructions);
write(path.join(root,'tmp/ysm-release-manifest.json'),JSON.stringify(rows,null,2));
console.log(JSON.stringify(rows.map(({files,...row})=>row),null,2));
