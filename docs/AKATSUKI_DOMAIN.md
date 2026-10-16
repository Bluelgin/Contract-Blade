# 赤月领域「无归之庭」

## 范围与基线

本实现只属于 Contract-Blade。领域第二阶段基于 PR #18；本轮灵魂伤害重构基于 `100e138113ffe3a12acd7b746e9700ae34e7eff2`，继续更新 `codex/akatsuki-domain`。最新伤害策略、魂核机制及验证见 [灵魂伤害文档](AKATSUKI_SOUL_DAMAGE.md)。
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
| 2 | 绯月·拘魂 | 真实命中剥离魂核；主动攻击固定魂核触发有限多段灵魂轰击；保留旧能力 ID |
| 3 | 月轮断空 | 最多 5 格、4 个敌人的范围脉冲；交错裂痕，无圆柱结界 |
| 4 | 血月锁敌 | 主目标失效后每 20 tick 最多重捕获最近合法敌人 |
| 5 | 蓄月裁断 | 以实际 HP 反馈优化五通道适配，基础侵彻已从 Lv1 开放 |
| 6 | 赤月反噬 | 保留普通箭/光灵箭/小火球白名单拦截与反击 |
| 7 | 绯樱葬阵 | 保留最多 3 名目标、有冷却且不可递归的击杀连锁 |
| 8 | 残月·不归 | 10% 血量或致命伤救援；25% 恢复、40 tick 保护、3600 tick 世界持久冷却 |
| 9 | 万刃天倾 | 原生模型剑雨；暴走视觉数量翻倍，真实伤害接入统一灵魂预算 |
| 10 | 无归之庭 | 巨型瞬间裁断、完全体灵魂成长、原生终月脉冲的保守协同 |

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

目标仍限敌对生物或攻击玩家的 Mob，排除玩家、驯养物、盟友；必须同维度、范围和视线合法。手持切换不会清除技能调度，预算更不会重置。

## 残月救援与图腾

`LivingDamageEvent` LOWEST 在护甲、吸收结算后、原版图腾处理前检查真实剩余生命；合法救援取消当前伤害并恢复 25% 当前最大生命。已处于 10% 或以下也会在服务端玩家 tick 检查。`LivingAttackEvent` 在有限保护期限内取消普通伤害；期限至少覆盖收束及再燃，并始终有限。

只有生存/冒险、Lv8、合法驻留赤月且领域可攻击时才可领取。`/kill`、虚空、`bypasses_invulnerability` 来源不救援，也不受保护；虚空深处的低血量 tick 检查同样拒绝。成功时先领取冷却，再切状态、恢复生命和发送演出。冷却拒绝时不恢复、不播放成功。原版图腾保持原处理：赤月可用时图腾不消耗，冷却时由原版正常救援。

`AkatsukiRescueSavedData` 位于主世界 `data/maid_weapon_akatsuki_rescue.dat`，按玩家 UUID 保存主世界 gameTime 截止值。所有赤月共用一条记录，换刀/丢刀、显现、死亡克隆、重连、换维度均不改写。正常停服会保存并跨重启继续倒计时；时间按服务器运行 tick 计算，停服期间不倒计时。与其他 SavedData 一样，进程崩溃发生于自动保存前的持久性依赖世界存盘；不宣称抗世界回档。

## 灵魂伤害与终月隔离

正式领域入口现为 `SoulDamageEngine`，`AkatsukiDomainDamage` 仅作兼容委托。五通道和实际 HP 自适应保留，原有 4/4/6/24 HP 硬预算及按 Boss cap 的 10%–20% 天花板已被请求策略与独立安全预算替换。详见 [模块、数值、精确 Boss 入口及研究记录](AKATSUKI_SOUL_DAMAGE.md)。

每次真实伤害预扣费用，失败不退款。多人、换刀、领域切态与救援共用玩家/目标 UUID 滚动账本。`SoulStrikeContext` 仅在服务器同步调用期间提供精确来源/目标权限，异常同样清理。普通空间斩可突破原版防御和受击冷却，AGGRESSIVE 还局部对抗 Forge 取消与降额；提前返回须有已核实的专用入口。

领域来源保留玩家间接归属、directEntity=null；仍由 BladeTetra 公开 `SoulLegacyDamageGuard.apply` 包围，隔离原生觉醒追加与终月手动贡献。`DomainHitWindow` 和适配器恢复本次触碰的受伤计时/伤害桶。云傀儡及 Apostle 的前置计时器采用源感知读取覆盖，字段原值始终保留。

终月只观察真实 `blade_tetra:akatsuki_execution` 伤害脉冲，之后暂停灵魂追加 20 tick。不开第二套处决，不访问私有执行集合；不能精确识别第一个脉冲之前或无伤害的完整生命周期。

默认没有直接改血；实验策略才允许精确版本和稳定阶段的一次已适配残差补偿。未知生命字段不探测写入，实体销毁/替换不追打。实际支持与未突破入口以 [真实验证记录](AKATSUKI_SOUL_VALIDATION.md) 为准。

## 视觉与可选桥接

只读核实 SlashBlade Resharped **1.20.1-1.9.65** 发布 JAR 的 `SummonedSwordRenderer` 字节码与公开方法签名。`AkatsukiNativeVisualBridge` 调用 `BladeModelManager.getInstance().getModel(slashblade:model/util/ss.obj)`、`BladeRenderState.setCol`、`renderOverrided` 底色与 `renderOverridedLuminous` 发光，使用模型组 `ss` 与 `slashblade:model/util/ss.png`、原生发光通道（剑雨缩放 .0038，减小第一人称遮挡）。颜色为赤红，alpha 随消散变化；单帧多剑按底色/发光分别合批，日光下仍可辨识深红剑身。没有服务端剑实体、没有复制模型/材质，没有三角形假剑降级。

空间斩是原创程序化裂隙网格：随机交错方向、分段尖端/不对称裂口、接近黑色内部、深红渐变边缘、粉白窄高光；约 1 tick 细线预兆，短促爆发，6 tick 内消散。巨型裁断最长 7 格，宽裂口短闪；没有缓慢飞行刀气。收束阶段现有裂隙向玩家移动缩小，再燃向外闪现交错裁断。音效复用原版锚耗尽及横扫音效的低音变调，不引入第三方素材。

`AkatsukiVisualDirector` 与伤害调度分离，客户端只在最近战斗和有效目标存在时产生效果。常态每 2–3 tick 一组 1–3 裂痕；暴走每 1–2 tick 一组 2–5 裂痕。旧 Lv2 追剑已替换为魂核，Lv9 剑雨独立保留。连续真实接触增加组合强度，停止后 40 tick 内衰减归零。标准档每组最多 4 裂痕、高档 5、低档 1；质量绝不影响伤害。

剑雨由独立的 `AkatsukiRainDirector` 接收原有 RAIN cue，常态在 44 tick、暴走在 36 tick 的短雨幕内随机错开出剑；不再使用 `i % 14` 的循环批次。默认标准档每幕常态 33 把、暴走 67 把；低/标准/高每 tick 最多 2/4/6 把，单幕最多 192 把，新 cue 替换余下计划而不叠加积压。出生时刻含子 tick 随机偏移，允许自然空隙和短簇；卡顿超过幕期限后直接丢弃，不补发整片剑阵。

每把剑出生时以当前目标位置选点：55% 在 1.1 格内集中，其余散布至 3.6 格，其中 20% 可按当前水平移动向前预测，预测偏移最多 1.4 格。落点经客户端实体无关的地形碰撞射线确认，锁定后不再追踪；无已加载区块或目标附近可落地表时跳过。随机高度 8–12 格、显现 .35–.85 tick，之后以恒速在 1.8–3 tick 内高速刺下；近竖直方向微倾、短尾迹及 1.5 tick 内消散的地面闪光复用原几何批次。剑身继续走原生 `ss` 模型底色/发光两批渲染。失效目标、离开可视距离、收束、领域结束、换世界、资源重载取消后续落剑。以上随机落剑调度、原生模型与图形回归夹具在灵魂伤害重构中保留；服务端伤害统一接入灵魂引擎。

剑雨动态验收使用独立临时存档：`python tools/run_akatsuki_visual_test.py --gradle gradle --rain --record`。需要独占的 854×480 X11 显示及 ffmpeg，输出 `gameplay.mp4` 和原始截图；依次检查常态静止/移动目标、暴走静止/移动目标、高档第三人称和低档。录制只在观察到真实场景初始化日志后启动，未完成夹具会返回失败。

暴走只有本地玩家请求 BladeTetra 的 `MikageBloodMoonDomainClient.beginAkatsuki(8)`，每 4 tick 续一次最多 8 tick 的短租期。退出、收束、换世界、断线停止续期，**永不调用 `endAkatsuki`**，不读写私有计时器。原生终月的较长请求通过提供者 `max()` 自然共享同一份染色。退出最多残留 8 tick 租期，之后按提供者 28 tick 淡出；不强行清除可能属于终月的效果。没有自建 PostChain 或修改真实天气、时间、天空。

资源重载清理临时剑和裂隙，重试公开接口；Shader 包/低配可关闭 `rampageTint`，提供者自身的 Shader 加载失败和视觉开关继续有效。可选类缺失、签名变化、反射调用错误安全关闭对应效果并 warnOnce，其他斩击仍可运行。卸载 BladeTetra 时领域本身因真实赤月授权不可用而暂停，不会客户端崩溃。

原生终月仍仅观察真实伤害脉冲；无法准确识别其完整开始/结束，故不依赖私有集合强行同步。提供者原生 `endAkatsuki` 可能临时缩短共享计时器，暴走下一次短续期会恢复；不承诺改写提供者行为。

## 配置和性能

服务端：`serverconfig/maid_weapon-akatsuki-server.toml`；客户端：`config/maid_weapon-akatsuki-client.toml`。
旧 duration/openingContacts/cooldown/cloakInterval 不再参与领域生命周期。暴走及救援配置保持；旧领域 damageBudget 配置仍可读取但不再驱动正式伤害，使用新的 `maid_weapon-soul-server.toml`。整合包调整 rescueHealthFraction 高于暴走退出值时，会按真实生命退出暴走。

服务端每 2 tick 调度战斗，空闲无广域查询，能力到期才查询有限范围并截断目标数。普通灵魂斩默认同目标最短四 tick，范围/剑雨等到期技能也共用同一安全账本。5 tick 一次短期状态快照，网络协议升级为 2，客户端与服务端必须更新为同一版本。

客户端最多 16 领域；裂隙实例上限 48/192/192，剑实例上限 48/160/256（低/标准/高）；24/48 格距离裁剪。单几何批次绘制裂口、原生剑按其 RenderType 合批。实例到期、领域结束、退出世界和重载均清理。没有永久几何边界，也没有用粒子堆积营造密度。

## 文件与架构

| 职责 | 文件 |
|---|---|
| 全局成长和声明式路线 | `system/akatsuki/AkatsukiDomainGrowth.java`；`data/maid_weapon/contract_ability_progression/akatsuki_domain.json` |
| 固有身份、驻留及来源 | `AkatsukiDomainAuthorization.java`；`system/contract/IntrinsicSpiritOriginData.java`；真实 preset 工厂的一处 afterCommit 观察 |
| 生命周期与各级执行 | `AkatsukiDomainService.java`；`AkatsukiDomainSession.java`；纯状态 `AkatsukiDomainState.java`；持久救援 `AkatsukiRescueSavedData.java` |
| 单一伤害入口 | `AkatsukiDomainDamage.java`；`DomainHitWindow.java` |
| 预算与反馈 | `soul/SoulDamagePolicy.java`、`SoulDamageSafetyBudget.java`；`DomainDamageAdaptation.java` |
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

本轮图形客户端、Boss JAR 扣血及回归结果详见 `AKATSUKI_SOUL_VALIDATION.md`；真人多客户端和其他第三方版本仍须对应环境验收。

## 可复现图形验收

```sh
python tools/run_akatsuki_visual_test.py --gradle gradle
```

使用与专服夹具相同的本地可选依赖目录；在有 OpenGL 的真实桌面或 Xvfb/Mesa 中运行。脚本创建独立临时目录，先由专服生成 `Akatsuki Visual Validation`，再复制到独立客户端 `saves/akatsuki-visual-validation`。`akatsukiVisualTest` 默认关闭，夹具拒绝其他世界名；只自动确认这一生成存档的原版实验性世界提示，不操作用户存档。

测试通过公开 debug 觉醒入口建立真实赤月契约、全局 Lv10，与加载的 Husk 进行真实归因伤害；领域、救援、网络和客户端渲染走正式实现。自动记录常态、暴走、剑雨、收束、再燃、高特效第三人称、低特效，以及通过实际普通伤害达到原生终月条件后的共存帧。HUD 绘制后截图，600 tick 后退出。日志中的 `AKATSUKI_NATIVE_EXECUTION_OBSERVED` 才是实际观察到原生终月的依据，不能仅凭截图文件名宣称验证成功。

`--output PATH` 可指定一个新的测试输出目录；已有同名测试存档拒绝覆盖。`--skip-assets` 只供已预置资源的测试环境使用，未补齐音频资源时不能用于声音验收。软件 OpenGL 可检验真实渲染路径与崩溃，不能代表独显帧率。真人手动输入、多客户端、第三方 Shader 包与声音听感仍需对应环境验收。

本轮已完成的构建、JUnit、专服、重启与图形结果以 PR 验证段落和链接日志为准。历史第二阶段的 45 项 JUnit 覆盖原有成长/自适应及新增状态、预算窗口、冷却与视觉调度；专服夹具另通过可受伤的 FakePlayer 执行真实 ServerPlayer/Player 伤害与图腾路径，测试本身会解除 FakePlayer 的默认无敌与出生保护，不改变玩家生产行为。
