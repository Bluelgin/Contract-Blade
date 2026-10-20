# 万华御札：符卡战斗代码与美术交付接口

基线 PR：#27；本文件描述 **Java 已提交的职责**，不代表 Astra 美术资源已经合入。

## 功能边界

- 玩家全局 `ContractMastery` 解锁：Lv.1 自动协奏 3 枚、Lv.2 4 枚、Lv.3 灵针、Lv.4 追踪、Lv.5 散华、Lv.6 护身（5 次拦截）、Lv.7 散华 12 枚/波与护身 8 次、Lv.8 连击协奏 6 枚、Lv.9 散华 15 枚/波与追踪、Lv.10 幻想万华。
- 灵针、散华、护身、幻想万华仍分别花费池上限的 6%/10%/16%/32%，保留服务器授权、有限队列、逐弹原生碰撞、2 tick 目标受击窗口、友伤过滤和 Boss 保护。
- `DanmakuGrowth.waveCount` 是唯一波次数来源：灵针 2、散华 2、护身 0（独立防御会话）、幻想万华 4。第四波不再被限制在 index 0–2 的旧入口拦截。
- `DanmakuPatternPlanner` 提供可单测的五瓣波形角度和四阶段递减花阵半径；几何计算本身无世界扫描和随机状态。
- `DanmakuShotQueue` 现在把原始有效女仆身份绑定到生成的每颗弹幕，`ContractDanmakuProjectile` 在真实碰撞时再次核验。飞行中更换契约不能借新女仆权限兑现旧弹幕伤害。
- `DanmakuSpellEffects` 在服务端以有界 vanilla 粒子提示法阵与波次，所有附近玩家可看见；`DanmakuVisualController` 通过服务端确认的 `VisualCue` 向施法者 HUD 显示短暂符卡宣言。视觉演出**不**控制碰撞、伤害或费用。

## 美术团队（Astra）边界

Astra 使用独立分支，提交 SVG / PNG / mcmeta / 导出脚本及预览，不修改上述 Java 文件。

交付建议路径：

- `assets/maid_weapon/textures/gui/danmaku/wheel_base.png`：无文字底盘，256×256，透明。
- `.../icons/needle.png`、`flower.png`、`guard.png`、`fantasy.png`：32×32 RGBA。
- `.../effects/needle_glyph.png`、`flower_glyph.png`、`guard_glyph.png`、`fantasy_glyph.png`：64×64 RGBA。
- SVG 源文件统一放 `art/danmaku/`。

*这些文件目前仅是约定名称，不要误认为已经存在或已加载。* Astra PR 提交后由代码作者完成正式绑定、贴图降级和客户端实机验证。当前所有战斗逻辑在缺少这些文件时仍可用。

## 验收标准与尚待完成

1. 等级解锁验证：Lv2/3、4/5、5/6、6/7、9/10 的服务器和轮盘一致性。
2. Lv10 实际产生四波共 32 枚（而不是只校验配置中的 32）；在实体限额、队列限额、Boss 保护存在时可以合理减少实际弹数，且不能越权扣费。
3. 普攻追踪、散华双波、护身拦截和终幕协奏的数值回归。
4. 原始女仆身份在队列、已生成投射物以及存档读回三处有效；缺失身份一律拒绝伤害。
5. 真人客户端检查轮盘按键、首次符卡解锁、HUD 层级、透明度、粒子遮挡、多人同步、TLM 原生模型。
6. 2/8/16 人整合包 TPS 实测。现有 CPU/JUnit 与静态 CI 无法代替实机结果。

符卡宣言的正式名称及各语言翻译将在美术稿确定后与客户端资源接入一并完善；目前保持现有语言键，避免并行美术分支冲突。
