> 历史记录：本文对应 PR #18 的 `02ffd9c` 独立灵魂伤害机制。新的溯源/累计追偿和空中魂核见 [当前架构](AKATSUKI_DAMAGE_PROVENANCE.md)，本页历史扣血数值不作为新版本验收。

> 历史方案与实现记录，可能包含已撤回功能、旧数值和当时的测试结果。
> 当前说明从 [文档导航](../../README.md) 进入；本页不作为现行玩法指南。

# 赤月灵魂伤害与「绯月·拘魂」

本轮基线是 PR #18 `100e138113ffe3a12acd7b746e9700ae34e7eff2`，开发分支继续为 `codex/akatsuki-domain`。仅修改 Contract-Blade；BladeTetra、Goety Revelation 与 Boss JAR 均只读，均未变为正式依赖。Lv8 救援、Lv9 `AkatsukiRainDirector` 随机高速落剑及十级授权沿用原实现。

## 从空间斩到灵魂轰击

Lv1 起，领域真实伤害统一进入 `SoulDamageEngine`。Lv2 旧 `akatsuki_swords` 能力 ID 保留，含义变为「绯月·拘魂」，不再自动发射追击幻影剑。Lv9 原生模型剑雨及其协议仍保留。

只有合法主手觉醒赤月、驻留固有剑灵、授权成长、有效可攻击领域，才能使用灵魂伤害。实体仍须为同维度敌对目标，排除玩家、驯养物及盟友。单纯的 invulnerable 标志不再使目标提前被筛掉；能否突破由本次真实伤害和已核实入口决定。

一次玩家归因的原生攻击由 `LivingAttackEvent` 记录前置 HP，玩家 tick END、领域自动伤害之前确认真实 HP 下降，再生成魂核。取消、零扣血、领域次级伤害、原生终月伤害均不生成魂核。未知延迟生命系统未在该 tick 更新时不声称成功。

魂核是独立 `LivingEntity`，方便原生近战及 SlashBlade 的生物命中筛选；不属于 Mob，没有 AI、掉落、自然生成、存盘或 Boss 生命继承。固定世界坐标、1.3×1.8 判定，默认 60 tick，一名玩家对同一 UUID 仅一个；创建间隔默认 60 tick，每玩家最多四个、全服最多 256。目标卸载、死亡、跨维度、关联失效，玩家失去授权或救援收束时清理。目标可在同维度离开原位置，默认关联范围 128 格，不加载目标区块。

攻击魂核必须由其所有者持合法赤月近身命中，有服务端 reach、视线和同 tick 去重。其他玩家不能劫持、转移或重复开启它。首次命中为目标预留一次有限总额度；默认四段，约 10 tick 完成，采用不同伤害通道及不等段额。后续挥刀仅可提前剩余段，不增加总额，每 tick 至多一段。完成后魂核崩裂，目标共享 60 tick 冷却；多人、换刀及领域再燃都不退款或重置。

## 伤害职责与作用域

| 模块 | 责任 |
|---|---|
| `SoulDamagePolicy` | 根据攻击属性、成长、已知限伤参考与有限最大生命比例计算请求 |
| `SoulDamageSafetyBudget` | UUID 共享滚动安全限额、同帧去重、目标爆发冷却及预扣 |
| `SoulStrikeContext` | 攻击者、目标、绑定、序列、模式、请求与临时权限 |
| `SoulDamageSource` | 仅精确来源对象及当前目标作用域内提供穿透标签 |
| `SoulDamageOverride` 与三个事件 Mixin | 局部拒绝取消、伤害 floor/ceiling 与事件阶段证据 |
| `DomainTargetCompatibility` / `BossLimitAdapter` | 唯一适配注册表，读取、阶段识别、已验证生命 API |
| `SoulDamageFeedback` / `DomainDamageAdaptation` | 实际 HP 差额反馈、五通道缓存与有限失败探测 |
| `SoulAnchorService` / `SoulBurstService` | 实体绑定、交互、清理及有限多段状态 |

`AkatsukiDomainDamage` 只保留旧入口兼容委托。旧 `DomainDamageBudget` 及原有限伤测试作为历史策略回归保留，不再参与正式伤害；未删除测试来掩盖变化。

上下文只在服务器线程的同步调用内存在，嵌套调用拒绝，`try-with-resources` 在异常时同样移除 ThreadLocal。事件必须同时匹配目标实例、精确 DamageSource 对象与服务器线程；普通武器、邻近目标及嵌套的其他伤害均不获得权限。源对象逃出作用域后失去新增标签权限。

Forge 的基础 `Event` 位于早期类加载器，因此取消 Hook 放在 `LivingAttackEvent`、`LivingHurtEvent`、`LivingDamageEvent` 自身，仍委托其他来源的正常父类行为。amount 构造、setter、getter 均局部限制到本次请求；AGGRESSIVE 下恢复被削减的数值，防止只在 LOWEST 恢复后再次被监听器压低。不能恢复在 hurt 前置分支直接返回、从未到达 Forge 的伤害。

所有正常伤害和可选补偿仍在 BladeTetra 公开 `SoulLegacyDamageGuard.apply` 内，directEntity 为 null，保留玩家间接归属。观察到原生终月脉冲后暂停领域伤害，不创建第二套处决，不加入终月手动贡献。

## 默认策略和安全限额

服务器权威文件：世界 `serverconfig/maid_weapon-soul-server.toml`。旧 `bossAdapters` 与新 `knownBossAdapters` 均可关闭专用计时器/生命适配；两者都开启才授予该权限。客户端和服务端需一起更新，新增魂核同步通道独立于原剑雨协议。

- `STANDARD`：基础防御/冷却穿透，保留常规事件取消和降额。
- `AGGRESSIVE`（默认）：额外提供强穿透标签、局部抗取消/降额和已验证前置计时器读取覆盖。
- `UNRESTRICTED_EXPERIMENTAL`：请求系数提高，并允许已注册、精确版本适配的一次残差补偿；未知生命结构不猜字段。

普通空间斩基础为攻击属性 × 1.25 × 成长系数；已知原生单次限额可作为向上参考，默认尝试其 1.75 倍，但参考贡献最多基础攻击的八倍，不因百万血量而无限放大。Lv5 才开始使用原有五通道真实反馈选择，Lv1 已有基础侵彻；Lv10 的成长系数最高。

魂核总请求独立计算：攻击 × 5 × 成长，加最大生命的 0.5%，生命比例部分最多攻击的十二倍；保留普通斩两倍的最低爆发参考，再受独立总安全上限约束。普通目标不凭空获得百万 Boss 的比例额度。

默认安全上限为单次 128 HP、目标共享每 20 tick 512 HP、玩家每 20 tick 384 HP；滚动 200 tick 目标 3072 / 玩家 2048 HP。普通斩同目标最短四 tick。爆发请求总上限 512 HP，预留时仍受玩家/目标共享窗口约束，每段仍受单次上限；失败和部分吸收仍全额计费。它们是有限的防重复安全栏，不再是旧 4/4/6/24 HP 或 Boss 原生限伤百分比天花板。

没有蓄积奖励：空闲只释放滚动窗口内已过期的费用，不可积攒超额爆发。预算由玩家/目标 UUID 共享，领域 session、武器物品、模式切换和救援都不能重置。正常停服清理短期伤害运行时；Lv8 180 秒持久救援冷却保持原 SavedData 实现。

## 精确 Boss 入口

Cataclysm 3.31 的 `LLibrary_Boss_Monster.hurt` 在 `bypasses_invulnerability` 时直接进入父类，跳过原生单次 cap、距离限伤和 damageBucket。Ignis 若干阶段减伤/拒伤同样识别该标签；不改阶段初始化或动画状态。

Legendary Monsters 2.1.15 的 `IAnimatedBoss.addDamage` 以累计伤害驱动公开 HP，普通 setHealth 无效；该 API 的强标签分支接受未压回原生 cap 的数值并同步生命。Cloud Golem 在最早 hurt 入口无条件读取 `GolemInvulnerabilityTime`，故另有版本和字节码双校验的可选 Mixin，只在本次精确来源读取时返回零，不改共享字段。Obliterator 普通计时器分支识别强标签；初始化、振刀和实体替换仍可能拒绝。必要初始化最多延后 20 tick，总爆发期限 32 tick，不无限排队。

Goety 2.5.58.4 Apostle 在 hurt 前置检查 `moddedInvul`/`obsidianInvul`，在 actuallyHurt 又检查 moddedInvul。可选源感知读取覆盖针对这三个真实位置，字段保持原值；第二阶段必要初始化不跳过。RevelationFix 更深的全头衔/Nether/剧情拒伤并不等同于这些计时器，不能据此宣称全部可破。

可选 Mixin 目标缺失、版本或字段/方法不匹配则不启用。普通 Java 主逻辑不链接可选 Mod 类型。适配器只注册在既有 `DomainTargetCompatibility`，没有并行的冲突注册表。

实验补偿优先正常 hurt，只有稳定阶段、仍有效存活、真实损失不足 65% 且精确适配时，一次性补齐剩余额度：Ignis 使用已核实的公开生命/死亡路径；LM 仅 Cloud Golem/Obliterator 使用 `addDamage` 累计生命 API。没有最大生命削减、猜测同步字段、无限值、强制删实体或通用防复活。默认 AGGRESSIVE 不执行直接生命补偿。

## 断曜流光字节码研究

输入为用户上传原始 `GoetyRevelation-2.3.4(1).jar`，SHA-256 `553b9c6aee6da1cf74600dc22d8ff482c77ac98ebc1d6024ae13fde70c739341`，内嵌 RevelationFix 4.6。只读 javap/CFR 研究，无反编译源码或 JAR 提交。

实签名：`DamageSourceInterface.revelationfix$setBypassAll(boolean)`；`LivingEventEC.revelationfix$hackedUnCancelable(boolean)`、`revelationfix$hackedOnlyAmountUp(boolean)`。`ValetteinItem.onAttack/onHurt` 对来源和当前事件设置这些标记；onDamage 设置事件标记。DamageSourceMixin 对指定原版 bypass 标签返回真。Hurt/Damage 的 setAmount Hook 在 onlyUp 标记时取 max(现值,输入)，并非自动知道前置 Boss 原生上限。LivingEventMixin 忽略已标记事件的取消操作。

另有 `IInvulnerableTickEntity` 的 `revelationfix$get/setCustomInvulTicks`、`get/setCustomHurtTicks`；Apostle 实现会映射普通和 Apollyon 的不同计时器。Valettein 还存在随机分支、原版计时器处理、当前生命乘 0.97、最大生命乘法削减以及五 tick 玩家冷却。它的 `EntityActuallyHurt` 可自建伤害/归属/死亡流程，并通过探测生命同步字段处理特殊结构；本实现没有复制这套流程，也不采用未知字段写探测、无限伤害或最大生命削减。

本项目独立实现精确来源上下文、有限额度、真实生命反馈及已核实入口，借鉴侵彻思想，不复制 ARR 源码。

## 可复现验证

```sh
gradle --no-daemon build -Pci=true
# 使用已取得的本地可选依赖，永不打开用户存档
python tools/run_soul_damage_tests.py --gradle gradle --bosses
python tools/run_soul_damage_tests.py --gradle gradle --goety
python tools/run_akatsuki_visual_test.py --gradle gradle --soul --record
python tools/run_akatsuki_visual_test.py --gradle gradle --rain --record
```

真实运行结果、HP 表、环境和限制见 `AKATSUKI_SOUL_VALIDATION.md`。软件 OpenGL 截图/MP4 是真实客户端帧，但不是独显性能或真人多人联机证明。不能用 hurt 返回值、编译通过或特效存在替代扣血验收。
