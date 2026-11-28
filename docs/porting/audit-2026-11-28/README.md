# 正式可玩版本缺口审计（2026-11-28）

本轮结论：**尚未达到正式可玩移植或完整玩家验收条件**。已有原生契约权威、支付、成长与白狐部分能力基础，仍缺正式获得入口、多数剑灵执行器、装备/任务投影、内部空间，以及生产客户端的完整资源和交互闭环。隔离原型证明部分旧资源可以继续使用，不能把原型显示成功算作功能交付。

## 基准与实物

重新查询远端，正式 `master` 与 `1.21.1-neoforge` 均为 `d1429a46492783c4e773d6ccb17b606ca48f5113`；PR [#38](https://github.com/Bluelgin/Contract-Blade/pull/38) 审计起点 `2955432290cb6475e8dfceb960e9a0c388be085f`，PR [#39](https://github.com/Bluelgin/Contract-Blade/pull/39) 为 `c31d2bf2e3a3918e03eb1a2c17ed65662b930118`，均 OPEN。历史已知失败不是当前状态：两 PR 最近已完成 CI 分别为 [46083092596](https://github.com/Bluelgin/Contract-Blade/actions/runs/46083092596)、[46046671641](https://github.com/Bluelgin/Contract-Blade/actions/runs/46046671641)，必要任务成功；跳过的可选视觉任务不算通过。

正式分支版本是 1.2.1；GitHub 最新公开 Release 是较早的 v1.0.6。分别检查精确 master 重建 JAR、实际下载的 v1.0.6 JAR 和 #38 生产测试 JAR，而不是把 Release 当作 master 的完整替代。路径、SHA256 见 [JAR_PROVENANCE.json](JAR_PROVENANCE.json)。精确 master 的 Java 17 `build` 本轮成功，175个测试、零失败/错误/跳过（这与#39含共享测试的182个是不同基准）；两个视觉实验也实际运行了对应 Minecraft 客户端。

NeoForge 的 `sourceSets` 只编译 `src/port/java`、`src/shared/java`，只打包 `src/port/resources`、`src/shared/resources`。留在工作树中的 `src/main` 不是新平台实现。实际 #38 JAR 中没有 `assets/maid_weapon/`、正式模型包与相关美术许可；只有开发命名空间物品模型/语言及三份共享成长 JSON。因此缺口是加载、执行、同步和资源整体未接通，不能只复制目录后标记完成。

## 审计交付

- [功能完整链路](FEATURE_FLOW_AUDIT.md)：按正常玩家操作追踪入口、执行、存储/网络、显示和验收；与 [主功能矩阵](../PARITY_MATRIX.md) 配合。
- [实际注册、Mixin与联动边界](REGISTRY_AND_COMPAT.md)：旧版运行快照与新平台源码对照，列出原有桥接Mod和Boss专用适配。
- [安全、数据与性能边界](SAFETY_AND_PERFORMANCE.md)：区分普通支付setDirty与转移日志fsync、正常重启与崩溃安全、现有网络与正式同步缺口。
- [配置逐项缺口](CONFIG_GAPS.csv)：八个实际配置类中的定义参数原文、字段/键与旧调用候选；不把共享默认值当成已迁移用户配置。
- [美术与资源审计](ART_AUDIT.md)：当前运行、历史源素材、撤下内容及许可边界。
- [逐文件清单](ASSET_INVENTORY.csv)：每个资源和模型 ZIP 内部文件的来源、ID、哈希、三种 JAR 分发状态、引用候选、许可、缺口及验收方法。
- [源素材/历史清单](AUTHORING_INVENTORY.csv)、[代码生成视觉清单](PROCEDURAL_VISUALS.csv)：不把仓库预览、源文件或 Java 绘图遗漏，也不整目录发布。
- [真实客户端实验](EXPERIMENTS.md)：原型源代码、实机截图、重载结果及严格的证据边界。
- [下一批开发任务](NEXT_TASKS.md)：按玩家流程交付，不按文件搬运进度交付。
- [对应版本依赖调查](../COMPATIBILITY_1_21_1.md)：发布元数据与实际接入/兼容验收严格区分；有版本不代表已兼容，未测试也不自动归为外部阻塞。

CSV 中的“引用候选”是可复核检索线索，可能包含注释、间接引用或动态拼接遗漏；它不自动证明运行可达。分组运行链和真实证据在上述 Markdown 中说明。文件存在、JAR 路径存在、模型 ID 可枚举、服务器通过、隔离效果能画出，都不能单独证明完整可玩。

本轮未修改/合并正式分支、未创建 Release、未操作玩家正式旧存档。原型只放在独立临时检出，正式 PR 仅保存审计工具、报告和证据，不把诊断覆盖层装进产品。
