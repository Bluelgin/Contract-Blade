# 契约配置

新增世界级规则位于 `存档/serverconfig/maid_weapon-server.toml`。专用服务器为
`世界目录/serverconfig/maid_weapon-server.toml`，由服务器决定并通过 Forge 同步客户端。
启动一次存档后生成。推荐停服修改并重启；不要直接在游戏运行中保存配置测试。
整合包可将模板放入 `defaultconfigs/maid_weapon-server.toml`，供新建世界使用。

原有 `config/maid_weapon-common.toml` 不迁移、不覆盖，恢复/消耗、喂食安全判定、
等级基础公式和好感度配置继续在那里调整。common 配置不是客户端同步配置；
需要准确统一显示的每级伤害请使用新的 server `damageBonus` 覆盖值。

## 自动显现

`Manifestation.autoManifestWhenHurt` 默认 **false**，开启才会受伤自动显现。
这项是有意调整：此前受伤显现没有开关，现在可由玩家/服主选择。
只处理生物造成的真实失血，不处理环境伤害、取消伤害或致死伤害。
仍需手持自己的可显现契约，有足够共鸣，且未达到跟随人数上限。
不会强行召回居家的女仆或从其他维度复制实体。

- `hurtCooldownTicks`：默认 200（10 秒），仅成功自动显现后开始冷却。
- `hurtMinimumResonance`：默认 1；手动显现不受此额外门槛影响。

## 同时跟随人数

`Manifestation.maxFollowingMaids` 默认 1，范围 1～8；设置为 2 即可正式同时召唤两位契约女仆。
手动与受伤自动显现共用上限。家模式和待命不占跟随名额；切回跟随后会在维护时检查，
人数已满就恢复原先驻留选择并提示。旧存档已超限或运行中降低上限时，多余女仆会安全待命，
不会删除、复制或强制吞回实体。重复契约引用不会重复计数。

活动记录按玩家与契约分别保存；召回、投掷、损毁救援及共鸣耗尽只清理对应女仆。
死亡、退出和跨维度会逐一召回跟随者，保留驻留者。未加载实体不会从引用克隆；
区块恢复请求按契约排队且维护其他女仆不中断。已有武器 NBT 无需迁移。

## 饰品工作（1.2.0）

共鸣剑穗、同心结与无尽契约不再自动切换战斗或喂食工作，也不修改作息。
装备投影仍保留；工作请在女仆界面手动选择。旧接管快照会恢复一次并清理，之后不覆盖新选择。
`AutomaticWork.combatTask` 和 `feedingTask` 作为旧配置兼容键保留，默认 false；
即使旧文件为 true 也不再生效，无需删除整个旧配置。

## 共鸣

`Resonance.poolMaximum` 默认 1000，是当前服务器共鸣池上限；降低上限后按新值限制，
提高上限不会直接补满。`Resonance.maximum` 默认 200，仅保留旧字段迁移兼容，
不是当前池上限。`Resonance.killRecovery` 默认 5，控制击杀恢复量，设为 0 可关闭。
恢复、消耗及旧配置迁移详见 [SPIRIT_RESONANCE_COSTS.md](SPIRIT_RESONANCE_COSTS.md)。

## 等级成长

每一级对应 `Growth.level1` ～ `Growth.level10`，表示“升到本级”的条件。
默认 `mode = "legacy"`，完全保留原成长规则、旧等级和龙/凋灵统计。
`level1` 只使用伤害设置。自定义规则不会回退已有等级。

- `mode = "tier"`：击杀强度不低于 `requiredTier` 的目标。
- `mode = "entities"`：击杀 `entityIds` 中任意一种目标，可以填其他 mod 的 Boss 注册 ID。
- `requiredKills`：自定义模式需要的合格击杀次数，只在当前等级累计。
- `damageBonus`：本级额外攻击力；默认 -1 沿用 common 等级公式，0 表示无等级加成。

例：升到 6 级时要求击杀凋灵或监守者，共两次，并提供 7.5 额外攻击力：

```toml
[Growth.level6]
mode = "entities"
requiredTier = 5
requiredKills = 2
entityIds = ["minecraft:wither", "minecraft:warden"]
damageBonus = 7.5
```

entities 模式不使用 requiredTier；列表为空时不会升级。ID 必须是实体注册 ID，
拼写正确但未安装的实体不会匹配。当前成长进度由服务器按玩家保存，不随重启丢失，
更换武器不会重置；旧武器成长字段仅用于兼容迁移。
修改规则会保留已累计次数；需要一套全新挑战时请使用新契约测试。
每次击杀最多升一级，不拿此前攻击过的其他 Boss 冒充本次目标。
既有“攻击后没有死亡才结算”的挑战判定仍保留。

## 验证

隔离 companion 测试覆盖共鸣上下限、旧字段迁移、实体列表、次数存档、等级伤害、
legacy 规则、受伤开关/冷却/共鸣门槛；projection 测试覆盖手动工作保留与旧快照恢复。
这些验证仅在显式开启开发测试参数时执行，不进入玩家存档。

多女仆隔离测试覆盖默认单人上限、家模式绕过与恢复、双显现、第三位手动/自动请求拒绝、
单独召回/强制召回/投掷/容器转移、死亡/退出/跨维度全体召回、运行中下调人数、
独立共鸣协同冷却和按契约恢复队列。损毁/挂刀台/复活回归另行验证。
