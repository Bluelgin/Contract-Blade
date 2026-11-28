# 美术、模型包与分发链路

审计基准和 JAR 哈希见 [README](README.md)。下表补足逐文件 [ASSET_INVENTORY.csv](ASSET_INVENTORY.csv) 的运行链；所有“未迁入”均指当前 #38 生产 JAR，不包括隔离实验临时复制的资源。

## 当前分发资源

|类别/来源与资源 ID|master 运行时引用与加载链|许可记录|当前状态、具体缺口|验收方法|
|---|---|---|---|---|
|`assets/maid_weapon/models/item/*`、`textures/item/*`；正式 ModItems 各 ID、猫/狗刀、武器、饰品、钥匙|`ModItems`→注册物品→模型 JSON→PNG；特殊持物/技能另由 renderer/compat 控制|`ASSET_SOURCES.md` 对应原创图标段、根 LICENSE；不能由代码 MIT 推断第三方贴图权限|未迁入正式注册和资源；开发 vanilla 贴图模型不能代替原版设计|生存配方获得；背包、手持、丢出、第三人称与装备投影逐项无紫黑、UV/透明/尺寸正确|
|十份 `.png.mcmeta` 动态条；初醒/流转/无尽灵脉、无尽契约、辅助饰品和空间钥匙|item 模型→block/item atlas 动态纹理；例如 `maid_weapon:item/awakening_vein`，32×1280，40 帧、每帧2tick|`art/resonance`、`art/contract_refresh/v2` 与 `ASSET_SOURCES.md`；保留原导出来源|生产未迁入；隔离原型两版本已实际显示初醒动画，不代表其他九份也验证|初醒完整4秒周期；80帧条完整8秒周期，逐帧顺序/尺寸/透明边、资源重载、低帧率下观感|
|`blockstates/maid_injector.json`、`models/block/*`、对应贴图与 GUI|`MaidInjectorBlock`→`MaidInjectorMenu`→`MaidInjectorScreen`；原方块不含 BlockEntity|项目资源来源记录|未迁入 block/menu/screen/recipe/权限链；不能只复制方块模型|放置/旋转/破坏、正式开屏、物品槽/提示、窗口缩放、关闭/掉线退款|
|`textures/gui/*`；包括 `maid_weapon:textures/gui/danmaku/wheel_base.png`（256²）|`DanmakuWheelScreen`、HUD/图标及代码绘图；轮盘素材由输入/选择状态驱动|`art/danmaku`、`art/spellcards`、导出工具与 ASSET_SOURCES|轮盘和正式 HUD 未移植；原型只显示静态轮盘底图，没有选择、输入或技能|改键/长按/松开/取消、四方向/符式图标、费用/冷却、UI缩放、网络拒绝和失效清理|
|`modelpacks/contract-fox-1.0.1.zip`；包内 `maid_model.json`，ID `contract_fox:fox_white`、`contract_fox:fox_black`，model/texture/animation|`FoxModelPackBootstrap` 安装→`tlm_custom_pack`→TLM loader→实体 modelId；每狐含 main/tac/iss/im/special/tails 六动作文件；29个内部文件逐项列 CSV|包内 credits/license 和 `licenses/fox_models`，CC BY-NC-SA 4.0 来源/修改记录；不是额外商业许可|生产未打包/安装/赋 modelId；native 白狐 initializer 目前空操作。两版本原包真实加载、基础模型/尾巴绘制及重载已观察|正式获得后实际服务器女仆赋模型；走跑/攻击/飞行/装备/技能和完全显现，各可选前置动作；不覆盖用户修改包、失败诊断、首次安装与升级|
|`geo/black_fox_boss*`、`animations/black_fox_{boss,combo_b,iaido}*`、`textures/entity/black_fox_boss*`|Boss renderer/model、状态/动画控制器；SlashBlade 动作与实体战斗状态驱动，不是 TLM 包自动替代|Boss 资产 CC BY-NC-SA 4.0 来源记录；combo_b/iaido 含 SlashBlade MIT 动作及修改，`licenses/black_fox`|未迁入 Boss/entity/控制器/渲染；本轮未在真实战斗重玩所有动作|正常挑战召唤、阶段/连段/攻击窗口、刀身挂点与插值、服务端命中时机和客户端动作一致|
|`shaders/core/black_fox_energy.{json,vsh,fsh}` 与 `textures/effect/black_fox_rift_energy.png`（224×416）|`BlackFoxEnergyShader`→原 RenderType/能量 painter→顶点年龄/UV、纹理、混合/深度/剔除状态；GLSL150|项目 Shader 代码按根 MIT；纹理仍按 ASSET_SOURCES 来源，不混同许可|原 GLSL 字节不变，在1.21.1 native ShaderInstance/新 BufferBuilder 中已编译/绘制/重载；完整 Boss/世界演出未迁入|镜头移动、遮挡/半透明叠加、亮度/深度/双面/性能、多效果并存及重载；原型直绘不能直接作生产 Renderer|
|`particles/black_fox_parry.json`、对应贴图与程序化粒子|`black_fox_parry` 注册与客户端 provider；大量其他效果由 Java 画线、圆环、雨、魂核/幻影剑等|项目来源与具体贴图记录|原生 particle/provider、程序化 renderer、触发包未迁入；详见 PROCEDURAL_VISUALS.csv|实战事件触发、距离/寿命/上限、插值/碰撞、多人只同步必要数据；不能用 END_ROD 当最终演出|
|声音/语音：`sounds.json` 为 `{}`，原版 SoundEvents、TLM 模型音源和 `TlmPecoVoiceBridge`|客户端注册、voice policy/bridge 与技能事件→TLM/原版音源；不等于“旧版无声音”|依赖自身许可与 ASSET_SOURCES/音源记录；不擅自导出依赖全部音频|生产触发桥/反馈未完整移植；本轮两客户端 SoundSystem 无设备，**没有试听通过**|有音频设备的客户端验收播放/音量类别/距离/打断/重复触发、可选前置缺失及重载；原型仅能验证部分加载日志|
|Patchouli `patchouli_books/*`、中英文 `lang/*`、图标与物品说明|`ContractHandbookService` 与书籍/条目、进度/配方解锁→依赖 Patchouli 页面渲染|项目文本与所引用美术按各自来源|正式手册/本地化未迁入；开发语言不是完整译文；Patchouli 对应平台版本要固定测试|首次引导、每页可打开、图标/配方/跨页链接/进度匹配当前玩法；中英文和缺依赖退路|
|`data/maid_weapon/{dimension,dimension_type,worldgen,structures}`、`shinkitsu_shrine.nbt` 与生成式建筑|世界生成/结构注册、`ContractInteriorBuilder`/interior/home life→空间实例、居民、家具/装饰|项目结构与构建代码；内嵌物品/方块受依赖许可与可用性约束|未迁入；内部空间大量布局由 Java 生成，不是单个 NBT；新版本目录/Codec/维度机制须适配|真实新世界进入/离开、建筑/光照/居民/家具、多玩家权限、重启/安全落点、无玩家区块卸载|
|配方、tags、loot、advancement、三份成长 JSON、pack.mcmeta、logo与依赖声明|注册与数据加载→获得/解锁→手册/tooltip；元数据决定可装载范围|项目数据；logo来源见 art/mod_list_logo、ASSET_SOURCES|只有三份成长 JSON 已共享并进入 Neo JAR，且与 master 字节一致；这不授予黑狐/赤月能力；其余未正式适配|1.21.1 单数数据目录及各 Codec、配方实际可用、tag解析、进度/书籍提示、打包范围和启动校验|

## 代码生成的视觉不能遗漏

`PROCEDURAL_VISUALS.csv` 列出了客户端/兼容中的 Visual、Renderer、Painter、Wheel、Atmosphere、Camera、Particle、Shader、Voice、Screen、Overlay 等候选实现及哈希。黑狐能量剑/刀光/招架、赤月领域/雨/魂核/蚀轨、万华弹幕轨迹、HUD和镜头，大量视觉由顶点与状态机生成，复制 assets 无法移植。该表是候选索引，最终仍要沿事件/网络/实体渲染注册追踪实际可达性。

## 历史、撤下与不应擅自恢复的内容

|来源|当前分发事实|处理|
|---|---|---|
|`contract-fox-1.0.0.zip`|master `processResources` 明确排除，旧重复包|保留历史；只迁移当前1.0.1包。托管安装文件名仍含1.0.0是旧路径约定，不能因此再打包旧内容|
|`contract-akatsuki-1.0.0.zip`、`art/akatsuki`、YSM Akatsuki 源|独立赤月包不在当前 JAR；旧 ASSET_SOURCES/Bootstrap 仍有曾经捆绑描述，与后续清理/构建不一致|按实际 JAR 和 art/README 的“不随正式包提供”处理；迁移赤月玩法不自动恢复该外观；修正冲突文档/不可达加载路径是后续任务|
|`sounds/black_fox/epicbattle_j_*.ogg`、`licenses/black_fox_music`、art 音乐与 retired-runtime|明确撤下且排除，ASSET_SOURCES 1.2.0 cleanup 可证|不恢复背景音乐；仅保留来源/许可与历史，不能把它当必补缺口|
|`art/akatsuki/swordplay/retired-20261008`、before/revision/archived-releases、预览帧、bbmodel、YSM导出|源素材/历史或另一格式发布附件，不等于 Minecraft 模组当前运行资源|分别记 AUTHORING_INVENTORY；不整目录塞进 JAR，不从旧图“补回”已撤下刀身/动作|
|`test_slashblade`|master 明确保留的开发物品，用于旧测试存档|保留兼容性决策单列，不能把它当普通玩家入口或新正式武器目标|

`art/resonance/*.svg`、`art/contract_refresh/v2/*.svg`、`art/danmaku`、`art/spellcards` 和导出脚本仍实际存在。检索工具的 ignore 规则可能隐藏素材；本清单使用文件遍历与实际 JAR，而不是把 `rg --files` 没列出误当素材丢失。原始素材不需重绘；跨版本优先复用相同字节、维护确定性导出和必要平台覆盖。

## 许可与需产品决定的事项

许可列记录仓库已有来源与声明，不表示本轮取得新授权。双狐/Boss等第三方衍生美术不是代码 MIT 的覆盖范围；随包保留 CC BY-NC-SA、署名、修改说明及 SlashBlade MIT 动作声明。完整法律文本跟随实际分发资产，不把撤下音乐许可重新加入无关包。

实验可以决定继续使用原 TLM 模型包和原 GLSL 的技术方向，不能决定是否重新分发独立赤月外观、是否恢复退役动作/刀身/音乐、是否支持额外第三方模型格式或改变正式获得设计。这些若要变化须单独产品决定；当前默认保持 master 的实际分发边界与原设计。
