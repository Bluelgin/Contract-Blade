# 赤月领域「无归之庭」

## 范围与基线

本实现只属于 Contract-Blade，基于 PR #17 `450692a644dbd1b0484a27fdc75f39323699cbd9`。
BladeTetra、传奇怪物、灾变全部为只读参考和可选运行环境。本次没有修改它们的仓库、伤害注册、觉醒、原生技能或处决阈值。

能力 ID：`maid_weapon:akatsuki_domain`。等级直接取玩家全局 ContractMastery，无赤月私有经验。

## 资格与旧存档

最终授权同时要求：真实主手物品通过 `BladeTetraStoryCompat.isAkatsuki` 调用提供者 `SoulLegacyState.active`；这包括 ModularSlashBladeItem 类型、铭文、觉醒及未损坏状态。还必须通过现有 ContractCombatCreditResolver、ContractAuthorization、IntrinsicSpiritApi 活动投影、非休眠、非过渡、完整序列化女仆 Owner/UUID/Binding/Spirit 匹配，以及跨维度实体和恢复日志的“未显现”检查。绝不使用 FoxSpiritState。

仅结构一致的 NBT 也不足以授权：`maid_weapon_intrinsic_origins` SavedData 记录服务器在真正创建/收回女仆时观察到的 Owner、Maid UUID、Binding、Spirit。领域检查该记录；NBT 中没有可自行打开的授权布尔值。更换身份、伪造全套压缩快照、客户端创建新身份均不能凭物品数据取得记录。服务端代码和管理员直接修改世界 SavedData 属于原有信任边界。

**已有赤月若在本版本前创建且尚无来源记录，需要正常显现、收回一次，留下真实实体来源。** 新建固有剑灵自动记录。来源表只控制新领域，不封锁旧武器、召唤、收回、魂符、胶片或 TLM 备份，也没有副本数量锁；完整合法副本继续共享玩家和目标预算。

驻留证明按完整 contract 内容比较缓存，最长 20 tick；活体存在、过渡、休眠、所有者和真实物品身份始终重新检查。剑灵显现、换刀、死亡或离线会停止领域。

## 十级实际实现

| 等级 | 名称 | 服务端能力与可见表现 |
|---|---|---|
| 1 | 绯月之庭 | 合法连续接触开庭；血月、圆形边界、空间残影；20 tick 斩影脉冲 |
| 2 | 百刃追斩 | 30 tick 幻影剑追击当前目标；客户端多剑只对应一个候选脉冲 |
| 3 | 月轮断空 | 60 tick 环斩；半径最多 5 格，最多 4 个敌人，共享全部额度 |
| 4 | 血月锁敌 | 每 20 tick 标记主目标；原目标失效后自动捕获领域内最近合法敌人 |
| 5 | 蓄月裁断 | 解锁五种伤害通道探测、实际生命反馈缓存及 10% 有限额度；内圈亮起 |
| 6 | 赤月反噬 | 每 40 tick 最多拦截一枚朝玩家飞来的普通敌对箭/光灵箭/小火球，反击射手 |
| 7 | 绯樱葬阵 | 合法击杀排队一次连锁；下一领域 tick 最多斩 3 名死亡地点 4 格内的敌人，30 tick 冷却；连锁不能产生连锁 |
| 8 | 不灭月衣 | 每 60 tick 获得 20 tick 月衣，减伤 15%、单次最多 2 HP，防普通击退；不覆盖其他药水/属性 |
| 9 | 万刃天倾 | 每 100 tick 血月剑雨；默认 48 把视觉剑，最多 4 个真实目标，额度升至 15% |
| 10 | 无归之庭 | 双环、空间穹顶、巨大血月与集中裁断；每 80 tick 单次焦点脉冲，额度升至 20%；观察到原生终月脉冲时压暗血月、收束剑刃 |

所有能力仍受同一伤害预算约束，同时到期不等于同时领取多份伤害。
Lv8 防打断指原版击退，不声称能取消其他模组自定义硬控、动作系统或脚本。

## 领域规则

默认 9 格半径、220 tick 最长持续、320 tick 开庭冷却。40 tick 内至少 3 次合法接触开庭，接触计数最短间隔 8 tick。只认主手近战真实触及目标，或现有服务证明归因成功的本体/原生发射物伤害；不借玩家当前持刀给陌生弹幕归因。

目标限定敌对生物或正在攻击玩家的 Mob，排除玩家、驯养物、盟友、无敌实体，要求同维度、距离和视线。无有效接触超过 40 tick 即停止；攻击空气不计数。不无限续期：每个领域固定寿命和总额度，达到寿命后必须遵守开庭冷却重新开始。冷却按服务器玩家 UUID 保留至到期，换手、死亡、换维度和重连不刷新；正常重启清理临时会话。

先在攻击事件记录接触，再由后续玩家 tick 执行独立脉冲。被 Boss 在 Forge 受伤事件前拒绝的普通近战，仍可通过服务端 AttackEntityEvent 的真实触及检查维持接触，但不会假装原攻击已扣血。

## 伤害类型及标签

| maid_weapon 伤害类型后缀 | 标签 |
|---|---|
| `akatsuki_domain_slash` | `minecraft:no_impact` |
| `akatsuki_domain_rapid` | 上述 + `minecraft:bypasses_cooldown` |
| `akatsuki_domain_piercing` | `no_impact` + `minecraft:bypasses_armor` |
| `akatsuki_domain_arcane` | `no_impact` + `minecraft:witch_resistant_to` + `forge:is_magic` |
| `akatsuki_domain_absolute` | `no_impact` + `bypasses_cooldown`、`bypasses_armor`、`bypasses_invulnerability`、`bypasses_shield`、`bypasses_resistance`、`bypasses_enchantments` |

`forge:is_magic` 是附加分类标签；不假定所有第三方模组都会识别它。所有 tag JSON 都为 `replace:false`，只加入本模组上述类型。普通攻击、SlashBlade SA、BladeTetra 终月类型不修改。配置关闭强通道会禁用选择它，不会全局删除其他来源的标签。

## 自适应算法

每目标保存一个短期状态：当前类型、各通道最近真实扣血/请求伤害比例、连续失效次数、切换时间窗及阶段指纹。每个脉冲只有一个 hurt 调用，所有类型共用预算。

顺序从 slash 开始。连续两次有效率低于 65% 后，间隔至少 20 tick 才选择尚未测试的通道；最多 4 次切换/200 tick。已有可靠效果时保持缓存；必要时重新探索或回到较有效类型。阶段变化或 200 tick 未使用会丢弃旧反馈。所有通道连续无进展则暂停 100 tick。

不把 hurt 返回 true 当作成功。同步调用前后读取公开 getHealth；只有生命变化有限、非负、不超出本次请求容差，且阶段稳定，才计入可靠反馈。自定义生命系统只使用其公开读取值，绝不 setHealth；延迟更新、不可读、回血、异常突变一律不宣称有效。未知情况保守限额、轮换次数限制和暂停探测，不盲目加伤。

## 统一预算

每次在伤害调用前预扣，失败也扣额度，不退款重试。纯逻辑见 DomainDamageBudget。

- 原生单次上限 C 可读：本次候选上限为 C × 等级比例，再受绝对单次 4 HP 限制。
- 原生 DPS 上限 D 可读：共享目标滑动 20 tick 额度为 D × 比例，再受 4 HP/s 限制。
- C 可读而 D 未知：本模组制定“每 20 tick 至多一个单击额度”的额外速率策略；这不是把 C 声称为 Boss 原生 DPS。
- C、D 都未知：单次最多 0.5 HP，单目标共享最多 1 HP/20 tick。
- 所有来源、所有玩家对同一 UUID 最短 10 tick 才能发起另一次真实脉冲；共享目标滑动窗口没有固定秒边界翻倍漏洞。
- 每玩家所有目标累计最多 6 HP/20 tick；每个领域所有目标总计最多 24 HP。
- 数值、适配器和配置无法超出代码里的 4/4/6/24 硬上限；比例硬上限 20%。

例如 C=20、D 未知、Lv10 时：单次最多 4 HP，且全体玩家对这个目标每 20 tick 共用 4 HP，不是每一刀追加 4 HP。如果 D=5，则目标额度进一步收紧为 1 HP/20 tick。

缓存清理不删除仍在时间窗内的共享扣费。实体卸载/死亡清理反馈；离线清理领域对象；短期预算到期后回收，正常停服清空运行时。

## 原攻击与终月隔离

领域 DamageSource 保留玩家为间接归属，directEntity 为 null；通过提供者公开 `SoulLegacyDamageGuard.apply(BooleanSupplier)` 包围同步调用，防止领域触发原生觉醒追加伤害或计入终月手动贡献。该 API 不可用时领域保留视觉、追加伤害关闭并诊断。

本模组自己的伤害入口有作用域防递归，击杀连锁另有排队和深度隔离。不给视觉剑注册发射证明，不生成真实高密度弹幕实体。

DomainHitWindow 保存/恢复仅由这次领域脉冲触碰的原版 invulnerableTime、hurtTime、hurtDuration、lastHurt，避免改变下一次普通攻击的差额计算。它不会清除攻击前已有的冷却。

受控版本的 Boss 适配同样保存/恢复伤害桶和受伤计时。云傀儡的额外前置计时只有在已扣预算的 absolute 通道期间临时放行，并立即恢复原值；休眠、转换、特定阶段保护仍拒绝。无全局清零、永久去防御或生命直写。

终月同步只观察真实 `blade_tetra:akatsuki_execution` LivingDamageEvent 脉冲；Lv10 暗月和剑刃收束持续短时，领域追加伤害暂停 20 tick，交由原生处决继续。不访问私有 EXECUTIONS，不修改开始条件，不再次发起处决。**无法识别首个脉冲之前的开始、未造成伤害的执行过程和精确结束回调**；这些情况下只显示通用领域演出。

## Boss 源码研究和适配边界

只读审阅版本：

- [BladeTetra eba3b9e](https://github.com/Bluelgin/Blade-Tetra-Forged-Styles/tree/eba3b9ec7f92cc3c26f2975f11671b62243236a2)：SoulLegacyState、AkatsukiAwakening、AkatsukiExecution、SoulLegacyDamageGuard、ContractBladeCompat。
- [Legendary Monsters dc5063f](https://github.com/Miauczel/Legendary-Monsters-1.20.1/tree/dc5063fac4b23be9ba78533b115df48bd352a293)：2.1.15 MC 1.20.1；IAnimatedBoss、ForgeEvents、Cloud_GolemEntity、TheObliteratorEntity。
- [Cataclysm 8199372](https://github.com/lender544/new1.20.1/tree/819937203e22b4fa72fdeccc6c56ad415d65eeef)：3.31；LLibrary_Boss_Monster、Ignis_Entity。

| 目标 | 源码结论与已实现策略 | 验证等级 |
|---|---|---|
| 云傀儡 | getHealth 读取累计伤害；ForgeEvents 调用 addDamage；普通 10 tick 前置无敌不看强标签，absolute 采用限定版本的局部放行；休眠/13/21 状态保护 | 源码审阅；未装真实 Boss JAR 做实机验收 |
| 毁灭者 | 独立 InvulnerabilityTime、状态/瞬移/振刀拒伤、自定义生命；读取 damageCap 与 getPhase；保留无条件拒伤，不强行模拟成功 | 源码审阅；未装真实 Boss JAR 做实机验收 |
| Ignis | DamageCap 和 DpsCap 分开；damageBucket 按 DpsCap/20 恢复；盾、反击、阶段保护独立；强通道有限绕过一般防御，PHASE_2/3、STRIKE、ULTIMATE_ATTACK 保护 | 源码审阅；未装真实 Boss JAR 做实机验收 |

仅在 Mod ID、版本前缀和实体类族均匹配时启用内置专用适配。其他版本走通用保守路径；匹配版本缺少预期反射成员时拒绝额外伤害并 warnOnce。对任意第三方版本和自定义硬无敌不宣称全兼容。

SPI `DomainTargetCompatibility.register(id, adapter)` 可追加只读上限/阶段适配，无需修改提供者源码。强穿透不是通用保证。

## 配置和性能

服务端：`serverconfig/maid_weapon-akatsuki-server.toml`；客户端：`config/maid_weapon-akatsuki-client.toml`。
服务端配置覆盖开关、半径、寿命、冷却、接触维持、各能力间隔、剑雨视觉数量、三档破限比例、候选通道、强通道、适配器、共享目标/玩家/领域限额。客户端 quality=0/1/2 与 visualDensity 只影响演出。

服务端能力按 5 tick 调度；环斩和剑雨到期才作局部实体查询，截断真实目标数，不全世界扫描。每目标至少 10 tick 一次 hurt；所有视觉用一个客户端几何缓冲区。客户端最多保存 16 个领域、96 个短演出；每帧最多渲染最近 6 个领域/48 个短演出，低档更少；退出或换世界清理。

客户端血月为领域上方的程序化几何圆盘及空间边界，遵守场景深度；不是全局换天、后处理 shader 或独立维度。没有新增外部美术资产。多剑/剑雨只是网格，数量不增加服务器伤害次数。

## 文件与架构

| 职责 | 文件 |
|---|---|
| 全局成长和声明式路线 | `system/akatsuki/AkatsukiDomainGrowth.java`；`data/maid_weapon/contract_ability_progression/akatsuki_domain.json` |
| 固有身份、驻留及来源 | `AkatsukiDomainAuthorization.java`；`system/contract/IntrinsicSpiritOriginData.java`；真实 preset 工厂的一处 afterCommit 观察 |
| 生命周期与各级执行 | `AkatsukiDomainService.java`；纯状态 `AkatsukiDomainSession.java` |
| 单一伤害入口 | `AkatsukiDomainDamage.java`；`DomainHitWindow.java` |
| 预算与反馈 | 纯逻辑 `DomainDamageBudget.java`；`DomainDamageAdaptation.java` |
| 可选联动 | `compat/AkatsukiCombatCompat.java`；`compat/DomainTargetCompatibility.java` |
| 客户端与同步 | `client/AkatsukiDomainVisuals.java`；`network/AkatsukiDomainNetwork.java` |
| 配置、注册和资源 | `common/AkatsukiDomainConfig.java`；MaidWeaponMod 注册；5 个 damage_type JSON、9 个追加分类标签 |
| 自动验证 | `DomainPolicyTest`、`AkatsukiGrowthTest`、`IntrinsicOriginTest`；`AkatsukiDomainValidation`；现有 fixture runner/CI |

实际修改文件清单以 PR Files changed 为准。没有重构黑白狐执行器、FoxSpiritState、终月计算、生命周期恢复状态机或 TLM 存储格式。

## 验证入口与限制

```sh
gradle --no-daemon clean build -Pci=true
python tools/run_stage_three_tests.py --fixture akatsuki-domain
python tools/run_stage_three_tests.py --fixture architecture
python tools/run_stage_three_tests.py --fixture blessing
python tools/run_stage_three_tests.py --fixture sword
python tools/run_stage_three_tests.py --fixture recovery
```

CI 下载 TLM 1.5.3、SlashBlade 1.9.65 到本地 runtimeOnly 目录，运行一次性专服世界，不访问用户存档。akatsuki-domain 夹具覆盖完整驻留证明、伪造全套身份、普通物品冒认、显现/收回、十级解析，以及真实原版 hurt 窗口和后续普通攻击伤害差额。它在**没有 BladeTetra** 的环境运行，不能替代真实觉醒赤月授权成功和全部能力实机验证。

JUnit 另测十级 JSON/fallback 等价、各级独立能力、预算硬上限和多人共享、原生单次/DPS 分离、换手/空挥、通道探测、阶段/异常生命反馈、来源重载和未来 schema。

图形客户端、真实 BladeTetra 安装组合、三种 Boss 实战、真人多客户端和重混淆产物启动尚需游戏内验收；不得用“编译通过”替代这些结果。最新实际运行结论见 PR 验证段落及其 Actions 日志。
