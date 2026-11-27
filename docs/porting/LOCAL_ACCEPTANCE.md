# 本地开发验收（非正式游玩版）

本轮构建仍是 `maid_weapon_port_probe`，不是完整移植包。不要放入正式旧存档，也不要与 Forge 1.20.1 JAR 混装。

## NeoForge

建立全新实例：Minecraft 1.21.1、Java 21、NeoForge 21.1.219、Touhou Little Maid `1.5.3-neoforge+mc1.21.1`，将 `contract-blade-1.21.1-PORT-PROBE-NOT-FOR-PLAY-0.0.0-port-dev.jar` 放入 mods。它只提供开发入口；不存在正式缔结台、黑狐/赤月/万华完整技能或内部空间。

源码验证：

```
python tools/porting/check_shared.py
python tools/porting/test_shared_patch.py
./gradlew --no-daemon build
python tools/porting/smoke_server.py
./gradlew --no-daemon runClient
```

smoke 脚本只创建 `run/port-smoke-server` 的一次性世界，通过本地 RCON 执行真实 TLM 生命周期与重启夹具，不读取正式玩家存档。不要手工在该目录存放有价值的世界。完整测试列表与断言见脚本及 `NativeTlmRoundTripCommand`；管理指令仅用于隔离夹具，不是正式玩法入口。

客户端验收先检查主菜单的 Mods 中是否加载 NeoForge、TLM 和 port probe。开发载体支持对自有女仆捕获、对方块显现、显现后右键空气归刃、潜行右键循环封存通道；O 键为开发完全显现入口。开发水晶不能代表正式白狐觉醒。任何未亲自执行的行为都不能标为实机通过。

## Forge

Java 17，固定原生依赖见 Core CI：TLM 1.5.3 Forge、TaCZ 1.1.8 仅用于相关编译/投影夹具。

```
./gradlew --no-daemon build -Pci=true
python tools/run_stage_three_tests.py --gradle ./gradlew --fixture resonance --compat vanilla
```

resonance 夹具必须同时有共鸣、完全显现、辅助饰品三项 PASS，并执行第二次服务端启动验证持久化。保留完整日志；任何缺标记、异常、跳过或取消均不等于通过。

## 已知边界

没有已验证的 1.20.1 → 1.21.1 正式世界导入工具。旧 ForgeCaps、嵌套第三方物品及内部空间不能自动升级。完整未完成项见 `PARITY_MATRIX.md`；第三方已发布版本与实际联动分开记录于 `COMPATIBILITY_1_21_1.md`。本轮不得发布 Release 或替换正式分支。

普通载体首次显现时会经服务端账本校验升级为通道载体；显现期间保留引用，HUD 可显示 live 余额。归刃只回收当前选中、已加载且属于该主人的女仆，不强制加载区块；跨维度实体仍由生命周期校验拒绝。新增 `PORT_NATIVE_PLAYER_CARRIER_PASS` 必须通过真实物品入口验证这条路径与旧副本拒绝。
