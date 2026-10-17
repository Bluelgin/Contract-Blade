# 黑狐挑战难度配置

配置文件：`maid_weapon-black-fox-server.toml`（Forge SERVER 配置，服务器决定并同步给客户端）。

- 单人已有世界：`saves/<世界名>/serverconfig/maid_weapon-black-fox-server.toml`
- 专用服务器：`<level-name>/serverconfig/maid_weapon-black-fox-server.toml`
- 整合包预设：把随附的 [示例配置](examples/maid_weapon-black-fox-server.toml) 放入 `defaultconfigs/`，供新世界复制。已有世界不会被该预设覆盖。

```toml
[blackFoxBoss]
    damageMultiplier = 1.0
    maxHealth = 400.0
```

只开放统一伤害倍率与最大生命值，不逐技能开放数值。

`damageMultiplier` 范围 0～10，1 是本次降低基础伤害后的默认水平，不是旧版伤害。0.75 表示再减 25%，1.5 表示加 50%，0 禁用 Boss 伤害。Forge 成功重载该文件后用于后续伤害；修改后重启世界/服务器是最稳妥的应用方式。

`maxHealth` 范围 1～1024，默认 400（200 心），遵循原版最大生命属性上限。新挑战生成时设置，当前 Boss 不会自动回血、重置血条或重算属性。半血/三成血强化仍按实际最大生命比例判断；最终核心依旧由耗尽生命触发。

## 本次基础调整

试探/反击 6→4，交叉与小斩 8→5，侧斩9→6，对空11→7；次元每轮18→10，站立居合60→14；封缄6、解封7、光束5、裂隙4；核心接触10→6，超时脉冲60→12。Boss 幻影剑设置值5→3、幻影刃8→5。原生连击B在原生计算后乘0.65。取消普通近战强化段额外的1.15倍。

以上为基础设置/原生计算倍率，不承诺最终扣血值；游戏难度、护甲、附魔及其他模组仍可影响结果。攻击频率、格挡窗口、五次破势与核心机制不变。

## 作用域与实现

固定基准集中在 `BlackFoxDamageTuning`；技能仅选用对应基准。统一倍率只在 `BlackFoxCombatEvents.difficulty` 的 `LivingHurtEvent`（护甲之前）应用一次，且必须是已登记 Boss 对本场挑战者造成的伤害。不会影响其他玩家、生物、玩家黑狐刀或奖励 SA。

原生连击B保留拔刀剑完整计算和实体，通过 `BlackFoxNativeDamageMixin` 对真实 AttackHelper 调用设置局部来源作用域，在同一伤害入口应用额外0.65倍。作用域在finally清理；不会用Boss当前技能猜测先前弹幕的来源，也不改全局拔刀剑伤害。幻影剑生成器只对 Boss 所有者采用新基准，玩家技能沿用原值。

血量仅在实体初始化设置，不在每tick或配置重载时重置。战斗表现、AI、格挡兼容桥不读取难度配置。
