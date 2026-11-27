# 1.20.1 → 1.21.1 功能验收矩阵

核对日期：2026-11-27。正式源基准 `master=d1429a46492783c4e773d6ccb17b606ca48f5113`；本轮起点 PR #38 `e73ac1c`、PR #39 `cd0ccfd`，均仍 OPEN。正式分支未改动。

状态：**完整**＝正式入口、执行、资源及必要验证均完成；**核心未验证**＝执行路径存在但缺本轮原生验收；**部分**＝仅部分行为或开发入口；**未移植**＝没有接入新平台；**外部阻塞**＝已确认具体缺失的外部条件；**退役**须明确批准。本表没有将任何玩法标为退役，也没有将未知的依赖版本当作外部阻塞。

Forge 列表示正式源码的实际实现，不代表本轮已经重新验证所有旧玩法。保留在 NeoForge 工作树的 `src/main` 不进入新版本编译或打包，因此不算已移植。以下路径以对应平台源码目录为根；同一行不能用配置存在代替执行器存在。

| 功能 | Forge 1.20.1 实现证据 | NeoForge 1.21.1 状态与验收缺口 |
|---|---|---|
| 契约缔结台及 GUI | `init/ModBlocks`, `ModMenus`, 配方序列化器与 shrine 模块 | **未移植**；缺正式 block/menu/recipe 注册 |
| 普通女仆捕获/显现/归刃 | `system/contract/ContractLifecycleService` | **部分**；`tlm/PortContractLifecycleTrial` 与开发载体交互；需正式入口和完整权限验收 |
| UUID、主人与账本权威 | `ContractCarrierData`, `ContractRecoveryJournal` | **核心未验证**；`storage/NativeContractStore`, 引用组件、实体附件、快照与事务日志；需本轮启动/重启验证 |
| 多契约通道 | `ContractChannelStorage`, `IntrinsicSpiritOriginData` | **部分**；`NativeChannelService` 支持封存选择和开发物品循环；显现中拒绝切换，缺正式 UI |
| 内置剑灵来源 | `IntrinsicSpiritOriginData`、白狐/黑狐祭坛获得路径 | **部分**；确定性 grant 账本与开发预设；水晶不是正式获得方式 |
| 解绑/重新绑定/魂符迁居 | `ContractInteractionService`, transfer 服务 | **未移植**正式流程；不能以切换通道代替迁居 |
| 紧急恢复/遗失武器/女仆重生 | `ContractAnomalyRecoveryService`, `ContractCarrierLossService`, rebirth 服务 | **部分**；诊断、检查点、已加载实体协调；未确认状态保持隔离，尚无完整恢复玩法 |
| 正常停服/重启 | Forge 原生恢复夹具 | **部分**；`NativeCheckpointService` 和双次启动夹具存在；非检查点事务不能猜测恢复 |
| 掉线/死亡/跨维度 | deployment、companion 与 oath 事件 | **部分**；NeoForge oath/白狐清理存在；完整生命周期与多人验收缺失 |
| 装备投影和恢复 | `system/deployment/ContractProjectionMode`、投影服务 | **未移植**；必须对真实 TLM 装备槽验收 |
| 随行/战斗协同/工作与任务路由 | `ContractCompanionService`, `ContractCombatTaskRouter`, `MaidCareTaskSystem` | **未移植**完整行为；直接显现 TLM 实体不能代表完成 |
| 安全喂食/护主/安全回收 | companion、`ContractGuardPolicy`、TLM 饰品权限 | **未移植**正式护主链 |
| 独立共鸣池/重复支付防护 | `system/resonance/ResonanceService`, `ResonancePoolSavedData` | **核心未验证**；`NativeResonanceService`, 独立 SavedData 与 attempt 收据；需原生夹具 |
| 共鸣恢复/战斗状态/击杀协同 | `ResonanceRecoveryEvents`, activity tracker | **部分**；原生 recovery tick 与直接攻击 credit；远程/第三方归因不完整 |
| 初醒/流转/无尽灵脉 | `ResonanceProgressService`, 升级物品 | **部分**；共享 `SpiritVeinRules` 与开发升级物品；缺正式资源和配方 |
| 无尽契约饰品与槽权限 | `ContractBaubleEffects`、TLM 原生槽注册 | **部分**；`NativeBaubleCapabilities`, `NativeBaubleExtension`；开发饰品尚未正式注册 |
| 配置数值兼容 | `MaidWeaponConfig`, `ContractRulesConfig`、各专用 config | **部分**；共享默认共鸣换算存在；NeoForge 原生可配置项不完整，不支持宣称全部自定义配置兼容 |
| 成长等级/技能数据重载 | `ContractMasteryData`, ability registry/reload | **部分**；`NativeMasteryData`, typed progression、bounded JSON reload；三份共享成长数据不等于三套技能执行器 |
| 同契之证/护主结/并契之环 | mastery/support baubles 与 `OathService` | **部分**；开发 oath/endless 饰品；正式辅助饰品和完整交互未完成 |
| O 键完全显现/燃烧/休整/反噬 | `system/oath` 与客户端按键/同步 | **部分**；`NativeOathService`, `NativeOathData`, O 键 payload 存在；需真实客户端、多女仆与数值验收 |
| 白狐获得/觉醒/驻留/黑狐协同 | `system/fox` 与祭坛、原居逻辑 | **未移植**正式获得；临时 white crystal 仅用于夹具 |
| 白狐守护/净化/治疗/护盾/机动 | `WhiteFoxBlessingService` 与成长数据 | **部分**；`fox/NativeWhiteFoxService` 有真实执行；仍缺完整演出、原生验收与阶段区别核对 |
| 白狐誓约/免死/冷却 | `WhiteFoxBlessingCooldownData`, blessing service | **部分**；独立原生冷却 SavedData、免死执行存在；伤害吸收顺序与专用客户端效果需验收 |
| 黑狐召唤/维持/模式/SA | black fox、sealed sword core 服务 | **未移植**执行器；`BlackFoxSwordCoreGrowth` 仅描述能力 |
| 黑狐挑战/Boss/场景 | black fox boss/arena/challenge 与对应资源 | **未移植** |
| 赤月觉醒/绯月之庭/血月暴走/残月·不归 | `system/akatsuki/AkatsukiDomainService` 等 | **未移植**执行器；`AkatsukiDomainGrowth` 仅成长资料 |
| 空间斩/魂核/普通暴击/幻影剑 | `system/akatsuki/soul` | **未移植**原生实体与伤害链 |
| 伤害观察/凭证/债务/限伤追偿 | `akatsuki/provenance`, `SoulDamageDebtService`, `SoulDamageEngine` 与 Mixin | **未移植**；不能直接复用 Forge 事件顺序，未知 Boss 不应追偿 |
| 断月蚀轨/成长/完全显现演出 | soul eclipse 服务与客户端资源 | **未移植** |
| 万华御札轮盘/输入/符式 | `system/danmaku`, 客户端轮盘与网络 | **未移植** |
| 幻灵剑/波次/追踪/命中间隔 | `compat/tlm/danmaku`, `DanmakuWaveSessions`, hit budget | **未移植**原生执行器；通用 verification 技能是测试用空效果，不是万华御札 |
| 万华防御/拦截/支付/无尽联动 | `DanmakuGuardService`, combat/payment 服务 | **未移植** |
| 契约内部空间/入口/安全离开 | interior 服务、dimension/worldgen/structures | **未移植**；旧维度 JSON 未加入新构建 |
| 居民/活动/家具/装饰/权限/区块 | interior/home life 服务与持久化 | **未移植**；尚无 NeoForge 实例和加载策略 |
| 共鸣 HUD/契约显示 | Forge network/HUD | **部分**；`PortStatusNetwork`, `PortResonanceHud`；需实际客户端验证，非完整成长/技能 UI |
| 网络安全及专用服务端隔离 | Forge `SimpleChannel` 与客户端隔离 | **部分**；原生 bounded payload、服务端费用与权限；完整消息集未迁移 |
| Items/Blocks/BlockEntities/Menus/Recipes | `init/ModItems`, `ModBlocks`, `ModMenus`, `ModRecipeSerializers` | **未移植**正式注册；当前 mod ID 仍 `maid_weapon_port_probe`，不得作为正式版本发布 |
| Entities/Effects/Sounds/Particles/Tabs | `ModSoulEntities` 与其他实际注册点 | **未移植**正式完整集合 |
| Data Components/Attachments | Forge NBT/Capabilities | **部分**；原生 typed components/attachment 已接入开发流程；不是 ForgeCaps 的自动转换 |
| 模型/贴图/动画/GUI/声音/粒子/Shader | `assets/maid_weapon/{models,textures,geo,animations,sounds,particles,shaders}` | **未移植**完整生产资源；原美术保留但未进入新构建 |
| 中英文文本/手册/配方/战利品/进度 | `assets/maid_weapon/lang`, Patchouli, `data/maid_weapon` | **未移植**完整内容；开发提示与模型不能代替正式资源 |
| Touhou Little Maid | 原生 TLM 1.5.3 Forge 适配与夹具 | **部分**；固定 `1.5.3-neoforge+mc1.21.1`，直接 API；仍需全任务/装备验证 |
| SlashBlade / Blade Tetra | 各自 compat 与 Mixin/版本门控 | **未移植**；未验证本轮 1.21.1 对应版本，不能声称兼容或虚构坐标 |
| Epic Fight / TaCZ | Forge 任务、投影、伤害联动 | **未移植**；Forge TACZ 历史 CI 成功不代表 NeoForge 兼容 |
| Goety / Iron's / 其他可选联动 | `forge/compat` 与 mixin 配置实际清单 | **未移植**；仍需逐个核实发布版本和真实 API |
| 旧武器/快照/共鸣/成长导入 | 旧 SavedData、物品 NBT、ForgeCaps | **未移植**正式导入；legacy snapshot codec 是探索性夹具，不是完整世界升级工具 |
| 多通道/嵌套物品/内部空间旧数据 | 对应旧存储与空间文件 | **未移植**；没有真实旧样本完整迁移验收，不应操作正式存档 |
| Java 17 公共业务规则 | PR #39 平台 adapter 委托纯 Java 源码 | **部分**；共鸣/成长/oath/技能费用等已共享；平台事件、注册与渲染保持隔离 |
| 共享成长资源 | 原 sealed core/white blessing/akatsuki 三份 JSON | **核心未验证**；移入 `src/shared/resources`，双版本构建纳入；仍需 native reload 核对 |
| 双版本同步 | `tools/porting/shared_patch.py`, `check_shared.py` | **部分**；明确共享树、确定性补丁、漂移诊断；不覆盖平台实现 |

## 当前安全与发布边界

普通物品不保存完整女仆快照；快照、共鸣、成长分别存储。未知 schema 必须保留并拒绝写入。已加载实体缺席不能证明未加载实体不存在。事务证据不足应隔离，不能因玩家需要取回而重建实体。

本轮检查到的旧 CI：#38 编译重载歧义；#39 共鸣夹具完成 OATH_NATIVE_PASS 后在辅助饰品 `disabled overrides accessory` 失败，伴随 NightConfig 文件监听重载。费用/恢复断言已走过不等于完整夹具通过。最终结果应以交付报告和新 HEAD 的运行记录为准。

**当前不满足正式游玩验收条件。** 必须先补齐正式入口、上述剑灵执行器、必要客户端与资源，再切换稳定 `maid_weapon` 注册体系；禁止提前将 probe 标记为完整产品。无实机截图、未执行和跳过的测试均不得写成通过。
