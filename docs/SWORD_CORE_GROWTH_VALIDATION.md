# 封界剑核阶段二验证记录

基础：PR #11 `ced992e8e9c12b0829ed432b8be7db8704ecdea4`（包含 PR #10 `d10a309`）。
环境：Java 17.0.20.1、Gradle 8.7、Minecraft 1.20.1 / Forge 47.4.10；
原生夹具使用 SlashBlade Resharped **1.9.65** 和 Touhou Little Maid **1.5.3**（已读取实际 JAR manifest）。
所有专服世界均由运行器新建，只监听回环地址、随机端口，运行后停服。没有访问用户游戏存档。

## 已实际通过

| 验证 | 结果和覆盖 |
| --- | --- |
| Gradle `build -Pci=true` | 编译、资源、JAR、重混淆与 JUnit 通过；最终提交前另执行 `clean build -Pci=true --offline` |
| JUnit | **12 项，0 failure / 0 error**：保留第一阶段 7 项，新增 BlackFoxSwordCoreGrowthTest 的 5 项 |
| 十级定义测试 | 每级至少增加一项 feature，累积保留低级功能，正式 JSON 与 Java fallback 等价，严格词表/参数校验，READY/无召唤/denied/未注册安全边界 |
| 模式与优先级单元测试 | Lv.5 COMBO、Lv.6 OWNER_FOLLOW、Lv.7 三猎手、Lv.8 原生攻击类型、Lv.9 报复与 Lv.10 SYNC；每级默认 16/8 范围，参数上限和有限值防御 |
| 原生释放 `SWORD_CORE_RELEASE_PASS` | 实际 ItemSlashBlade.releaseUsing：Lv.1 召唤基础核，锁定的连击 B 不切换也不扣资源；Lv.5 双向反复切换；20/30 耀魂；399 tick 存在、400 tick 清理 |
| 原生 B 回归 `SWORD_CORE_NATIVE_PASS` | 完整 B1–B7 实际调用与真实伤害，友方不受伤，反复双向切换；高血量敌人损失 **274.1858** 生命 |
| 成长 `SWORD_CORE_GROWTH_PASS` | 真实玩家 SavedData Lv.1–10 查询；Lv.10 无奖励和过期账本 token 拒绝；Lv.5 活动核升级到 Lv.6 后中央刷新跟随；移动后平滑收敛于脚部 +7；12 格敌人弹幕合法、连击 B 拒绝；Lv.8 原生 TargetEntityId 优先；最多 3 名近战猎手 |
| 剑印/连锁 | 原生幻影实际命中添加剑印；真实标记目标死亡排入连锁；同冷却第二次死亡不额外入队；连锁经原生投射物执行 |
| 护主/报复 | 原生锁定/标记/靠近主人目标的优先级查询；通过真实 Forge EventBus 投递正数 LivingDamageEvent 检验 Lv.9 攻击者优先。后者是事件夹具，不是真人受击测试 |
| Lv.10 协同 | 实际原生切换开启窗口；夹具推进核心、剑影、投射物和世界时间 tick；附近敌人受到真实原生伤害；24 tick 窗口结束，不延长剑核寿命 |
| 四类原生弹幕 | 幻影剑保留原生 impact 回归；新增 Drive、纵向 Drive、WaveEdge 实际发射与命中造成伤害；WaveEdge count=1 验证恰好 2 枚；RGB 正确为 B04CE5 |
| 发射归因 `SWORD_CORE_MASTERY_CREDIT_PASS` | 实际原生幻影与范围斩在换手后保留发射时载体；射手改变不授予原玩家击杀；provenance scope 清理 |
| 多主人归因 `SWORD_CORE_MULTI_CREDIT_PASS` | 同服两个独立主人/剑核，换手后新 actor 仍持有召唤原刀；实际原生致死命中分别只给对应玩家 +1 击杀 |
| 独立胜利奖励 `CONTRACT_MASTERY_FOX_PASS` | 真实挑战终局、破坏实际终局核、原刀归还、胜利获得 SA，契约初始化查询 READY/Lv.10 SYNC；狐灵显现/收回和迁居回归通过。伴随 BLACK_FOX_SHRINE_RETURN_PASS |
| 无可选 Mod 专服 `CONTRACT_MASTERY_VALIDATION_PASS` | 第一阶段的全局共享/玩家隔离、迁移、真实死亡结算、独立能力资格等回归通过；不要求 SlashBlade/TLM 才能加载成长核心 |
| 原有检查 | 15 项源码/资源边界检查全部通过；SwordCoreTimelineCheck 通过；打包后 validate_mod_presentation.py 通过。源码检查与上述专服测试区分记录 |

新增 JUnit 并没有替代原生释放、B1–B7 或归因断言。原来的 Lv.1 完全体/无路线预期
更新为阶段二的 Lv.1 基础核与 Lv.5 模式解锁；真实胜利夹具更新为 READY/Lv.10 完整路线。

## 过程中修复与验证边界

- 首次编译发现 actor 不能访问 compat 包内部 resolveBladeState；改用既有公开紫色刀状态桥接方法，编译通过。
- 首次颜色断言错误地将原生 WaveEdge 的 ARGB 与 24 位 RGB 直接比较；实际返回 `0xFFB04CE5`，
  原生 DriveRenderer 也使用 `& 0xFFFFFF`。修正验证的 RGB 掩码后，四类原生颜色和伤害通过。
  没有删除颜色断言或通过修改伤害/评级掩盖失败。
- Native WaveEdge 的提前加入世界时序已用真实 scoped actor 补上 birth owner/launch credit；保留原生攻击与命中实现。
- 未运行图形客户端、多真人联网或生产重混淆 JAR 启动。紫色数据、原生着色分支与网络状态已实现，
  **不声称已完成视觉截图、客户端低评级渲染或真人手感验收**。
- 原生客户端持刀低评级紫色分支由 mixin 实现；实际图形颜色、环绕效果和延迟环境仍需要客户端检查。
- 第一阶段已记录的完整黑狐 Boss/旧场景/旧模型白名单基线失败不在本次修复范围；本次没有删除其断言，
  也没有重跑失败的整场夹具并宣称通过。聚焦真实终局奖励不代表人工完成整场 Boss 战。
- 初次初始化 Maven/Forge 缓存及本机 JDK 用时较长，曾因旧代理与离线缺少 JUnit 依赖失败；
  这些环境问题处理后，最终构建与专服结果按明确 PASS 标记记录。

## 复现

准备以下**本地测试依赖，不打包/提交第三方 JAR**：

- `libs/touhoulittlemaid-1.5.3-forge+mc1.20.1.jar`
- `tmp/compat-test/SlashBladeResharped-1.20.1-1.9.65.jar`

```sh
gradle --no-daemon clean build -Pci=true
python tools/validate_contract_mastery.py
python tools/run_sword_core_growth_tests.py --gradle gradle
```

运行器要求每个场景的全部 PASS 标记，不能仅凭 Gradle JavaExec 成功判断游戏验证成功。
缓存齐备时加 `--offline`；定位单一问题时加 `--fixture sword-core`、`fox-reward` 或 `mastery`。
完整运行包括剑核、真实胜利奖励和无可选 Mod 熟练度三个新世界。日志/世界仅留在临时路径，不提交。
