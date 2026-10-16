# 赤月普通空间斩：十级成长与创造模式测试

基于 PR #19 HEAD `6a350305dfdd25671fa6958a27098b246fa8b1d7`，只在 Contract-Blade 修改。

## 成长策略

普通空间斩使用玩家 `Attributes.ATTACK_DAMAGE` 的值乘成长倍率，而不是推测被 Boss 限伤的债务。附魔/额外伤害如果未反映在该属性中，不会自动成为额外倍率输入。

| Lv | 面板倍率 | 单次普通斩等级上限（HP） |
|--|--:|--:|
| 1 | 5% | 1 |
| 2 | 8% | 2 |
| 3 | 12% | 4 |
| 4 | 16% | 6 |
| 5 | 20% | 10 |
| 6 | 25% | 16 |
| 7 | 30% | 24 |
| 8 | 36% | 40 |
| 9 | 42% | 64 |
| 10 | 50% | 无限等级上限 |

新 progression 与 Java fallback 使用相同参数 `space_cut_multiplier` / `space_cut_level_cap`，其中 cap=0 是专门定义的“无等级上限”标记，不写 Infinity。Lv10 普通斩也绕过全局单次/窗口数值预算，但是继续共享冷却、魂核轰击锁、攻击事实与目标限制。每次尝试仍计入共享账本，严格限伤追偿与魂核保留现有计费与额度上限。普通斩仍走原生防御和合法阶段规则。旧 `baseSpaceCutAttackFraction` 等配置键为兼容保留，不再主导普通斩。

## 创造模式、假人

- `allowCreativeDomainDamage=true`：默认允许合法持刀、剑灵驻留、通过授权的创造模式玩家实测领域伤害；该配置允许关闭。不能绕过来源证明和契约身份。
- `allowTrainingDummyTargets=true`：玩家主动近战接触 registry path 含独立 `dummy` / `mannequin` 词段的 LivingEntity 才允许成为短暂测试目标。它们不会被自动 AoE 扫描；仍禁攻击友方、驯养生物、玩家。
- `additionalTrainingEntityTypes=[]`：不符合默认命名的假人可添加准确 `namespace:path` 注册 ID。非 LivingEntity、免疫伤害/自定义虚拟血量的假人仍可能只显示特效不扣真实 HP。

## 待实机验证

生存/创造分别打循声守卫者；Lv1/5/9/10 分别验证等级请求与真实 HP。Lv10 100 面板请求 50 HP；10000 面板请求 5000 HP，不再受原128/384 HP预算削减。确认训练假人只能主动接触触发，不会自动溅射宠物/村民；对 Cataclysm / LM / Goety 重新验证原始限伤差额、魂核、四段及多人冷却。打开 `damageDiagnostics` 区分受权/扣血/拒伤原因。源代码/CI 验证不能代替生产 Forge、真人双客户端和完整整合包验收。
