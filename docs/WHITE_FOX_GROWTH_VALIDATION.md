# White Fox Growth Rework 验证记录

基线：PR #16 `9f9ca02b69a83c703964ec485f484e3933efcf08`。
独立分支 `codex/white-fox-growth-rework`，PR 目标为 `codex/architecture-refinement`。
以下结果来自本次实际执行；验证入口不会把缺失 PASS 标记当作通过。

## 实际执行结果（2026-10-14）

| 验证 | 结果 |
| --- | --- |
| Java 17 / Gradle 8.7 构建、打包、重混淆 | PASS；`clean build -Pci=true --offline`，48 秒，10 个任务执行，包含重混淆 |
| JUnit | 29 项，0 failure / error / skipped；白狐成长 5、白狐运行时 3、恢复账本 9、全局熟练度 7、黑狐成长 5 |
| 核心源码/数据校验 | 17 个校验脚本 PASS；资源验证器 4 项行为测试 PASS |
| 新白狐专服夹具 | WHITE_BLESSING_PASS |
| 新白狐同世界真实停服/重启 | WHITE_BLESSING_RESTART_PASS；誓约与危急长冷却均保留 |
| #16 事务/恢复事实/驻留权限回归 | CONTRACT_ARCHITECTURE_PASS |
| 生命周期回归及同世界重启 | CONTRACT_STAGE_THREE_PASS / CONTRACT_STAGE_THREE_RESTART_PASS |
| 黑狐原生剑核、成长及多人归因 | SWORD_CORE_NATIVE_PASS / SWORD_CORE_RELEASE_PASS / SWORD_CORE_MASTERY_CREDIT_PASS / SWORD_CORE_GROWTH_PASS / SWORD_CORE_MULTI_CREDIT_PASS |
| 白狐 Boss 专用净化之刃 | WHITE_FOX_SE_PASS；原生 SE 注册、补强、1% 净化及目标冷却不变 |

实际使用 Forge 47.4.10、TLM 1.5.3、SlashBlade 1.9.65，无 BladeTetra。
测试依赖 SHA-256 与 #16 一致：

- TLM：`8c341567e57aa3c006f9fa85c7a5e3bc36a53b1cca4e3538c3db705eeeea1bbc`
- SlashBlade：`2a6eb8d2ead295cb3838b551b9f3cf0e81de97966dbc91b84cf2c7589f3a4361`

测试创建可丢弃世界，没有读取用户存档，所有专服串行运行。
本次执行日志目录：白狐 `/tmp/contract-stage-three-oysjk9ut`，事务 `/tmp/contract-stage-three-_yhhnnno`，
生命周期 `/tmp/contract-stage-three-vqmm8t_r`，剑核 `/tmp/contract-stage-three-ndeis5j8`，净化之刃 `/tmp/contract-stage-three-rfz9_ynt`。
这些是运行时临时日志路径，不是稳定下载地址；上面的入口可重新生成证据。

初次编译发现验证器局部变量重名，已修正。
初次专服视觉生命周期断言失败：直接调用 entity.tick 不递增实体年龄；
改为 ServerLevel.tickNonPassenger 的真实实体 tick 入口，保留所有断言，重新执行整组白狐及其重启后通过。
没有删除断言、延长视觉生命周期或用 Gradle 的 BUILD SUCCESSFUL 代替玩法 PASS。

## 自动化入口

```sh
gradle --no-daemon clean build -Pci=true
python tools/run_stage_three_tests.py --gradle /absolute/path/to/gradle --fixture blessing
```

后者创建临时世界，加载 Forge 47.4.10、TLM 1.5.3 和 SlashBlade 1.9.65，
要求 `WHITE_BLESSING_PASS`，然后停止并重新加载同一个世界，要求
`WHITE_BLESSING_RESTART_PASS`。任何非零退出、`_FAIL` 或缺失标记均失败。
依赖放置：`libs/touhoulittlemaid-1.5.3-forge+mc1.20.1.jar`、
`tmp/compat-test/SlashBladeResharped-1.20.1-1.9.65.jar`；仅运行依赖，不打包/提交。

JUnit 覆盖 JSON/fallback 十级等价、每级解锁、护主绝对上限、各短冷却期限、
换手不重置预算、灵愈命中去重/累计/过期/冷却、主副手合法命中模式、
誓约与危急独立持久化、未知未来 Schema 保留且拒绝消耗。
真实权限和女仆生命周期通过 Forge 夹具覆盖，不用纯布尔测试冒充原生授权验收。

专服夹具使用 FakePlayer、真实原奖励武器、真实 TLM 女仆/存储、正式成长数据、
真实 SlashBlade 视觉实体；用最终伤害事件注入做精确数值断言。
包含十级护主/机动/吸收、净化白名单及冷却、六命中灵愈、轻斩击、
只治疗己方一名女仆、陌生/未授权/非原刀/空驻留/普通同名刀拒绝、
发射证明与射手篡改、Lv6/Lv7 副手黑白边界、原生黑狐剑核共存及显现/收回、
誓约优先级/净化/吸收、白狐真实显现停止加护、危急独立冷却、
断线/死亡/换维度事件清理及真实保存重启持久冷却。
保留原有真实耐久损毁释放与原女仆迁居回归夹具。

## 未覆盖边界

FakePlayer/事件注入不等于图形客户端或真人多人实机验收。
需要真人确认刀光位置、颜色、声音和触发辨识度；没有新增复杂美术资源。
没有验证生产重混淆 JAR 的启动、全部可选 Mod 组合、强杀/断电、
外部 Mod 绕过 Forge 事件或在同优先级后继续改写/取消最终伤害。
本路线不修改既有生命/归因/契约权威，不恢复副本数量封锁。
正常停服在玩家保存前清理本路线临时盾；崩溃后原版已保存的短时药水效果
可能按剩余时长恢复，不声明断电场景下临时状态精确清零。
