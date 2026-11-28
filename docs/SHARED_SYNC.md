# 共享源码同步

平台源码保持各自版本独立维护。公共文件边界：`src/shared/java`、`src/sharedTest/java`、`src/shared/resources`。不得将注册、Minecraft API、Mixin、网络或渲染放入这些目录。成长 JSON 只有在两平台语义与格式相同时共享。

从**目标工作树**运行，先提交目标已有工作，使用经过验证的具体 commit：

```
python tools/porting/shared_patch.py BASELINE SOURCE
python tools/porting/shared_patch.py BASELINE SOURCE --apply
# 审查变更，运行共享测试及受影响平台验证，然后单独提交
python tools/porting/shared_patch.py BASELINE SOURCE --check
```

默认只检查补丁适用性；`--apply` 不暂存、不切换分支、不推送。输出记录源、目标和公共基准的完整 SHA。冲突或工作树不干净时拒绝应用。`--check` 比较目标 HEAD 与源 commit 全部共享树，发生漂移返回非零；它不是仅检查增量补丁。平台文件即便在源 commit 中变化也不会复制。

提交说明应记录来源 SHA、同步方向、公共基准及两个平台的验证结果。公共语义变更先验证再同步；不能把 NeoForge 尚未验证的新玩法反向覆盖 Forge 正式逻辑。必要的平台配置仍由 adapter 传入，不能用共享默认常量覆盖用户配置。

`check_shared.py` 用 `javac --release 17` 独立编译公共源码；两个分支 CI 都执行边界检查和真实 Git 临时仓库同步回归。平台构建分别使用 Java 17 和 21。
