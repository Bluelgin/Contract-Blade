# 1.2.0 发布构建

1.2.0 使用完整 Core 源码，包含狐灵、黑狐挑战、技能、共鸣成长与对应资源。
不再使用 1.1.1 的功能裁剪流程；历史脚本 `prepare_stable_release.py` 已加版本保护，不能用于 1.2.0。

使用 Java 17 / Gradle 8.7：

```text
gradle --no-daemon build -Pci=true
python tools/validate_release_readiness.py --jar build/libs/contract-blade-forge-1.20.1-1.2.0.jar
python tools/validate_mod_presentation.py
```

`-PstableRelease=true` 现在只将构建目录隔离到 `build/stable-release`，不删除任何玩法。
对应文件为 `build/stable-release/libs/contract-blade-forge-1.20.1-1.2.0.jar`，使用
`python tools/validate_stable_release.py` 和 `python tools/validate_mod_presentation.py --stable` 检查。

自动检查不能代替玩家验收。迁移前备份整个存档并保留已有配置；不可使用 1.1.1 的裁剪包覆盖
已经进入挑战领域的测试存档。实际发布前逐项完成 [1.2.0 验收清单](RELEASE_1_2_0_CHECKLIST.md)。

## 体积精简（2026-10-24）

- 正式 JAR 仅包含实际加载的双狐模型包；原始 `contract-fox-1.0.0.zip` 留在仓库，开发运行仍可读取。
- 原生验证、验收命令及录制/性能采样代码独立为 `validation` 源集；`build` 同时编译验证源码以防失修，
  Forge 的 `runClient` / `runServer` 加载它，正式 `jar` / `jarJar` 不包含它。
- 不包含 Boss 背景音乐，不接管原版音乐；战斗音效、振刀反馈和全部玩法保持。
- 列表插画由原图缩至 256×179；原图留在 `art/mod_list_logo/`，角色模型及微缩预览不变。

## 契约女仆进食修复（2026-10-24）

- 无投影、Home/驻留及卸下投影时，日常装备清理不再停止进食；TACZ 清理遵循同样规则。
- 进食期间延后恢复投影前的装备，避免覆盖食物。真正收纳前才调用独立的进食清理，
  主手进食不调用仅适用于原生副手进食的恢复方法。
- 原生投影验证覆盖无饰品、Home、吃完事件、卸下饰品后恢复原装备、收纳时食物守恒。
- 真实女仆之梦 1.0.1 / TLM 1.5.3 / TACZ 1.1.8 同装隔离验证通过：原生右键喂食只消耗一份食物，
  跟随及 Home 模式正常增加食物记录、完成对应愿望，且记录与完成次数经过契约收纳/显现后保留。
  不绕过女仆之梦自己的食物营养阈值与黑白名单。

## 装备所有权修复（2026-10-24，仍为 1.2.0）

- 手动换入与原装备相同的物品时也归还独立保存的原装备，不再用物品数据相等判断是否为同一件实物。
- TACZ 尊重手动换入的真实物品；结束投影时保留新物品并归还原装备。
- 各生命周期入口统一先清理 TACZ 专用手位快照，再恢复通用装备快照；移除历史无快照残留投影。
- 投影内容更新时使法术配装缓存失效；进食期间不初始化法术配装。
- 原生物品守恒和真实 TACZ 投影回归通过。验证类与诊断内容不进入正式 JAR，外部兼容模组不随包分发。
