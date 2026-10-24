# 仓库目录与归档约定

## 目录职责

| 位置 | 内容 | 是否作为发布附件 |
| --- | --- | --- |
| `src/main` | 正式代码、运行资源、必要署名许可 | 由构建规则选择进入 JAR |
| `src/test` | 单元测试 | 否 |
| `tools` | 构建、转换、检查和隔离验证入口 | 否 |
| `docs` | 玩家指南、维护说明和历史验证记录 | 按需链接，不整目录塞入 JAR |
| `art` | 原始美术、可编辑文件、许可来源和历史试稿 | 不等于运行资源，不整体打包 |
| `build` | Gradle 构建结果 | 本地生成，不提交 |
| `dist` | 整理后的 mod / YSM 发布附件 | 本地生成，不提交 |
| `tmp`、`run-*` | 缓存、隔离测试世界、日志和历史恢复文件 | 不提交，不用于玩家安装 |

## 发布材料

- 可维护的说明留在 `docs`，不要只写在被忽略的 `dist`。
- 代码仍按 MIT；第三方模型和动作遵守各自许可。资源包白名单 ZIP 是有意跟踪的源资源，
  不能因为扩展名是 ZIP 就删除。
- 正式版本号仍为 1.2.0；YSM 双狐包独立编号 1.1.1。整理仓库不等于上传发布附件。
- 发版前执行构建、产物检查，并按验收清单说明未覆盖的实机与多人边界。
- 不将本地依赖 JAR、私人游戏配置、测试存档或分支备份 bundle 上传到仓库。

## 素材归档：先标明用途，再考虑搬动

当前素材导航见 [art/README.md](../art/README.md)。历史试稿保留原位置，
已退役音乐实现集中在 `art/black_fox/music/retired-runtime`；它不是正式 Java 源集。
赤月源模型、历史包和音乐来源记录仍保留，但不随 1.2.0 正式包提供。

移动前应搜索代码、脚本、文档和 CI 的旧路径，更新所有引用并验证重建结果。
本次不批量移动或删除未提交试稿，不重写 Git 历史。删除当前文件不能抹去历史体积；
若将来拆出素材仓库，应单独规划，保留许可和可重建版本，不能直接 force-push 清历史。

## 分支维护

- `master` 为 Core 主线；新增功能或维护使用短期 `codex/...` 分支。
- PR 合并后，仅在分支没有主线之外的提交、没有活动 PR、没有工作目录占用时清理。
- `codex/part-core-archive` 为旧 Part 源码引用，保留。
- `codex/core-only-repository`、本地 `codex/1.12.2-legacy` 仍有其他工作目录使用，保留。
- `codex/contract-interior` 在本次核对时有九个主线未包含的提交，暂不删除或恢复旧行为。
- 仓库清理不得丢弃工作区改动，不通过 reset、clean 或批量删除来让状态“好看”。

本次分支历史备份位于本机 `tmp/branch-cleanup-20261024-before.bundle`，不作为发布资源。

## Maintenance summary

Runtime sources, tests, tools, documentation, and editable art have separate roles.
Builds, staged distributions, local dependencies, and disposable test worlds stay out
of Git. Preserve upstream attribution and unfinished source assets. Move referenced
assets only with all consumers updated and a rebuild checked. Branch cleanup never
authorizes history rewrites, loss of uncommitted work, or deletion of unique commits.
