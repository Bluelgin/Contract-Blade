# 赤月领域「无归之庭」

## 范围与基线

本实现只属于 Contract-Blade。第二阶段基于 PR #18 `a5db47f5d55b6725b0b849429fee7a02a0e3d3d2`，继续更新 `codex/akatsuki-domain`；底层仍依赖 PR #17 的白狐成长基线。
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
| 1 | 绯月之庭 | 持合法觉醒赤月自动开庭；正常挥刀即时空间斩，常态不染色 |
| 2 | 百刃追斩 | 客户端原生幻影剑编队、加速追击和消散；真实脉冲独立计费 |
| 3 | 月轮断空 | 最多 5 格、4 个敌人的范围脉冲；交错裂痕，无圆柱结界 |
| 4 | 血月锁敌 | 主目标失效后每 20 tick 最多重捕获最近合法敌人 |
| 5 | 蓄月裁断 | 保留五伤害通道与 10% 破限比例 |
| 6 | 赤月反噬 | 保留普通箭/光灵箭/小火球白名单拦截与反击 |
| 7 | 绯樱葬阵 | 保留最多 3 名目标、有冷却且不可递归的击杀连锁 |
| 8 | 残月·不归 | 10% 血量或致命伤救援；25% 恢复、40 tick 保护、3600 tick 世界持久冷却 |
| 9 | 万刃天倾 | 原生模型剑雨；暴走视觉数量翻倍，真实伤害仍共享预算；比例 15% |
| 10 | 无归之庭 | 巨型瞬间裁断、20% 比例、原生终月脉冲的保守协同 |

Lv8 仍沿用数据包 capability ID `akatsuki_cloak`，以保持既有十级路线和整合包授权兼容；不再执行旧周期月衣。

## 自动领域状态机

`AkatsukiDomainState` 为纯状态机；`AkatsukiDomainSession` 仅保存临时目标、接触时间、能力调度和有限保护期限。预算属于独立玩家/目标服务，救援冷却属于世界 SavedData。

- `INACTIVE`：玩家死亡。
- `SUSPENDED`：失去授权、切换武器、剑灵显现或旁观模式。
- `NORMAL`：合法持刀自动展开，无需接触、不设固定寿命或重开冷却。
- `RAMPAGE`：当前生命/当前最大生命低于 50% 进入，达到 55% 才退出。支持动态最大血量。
- `RESCUE_COLLAPSE`：救援后 12 tick 停止领域追加攻击、裂痕收束、暂停染色续期。
- `RESCUE_REIGNITE`：接下来的 4 tick 再展开演出，之后按真实血量恢复领域（默认恢复 25%，因此暴走）。

没有战斗时只维持状态同步，领域不扫描敌人、不伤害、不生成常驻边界或粒子。只有合法接触之后 40 tick 内调度追加攻击；停止攻击后客户端视觉渐弱。每次真实接触即时反馈，重复的同 tick 同目标事件只生成一次即时演出。普通近战即使被 Boss 提前拒伤，仍可由 AttackEntityEvent 的服务端触及检查提供反馈。

目标仍限敌对生物或攻击玩家的 Mob，排除玩家、驯养物、盟友、无敌实体；必须同维度、范围和视线合法。手持切换不会清除技能调度，预算更不会重置。

## 残月救援与图腾

`LivingDamageEvent` LOWEST 在护甲、吸收结算后、原版图腾处理前检查真实剩余生命；合法救援取消当前伤害并恢复 25% 当前最大生命。已处于 10% 或以下也会在服务端玩家 tick 检查。`LivingAttackEvent` 在有限保护期限内取消普通伤害；期限至少覆盖收束及再燃，并始终有限。

只有生存/冒险、Lv8、合法驻留赤月且领域可攻击时才可领取。`/kill`、虚空、`bypasses_invulnerability` 来源不救援，也不受保护；虚空深处的低血量 tick 检查同样拒绝。成功时先领取冷却，再切状态、恢复生命和发送演出。冷却拒绝时不恢复、不播放成功。原版图腾保持原处理：赤月可用时图腾不消耗，冷却时由原版正常救援。

`AkatsukiRescueSavedData` 位于主世界 `data/maid_weapon_akatsuki_rescue.dat`，按玩家 UUID 保存主世界 gameTime 截止值。所有赤月共用一条记录，换刀/丢刀、显现、死亡克隆、重连、换维度均不改写。正常停服会保存并跨重启继续倒计时；时间按服务器运行 tick 计算，停服期间不倒计时。与其他 SavedData 一样，进程崩溃发生于自动保存前的持久性依赖世界存盘；不宣称抗世界回档。

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
- 每玩家所有目标累计最多 6 HP/20 tick；每玩家滚动 220 tick 所有目标总计最多 24 HP（可配置窗口 20–1200 tick）。
- 数值、适配器和配置无法超出代码里的 4/4/6/24 硬上限；比例硬上限 20%。

例如 C=20、D 未知、Lv10 时：单次最多 4 HP，且全体玩家对这个目标每 20 tick 共用 4 HP，不是每一刀追加 4 HP。如果 D=5，则目标额度进一步收紧为 1 HP/20 tick。

缓存清理不删除仍在时间窗内的共享扣费。实体卸载/死亡清理反馈；离线清理领域对象；短期预算到期后回收，正常停服清空短期伤害运行时。救援冷却独立持久化，不随运行时清理。

## 原攻击与终月隔离

领域 DamageSource 保留玩家为间接归属，directEntity 为 null；通过提供者公开 `SoulLegacyDamageGuard.apply(BooleanSupplier)` 包围同步调用，防止领域触发原生觉醒追加伤害或计入终月手动贡献。该 API 不可用时领域保留视觉、追加伤害关闭并诊断。

本模组自己的伤害入口有作用域防递归，击杀连锁另有排队和深度隔离。不给视觉剑注册发射证明，不生成真实高密度弹幕实体。

DomainHitWindow 保存/恢复仅由这次领域脉冲触碰的原版 invulnerableTime、hurtTime、hurtDuration、lastHurt，避免改变下一次普通攻击的差额计算。它不会清除攻击前已有的冷却。

受控版本的 Boss 适配同样保存/恢复伤害桶和受伤计时。云傀儡的额外前置计时只有在已扣预算的 absolute 通道期间临时放行，并立即恢复原值；休眠、转换、特定阶段保护仍拒绝。无全局清零、永久去防御或生命直写。

终月同步只观察真实 `blade_tetra:akatsuki_execution` LivingDamageEvent 脉冲；Lv10 空间裁断配合真实脉冲持续短时，领域追加伤害暂停 20 tick，交由原生处决继续。不访问私有 EXECUTIONS，不修改开始条件，不再次发起处决。**无法识别首个脉冲之前的开始、未造成伤害的执行过程和精确结束回调**；这些情况下只显示通用领域演出。

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

仅在 Mod ID、版本前缀和实体类族均匹配时启用内置专用适配。传奇怪物实际 Mod ID 为 `legendary_monsters`；其 mods.toml 把版本硬编码为 `1.20.1`，因此从所属 Mod 文件的 MANIFEST.MF 读取 Implementation-Version 校验 2.1.15；缺失时保守回退。其他版本走通用保守路径；匹配版本缺少预期反射成员时拒绝额外伤害并 warnOnce。对任意第三方版本和自定义硬无敌不宣称全兼容。

SPI `DomainTargetCompatibility.register(id, adapter)` 可追加只读上限/阶段适配，无需修改提供者源码。强穿透不是通用保证。

## 视觉与可选桥接

只读核实 SlashBlade Resharped **1.20.1-1.9.65** 发布 JAR 的 `SummonedSwordRenderer` 字节码与公开方法签名。`AkatsukiNativeVisualBridge` 调用 `BladeModelManager.getInstance().getModel(slashblade:model/util/ss.obj)`、`BladeRenderState.setCol` 和 `renderOverridedLuminous`，使用模型组 `ss` 与 `slashblade:model/util/ss.png`、原生 .0075 缩放和发光通道。颜色为赤红，alpha 随消散变化；单帧多剑共享 buffer 批次。没有服务端剑实体、没有复制模型/材质，没有三角形假剑降级。

空间斩是原创程序化裂隙网格：随机交错方向、分段尖端/不对称裂口、接近黑色内部、深红渐变边缘、粉白窄高光；约 1 tick 细线预兆，短促爆发，8 tick 内消散。巨型裁断最长 7 格，宽裂口短闪；没有缓慢飞行刀气。收束阶段现有裂隙向玩家移动缩小，再燃向外闪现交错裁断。音效复用原版锚耗尽及横扫音效的低音变调，不引入第三方素材。

`AkatsukiVisualDirector` 与伤害调度分离，客户端只在最近战斗和有效目标存在时产生效果。常态每 2–3 tick 一组 1–3 裂痕、8 tick 一组追剑；暴走每 1–2 tick 一组 2–5 裂痕、4–6 tick 一组更多追剑。连续真实接触增加组合强度，停止后 40 tick 内衰减归零。标准档每组最多 4 裂痕、高档 5、低档 1；质量绝不影响伤害。

暴走只有本地玩家请求 BladeTetra 的 `MikageBloodMoonDomainClient.beginAkatsuki(8)`，每 4 tick 续一次最多 8 tick 的短租期。退出、收束、换世界、断线停止续期，**永不调用 `endAkatsuki`**，不读写私有计时器。原生终月的较长请求通过提供者 `max()` 自然共享同一份染色。退出最多残留 8 tick 租期，之后按提供者 28 tick 淡出；不强行清除可能属于终月的效果。没有自建 PostChain 或修改真实天气、时间、天空。

资源重载清理临时剑和裂隙，重试公开接口；Shader 包/低配可关闭 `rampageTint`，提供者自身的 Shader 加载失败和视觉开关继续有效。可选类缺失、签名变化、反射调用错误安全关闭对应效果并 warnOnce，其他斩击仍可运行。卸载 BladeTetra 时领域本身因真实赤月授权不可用而暂停，不会客户端崩溃。

原生终月仍仅观察真实伤害脉冲；无法准确识别其完整开始/结束，故不依赖私有集合强行同步。提供者原生 `endAkatsuki` 可能临时缩短共享计时器，暴走下一次短续期会恢复；不承诺改写提供者行为。

## 配置和性能

服务端：`serverconfig/maid_weapon-akatsuki-server.toml`；客户端：`config/maid_weapon-akatsuki-client.toml`。
旧 duration/openingContacts/cooldown/cloakInterval 不再参与领域生命周期。配置包含暴走入/出比例、救援开关/阈值/恢复量/保护/收束/再燃/冷却、滚动预算窗口；旧 wholeDomainHP 改为 combatWindowHP，默认 24。整合包调整 rescueHealthFraction 高于暴走退出值时，会按真实生命退出暴走。

服务端每 2 tick 调度战斗，空闲无广域查询，能力到期才查询有限范围并截断目标数。暴走候选间隔缩短一半、最低 10 tick，仍受全部共享预算和目标 10 tick 间隔限制。5 tick 一次短期状态快照，网络协议升级为 2，客户端与服务端必须更新为同一版本。

客户端最多 16 领域；裂隙实例上限 48/192/192，剑实例上限 48/160/256（低/标准/高）；24/48 格距离裁剪。单几何批次绘制裂口、原生剑按其 RenderType 合批。实例到期、领域结束、退出世界和重载均清理。没有永久几何边界，也没有用粒子堆积营造密度。

## 文件与架构

| 职责 | 文件 |
|---|---|
| 全局成长和声明式路线 | `system/akatsuki/AkatsukiDomainGrowth.java`；`data/maid_weapon/contract_ability_progression/akatsuki_domain.json` |
| 固有身份、驻留及来源 | `AkatsukiDomainAuthorization.java`；`system/contract/IntrinsicSpiritOriginData.java`；真实 preset 工厂的一处 afterCommit 观察 |
| 生命周期与各级执行 | `AkatsukiDomainService.java`；`AkatsukiDomainSession.java`；纯状态 `AkatsukiDomainState.java`；持久救援 `AkatsukiRescueSavedData.java` |
| 单一伤害入口 | `AkatsukiDomainDamage.java`；`DomainHitWindow.java` |
| 预算与反馈 | 纯逻辑 `DomainDamageBudget.java`；`DomainDamageAdaptation.java` |
| 可选联动 | `compat/AkatsukiCombatCompat.java`；`compat/DomainTargetCompatibility.java` |
| 客户端与同步 | `client/AkatsukiDomainVisuals.java`；`AkatsukiVisualDirector.java`；`AkatsukiNativeVisualBridge.java`；`network/AkatsukiDomainNetwork.java` |
| 配置、注册和资源 | `common/AkatsukiDomainConfig.java`；MaidWeaponMod 注册；5 个 damage_type JSON、9 个追加分类标签 |
| 自动验证 | `DomainPolicyTest`、`AkatsukiGrowthTest`、`IntrinsicOriginTest`、`RescuePersistenceTest`、`VisualDirectorTest`；`AkatsukiDomainValidation`；现有 fixture runner/CI |

实际修改文件清单以 PR Files changed 为准。没有重构黑白狐执行器、FoxSpiritState、终月计算、生命周期恢复状态机或 TLM 存储格式。

## 验证入口与限制

```sh
gradle --no-daemon clean build -Pci=true
python tools/run_stage_three_tests.py --fixture akatsuki-domain --compat vanilla
python tools/run_stage_three_tests.py --fixture akatsuki-domain
python tools/run_stage_three_tests.py --fixture akatsuki-provider
python tools/run_stage_three_tests.py --fixture architecture
python tools/run_stage_three_tests.py --fixture blessing
python tools/run_stage_three_tests.py --fixture sword
python tools/run_stage_three_tests.py --fixture recovery
```

CI 下载 TLM 1.5.3、SlashBlade 1.9.65 到本地 runtimeOnly 目录，运行一次性专服世界，不访问用户存档。akatsuki-domain 夹具覆盖完整驻留证明、伪造全套身份、普通物品冒认、显现/收回、十级解析，以及真实原版 hurt 窗口和后续普通攻击伤害差额。基础夹具在**没有 BladeTetra** 的环境运行。另有 akatsuki-provider 夹具安装公开 BladeTetra 1.5.9、Tetra 6.3.0、mutil 6.2.0，通过提供者公开 debug 觉醒方法构造真实 ModularSlashBladeItem，验证实际身份、十级授权、显现/收回、无攻击持刀自动开庭、半血滞回、真实致命伤救援、原版图腾回退、持久冷却与同世界真实重启、公开 secondary guard 中的追加伤害与同 tick 去重。该夹具不替代真人游戏输入和客户端演出验收。

JUnit 另测十级 JSON/fallback 等价、各级独立能力、预算硬上限和多人共享、原生单次/DPS 分离、常驻与换手、救援幂等/再燃、视觉频率/空闲、冷却序列化、通道探测、阶段/异常生命反馈、来源重载和未来 schema。

图形客户端、三种 Boss 实战、真人多客户端和重混淆产物启动尚需游戏内验收；不得用“编译通过”替代这些结果。最新实际运行结论见 PR 验证段落及其 Actions 日志。
