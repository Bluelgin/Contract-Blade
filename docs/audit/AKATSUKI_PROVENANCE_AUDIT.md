# 全树审计：伤害溯源与空中拘魂

基线为 PR #18 `02ffd9c61b676f61b531ecfe40a23c0b86a794cc`。独立分支 `codex/akatsuki-provenance-airborne`，目标为 `codex/akatsuki-domain`。没有修改第三方仓库或发行 JAR。本表区分可证明缺陷、运行风险与设计变更；扫描和自动夹具均不能证明全部模组组合无缺陷。

## 代码证实的缺陷

| 编号 | 严重程度 | 类/方法、触发与实际错误 | 根因 | 修复与回归 | 状态 |
|---|---|---|---|---|---|
| A01 | P1 | SoulAnchorService.observe/confirm：A 攻击失败，B/环境同 tick 扣血，A 获得魂核 | tick 总血差不代表单次调用归因 | 全调用同步 Scope；嵌套同目标扣血隔离；A/B、环境、范围多序列、异常测试 | 已实现；专服/最终回归见验收记录 |
| A02 | P1 | SoulAnchorEntity.hurt：自动范围斩/投射物激活核心 | LivingEntity 与伤害入口兼作操作意图 | 普通 Entity，hurt 固定拒绝；原生 AttackEntityEvent + 服务端精准射线 | 已实现；专服/最终回归见验收记录 |
| A03 | P2 | SoulBurstService.tick：384 预留最后一段153.6被128截断；预留与执行不一致 | 分段后截断且没有重新分配 | 预留前容量约束、水位分配；128/384/512/1024 边界及实际 HP | 已实现；专服/最终回归见验收记录 |
| A04 | P2 | ContractCombatCredits.logout：所有历史玩家 UUID 留在 GENERATIONS 直到停服 | 只递增不删除 | 清理该玩家发射、命中证明后移除 generation；原生归因回归 | 已实现；专服/最终回归见验收记录 |
| A05 | P2 | SoulAnchorService.create：公开调用无需对应原生成功攻击凭据 | API 隐含调用约定 | 仅消费已签发、不可重放的原生观测债务 | 已实现；专服/最终回归见验收记录 |
| A06 | P1 | SlashEffectProvenanceMixin.tick：第三方 tick 抛异常，RETURN 不执行，残留黑狐弹幕权限；嵌套覆盖外层 | HEAD/RETURN 不构成 finally | WrapMethod + 嵌套 AutoCloseable Scope；异常/父级恢复 | 已实现；专服/最终回归见验收记录 |
| A07 | P2 | SoulAnchorService.create：同 tick 死目标的核心未执行 tick，仍占玩家数量上限 | 只回收 isRemoved | 创建前检查真实绑定/目标有效性并 discard | 已实现；专服/最终回归见验收记录 |
| A08 | P2 | 旧额外黑狐验证入口依赖/文本断言过时 | 与现有三轮时序实现脱节；独立 javac 未列传递依赖 | 基线与新树分别原样执行，保留失败断言，见验证记录 | 未修复独立脚本 |

| A09 | P2 | BlackFoxAttackTrace.MELEE：WeakHashMap 的值 HitContext 反向持有玩家键，历史参与者无法依赖弱键回收 | 值到键形成强引用链 | 登出/停止挑战清除对应 owner 的近战与出生证明；不改战斗时序 | 已实现；专服/最终回归见验收记录 |

| A10 | P1 | 仅凭 player_attack DamageSource + 当前手持可误认延迟或第三方裸调用 | 缺少本次原生调用证明 | Player.attack 的实际 hurt 操作与 SlashBlade1.9.65 AttackHelper 精确 Source/目标/载体单次凭据；裸调用拒绝、原生API实测 | 已通过原生专服 |

| A11 | P2 | SoulAnchorRenderer.ribbon：魂核改到正上方后，与UP平行的牵引向量叉积为零，连接线消失；旧局部缩放也截短牵引 | 固定UP叉积且混用局部/世界单位 | 平行时改用X轴，补偿局部缩放；真实客户端截图复核 | 已实现，待最终图形复核 |

## 审查范围与未证实风险

| 模块 | 关键调用链检查 | 运行风险/剩余验证 |
|---|---|---|
| 契约生命周期 | ContractLifecycleService、ContractMaidLifecycleService、ContractMaidStorage、ContractWeaponLocator、ContractAnomalyRecoveryService：显现 publication、捕获 commit/rollback、异常 discard、实体实例与 UUID 防重、跨维度/登出边界 | 未知 Mod 的非常规移除仍需实际组合测试；不恢复永久“不可信”封锁，不新增备份事务系统 |
| TLM 备份/胶片与魂符 | 原生备份边界、历史 schema、迁居所有权与转移安全校验 | SoulTalismanService 提交后下游异常注入需验证；没有将未证实异常说成已修复 |
| Mastery/Ability | overworld SavedData 按玩家 UUID 唯一；legacy receipt、防伪造与 future schema、能力注册和 datapack/fallback 一致性 | 不增加赤月私有经验；新参数仍通过现有服务端词汇边界验证 |
| 黑狐 | 400 tick 剑核、范围/跟随、原生 SA 弹幕发射证明、显现停止与阶段状态 | 仅修复可证实作用域泄漏；不重写稳定 SA/挑战时序 |
| 白狐 | 固定 UUID 临时属性、只移除自身吸收、驻留授权、oath/crisis 持久冷却、共鸣清理 | 命中监听器在最终 HP 之前运行，未知外部最终拒伤可能消耗短期命中状态，属运行风险 |
| 赤月 | 领域/暴走、救援持久冷却、图腾/终月守护；Lv9 RainDirector、五通道适配 | Lv8/Lv9 生产实现不改；新增追偿拒绝受保护阶段，不能跳过实体替换/初始化 |
| 网络/类加载 | soul_burst 仅 S2C、距离裁剪/硬边界；可选 Boss 类均反射或 @Pseudo + 版本/字节码门控 | 普通 Entity 同步协议升至2，客户端和服务端必须一起更新；第三方 Shader/领地插件需组合验收 |
| 工程/资源/CI | 正式源码、资源、配置、Mixin、静态状态、18 项 CI 入口、四额外脚本；模型/许可仍使用原资源 | 完整构建、生产专服、真实图形验证状态以最终验证记录为准 |

## 配置与设计变更

旧攻击属性/cap/maxHP 独立伤害公式保留为兼容工具及旧测试，不再产生追偿额度；旧配置键保留并标为弃用。新追偿必须有 E/A/特殊限伤证据。基础领域伤害单独记账，保守上限默认0.5HP且不使用强事件覆写。`bossAdapters` 与 `knownBossAdapters` 是总开关/子开关的 AND。

死亡、登出、换刀、换维度、实体替换、阶段与正常防御变化都会终止旧债资格。账本不写武器 NBT，也不长期持久化；重启不复活旧债。seen 序列保留到原有效期，避免换刀后重放旧事件。

## 证据限制

环境维护删除了本任务第一轮未提交实现和日志。那一轮测试不作为当前提交的验收；下列记录必须以恢复后重新执行为准。FakePlayer 不是真人，软件 OpenGL 是真实 Minecraft 渲染但不是人工多人战斗。
