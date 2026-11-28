# 正式玩家流程与功能缺口审计

日期：2026-11-28（Asia/Shanghai）。源码基准 master `d1429a4`、NeoForge #38 `2955432`、Forge #39 `c31d2bf`。本表以实际源码及构建边界为准；本轮隔离视觉实验不能改变生产分支的功能状态。`src/main` 在 NeoForge 工作树中保留，但不参与新平台编译。

状态沿用 PARITY_MATRIX：完整 / 核心已实现但未验证 / 部分 / 未移植 / 外部阻塞 / 经确认退役。下列生产玩家流程没有任何一项因截图或编译成功直接晋升“完整”。旧版“有实现”也不表示本轮已逐项重玩。

表中旧版类以 `src/main/java/com/maidweapon/forge/` 为根；新平台以 `src/port/java/com/maidweapon/port/` 为根。每条链的最后一栏是还需补齐的验收，不是已经通过的测试。

|玩家流程|入口→执行器（master）|持久化/网络（master）|渲染/资源与依赖|NeoForge 当前状态及证据|下一项验收/缺口|
|---|---|---|---|---|---|
|获得正式契约武器和饰品|`init/ModItems`、配方、创造标签→各 Item 交互|物品 NBT、契约绑定与饰品权限|原 item 模型、贴图/动画条与语言；配方/进度|**未移植正式入口**；`init/ProbeItems` 只注册开发物品，未建立正式 `maid_weapon` 产品注册|普通玩家从配方获取后可进入全部链路，不能依赖 give/测试水晶|
|使用缔结台|`block/MaidInjectorBlock`→`menu/MaidInjectorMenu`→契约/剑灵服务|Forge 开屏与菜单槽校验；服务端结算|`client/MaidInjectorScreen`；方块模型、贴图与文本|**未移植**；无对应 NeoForge block/menu/screen 注册|开屏、槽位/距离/所有权、关闭和掉线时退还物品；正式资源显示。旧方块是普通 Block，不凭空新增 BlockEntity 架构|
|捕获自有普通女仆|旧交互→`ContractLifecycleService.capture`|`ContractCarrierData`、MaidStorage/恢复日志|TLM 实体序列化、物品提示|**部分**；`ProbeContractCarrierItem`→`PortContractLifecycleTrial`；typed components、attachment、`NativeContractStore`/journal；原生与先前客户端证据存在|正式武器/缔结流程接入；背包/装备/主人/工作状态全样本与拒绝路径|
|显现、归刃与身份连续性|旧交互→lifecycle / deployment|旧绑定、实体索引与恢复记录|TLM 实体、HUD/交互提示|**部分**；普通开发载体在 useOn 前经校验转换 channels，live 引用保留，use 空气归刃；`PORT_NATIVE_PLAYER_CARRIER_PASS` 验证余额 875、身份与旧票拒绝|正式玩家入口、范围与操作习惯；多种世界/权限/客户端组合。实际物品 fixture 不等于所有正式玩法|
|多通道选择/切换|`ContractChannelStorage`、内置剑灵通道→交互/授权|分通道身份/快照/成长|通道 UI、按键和提示|**部分**；`NativeChannelService` 支持封存选择及 live lease，显现中拒绝切换；开发物品循环|正式通道选择 UI、退出/换世界缓存失效；不能靠开发指令或水晶代替|
|解绑、迁居、重新绑定|`ContractInteractionService`、`FoxSpiritTransferService`→权限/转移|契约/剑灵账本与迁移事务|魂符/缔结 UI、饰品和说明|**未移植完整流程**；native grant/通道不是迁居实现|所有权转移、旧载体失效、成长与余额继承、重复请求幂等|
|武器遗失、紧急恢复、重生|`ContractCarrierLossService`、`ContractRecoveryService`、`ContractMaidRebirthBridge`|旧恢复/遗失日志与快照|恢复物品/提示及 TLM 重生接口|**部分底层/玩法未完**；native diagnostics 与 LIVE acknowledge 只接受可证明的已加载实体，不生成恢复实体|玩家正常操作入口、旧副本与重复女仆防护、死亡/退出/跨维度、余额成长保留|
|正常存档重启|旧存储与 native restart fixtures|独立 SavedData、原生世界/玩家保存|载体及 TLM 数据重新装载|**部分**；#38 两进程正常停服/重启 PASS；`NativeCheckpointService` 调用 saveEverything 后标记 STORED checkpoint|扩大到真实玩家背包/掉落物/多个维度与多女仆。正常重启不证明 SIGKILL/断电安全|
|异常退出/未加载实体|旧恢复 journal→恢复服务|跨文件事务与实体加载证据|诊断与隔离反馈|**部分**；证据不足拒绝操作；`NativeLiveReconciliation` 不把未加载当作不存在；仍有 operator 级诊断|崩溃各阶段负向夹具、玩家可诊断隔离状态、无凭证不得补生成实体|
|战斗随行、工作、安全喂食|`ContractCompanionService`、`ContractCombatTaskRouter`、`MaidCareTaskSystem`|部署/协同状态、实体任务及 companion network|TLM task/AI、对话、装备及动作|**未移植完整链**；原生生成一个 TLM 实体不能证明协同/任务等价|原版任务切换、工作日程、护主、退出/跨维度、多人上限与安全回收|
|装备与武器投影|`ContractEquipmentProjection`、projection/transfer guard、相关 Mixin|原装备恢复、掉落/背包权限与身份|TLM 装备/武器渲染及第三方持物动画|**未移植**；TLM snapshot roundtrip 覆盖保存，不等于实时投影/恢复服务|实穿/投影物品隔离、死亡/菜单/掉落/弹药/可选 Mod 组合|
|共鸣支付、恢复、战斗奖励|`ResonanceService`、recovery/activity/credit→成本策略|pool/progress SavedData、尝试收据、network|HUD、技能/恢复反馈|**部分**；`NativeResonanceService`、`NativeRecoveryTick`、`NativeCombatCredits` 已有原生路径；fixture/共享测试通过|旧可配置值完整适配；远程及第三方击杀/协同归因、全部技能真实消费；不能把 DEFAULT 当用户配置|
|初醒/流转/无尽及饰品|升级物品→progress/bauble resolver|玩家全局进度与 TLM 原生槽|四套动态图标、饰品权限/UI/配方|**部分**；开发升级物品与 `NativeBaubleCapabilities/Extension`；正式资源/配方未进包|全部旧饰品效果、重复槽/权限、升级不可跳级、不覆盖旧配置；原有动画完整周期|
|成长与技能解锁|`ContractMasteryData`、ability reload→执行服务|mastery 与能力定义|成长/tooltip、解锁与反馈|**部分**；`NativeMasteryData` 和三份共享成长 JSON 有解析/边界/加载基础；black/red provider 明确 false|把授权、获得、执行、反馈连通；JSON 存在不能证明该能力可用|
|O 键完全显现|`OathService` 与饰品授权→burn/rest/backlash|Oath 状态、共鸣支付/专用燃烧、network|按键、状态、反噬、原效果|**部分**；`NativeOathService/Data` 与原生 payload、O 键存在；部分测试覆盖|正式饰品、全部能力保留、独立休整/反噬、无尽免耗与燃烧区别、真实多女仆/客户端场景|
|白狐获得与外观|`ShrineFoxStory`、祭坛/转移/来源→`FoxSpiritState/Ledger`|正式胜利/来源身份与 model choice|`contract_fox:fox_white`、模型包安装与动作|**未移植正式获得，外观未接入生产**；native white_test 水晶与初始化 `maid->{}`，只命名，不指定原模型|祭坛/觉醒正常获得、权威来源与视觉身份分离；本轮模型原包可加载/基础渲染只是隔离原型|
|白狐实际能力|`WhiteFoxBlessingService`→守护/净化/治疗/誓约|cooldown SavedData、共鸣/成长|白狐原效果/声音/TLM 模型、成长提示|**部分**；`NativeWhiteFoxService` 有真执行；新吸收边界有 DamageContainer 回归；当前 cue 使用原版 END_ROD|全部阶段/普通与完全显现差别、重生/冷却、旧视觉声音、装备兼容；未在本轮重玩全部白狐能力|
|黑狐获得/挑战/Boss/SA|祭坛、challenge、`BlackFoxBossCompat` 与技能适配→SlashBlade 执行|Fox ledger、战斗/冷却状态、feedback network|Boss geo/动画/血条、粒子、能量 Shader、镜头；条件前置|**未移植执行链**；`BlackFoxSwordCoreGrowth` 有资料但 provider false|1.21.1 真 API、Boss/场景、技能/冷却/支付及服务端伤害；从源贴图复制不能恢复 procedural 演出|
|赤月觉醒与领域|原生来源/契约→`AkatsukiDomainService`|domain session、rescue、network|AkatsukiWorld/DomainVisual、图标/雨/镜头|**未移植执行链**；`AkatsukiDomainGrowth` 不授予能力|觉醒、绯月之庭/暴走/残月、空间斩、完全显现与客户视觉|
|魂核、凭证与伤害债务|`provenance`、`SoulDamageEngine/DebtService`、anchor/projectiles→观察/分配/追偿|来源凭证、hit window、同步及 Mixin|魂核/幻影剑/断月蚀轨与 renderer|**未移植**；不能假定 Forge 伤害阶段与 NeoForge 等价|以 native 伤害流水做最小实验证明；负向验证无凭空债务、无重复追偿、无 Boss 阶段穿透/粗暴改血|
|万华获得、轮盘、四符式|普通封存女仆 + 原生 DANMAKU_ART 饰品→`DanmakuEligibility/CombatService`|`DanmakuNetwork`、wave queue、hit budget、支付与冷却|wheel/input、TLM 弹幕/投射物、Painter、声音/粒子|**未移植**；通用 NativeAbilityService 中 verification 为 no-op，不是万华执行器|四符式真实命中/防御、追踪/波次/间隔、费用/无尽、权限和客户端同步。注意旧 `DanmakuForm.unlocked()` 恒 true，不能按遗留 requiredLevel 字段重新加门槛|
|内部空间进入/居民/装饰|`ContractInteriorKeyItem`→`ContractInteriorService/Builder`、home life|InteriorSavedData、权限/选择/居民位置|dimension/worldgen、生成式建筑、钥匙、家具与引导|**未移植**；当前新包 data 只有三份成长 JSON|维度格式、完整进入/离开/安全位置、居民工作/床/活动、多人隔离、正常重启、区块按需加载；不能只迁移 shrine.nbt 就宣称空间完成|
|手册、提示、附属玩法|`ContractHandbookService`、tooltip、宠物武器/附属饰品及实际 ModItems|配方/advancement、按需网络|Patchouli 页面/图标、语言、猫狗刀/饰品资源|**未移植完整集合**；当前仅开发语言/模型|逐项从真实注册 ID 对应获得/使用/提示，图标和条目引用闭合；不把仓库中缺失的新菜单凭空算成已有功能|
|声音/语音|`ClientRegistration`→`TlmPecoVoiceBridge`、voice policy 与事件；技能 SoundEvents|可选 TLM 类隔离与选择策略|TLM/原版音源及模型语音；旧 BGM 已撤下|**未移植完整触发链**；Neo 自有 sound 资源/桥接未建立；本轮音频设备失败，试听未通过|实际播放/打断/重复触发、缺可选前置、重载及可听验收；空 sounds.json 不表示原版没有任何声音行为|
|第三方联动|各 compat/Mixin/任务与权限→真实第三方 API|各适配器网络/数据|SlashBlade/Blade Tetra/Epic Fight/TaCZ/Goety/Iron's 等动作与渲染|**未移植完整集合**；已有版本列表是发布元数据，不是兼容验证|逐个固定版本测试、无可选 Mod 启动和 Dedicated Server 隔离；Blade Tetra 的版本进度不阻断核心|
|1.20.1 存档导入|旧物品/ForgeCaps、TLM 快照、SavedData 与内部空间|旧/新/未来 schema、备份/幂等/隔离|第三方嵌套物品及模型选择|**未实现正式迁移工具**；LegacySnapshotCodec 探索不等于 DataFixer/组件/世界升级；本轮旧世界只在 1.20.1 副本中运行|真实旧样本、嵌套物品、余额/成长/UUID、多通道/饰品/空间；重复导入不复制，未知数据不静默丢弃|
|双版本共享规则|Forge adapter→Java 17 shared domain；Neo adapter 同源|平台各自 NBT/Components/Attachment/Payload|共享原始资源仅限格式/语义一致|**部分**；9 份纯源码与三份成长资源等共享；Java17/漂移检查此前 PASS，#39 CI PASS|不把平台 API/渲染强塞共享；共享默认值不可盖配置；各玩家流程分别测试。美术原文件可复用，但导出/格式/加载须版本适配|

## 证据层级

- 代码/资源存在：本轮读源码与 JAR 直接核对，不算可玩通过。
- 原生行为：#38 `46083092596` 有生命周期/通道/饰品/物品入口/重启；#39 `46046671641` 有 Forge core、TaCZ、共鸣/重启、投影/随行、万华 native。可选 visuals jobs 跳过不计通过。
- 既往真实玩家截图：`docs/porting/evidence/2026-11-27` 明确保存原提交和 JAR SHA；不是本轮全部正式玩法重玩。
- 本轮真实客户端视觉实验：见 `EXPERIMENTS.md`；只覆盖隔离资源/渲染样本，不提升正式获得、执行器或完整演出的状态。
- 缺失生产入口与依赖链的资源，即使原型可显示，仍必须完成正常注册/触发/同步/重载/验收。
