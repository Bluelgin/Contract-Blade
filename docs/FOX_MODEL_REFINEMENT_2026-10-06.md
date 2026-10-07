# 双狐模型接入记录 · 2026-10-06

## 接入范围

- 最新源工程：`art/fox_polish_preview/sleeve/fox_white_optimized.bbmodel`、`fox_black_optimized.bbmodel`。
- 原生 Blockbench Bedrock 导出：`white-optimized.geo.json`、`black-optimized.geo.json`。
- 更新内置 `modelpacks/contract-fox-1.0.1.zip` 的双狐几何和贴图，模型 ID、比例、描述、图标及所有 379 个原有活动动画保持不变。
- 两张运行贴图均为 512×512，去重重排而非整体缩小。白狐去除 93 个完全被同骨骼内不透明方块包住的面；黑狐保留所有面以兼容透明 Boss。
- 同步独立黑狐 Boss 的几何与贴图；保留原有五个被动动作、七段连击 B、独立战斗逻辑以及 `BossSheathLocator`。
- 原始交接包、完整美术原稿保留。接入前的运行模型包、Boss 几何和贴图保存在 `art/fox_polish_preview/runtime-backup-20261006/`。

## 已有模型包升级

`FoxModelPackBootstrap` 增加上一版已知管理包的 SHA-256：`9c907d7583939b1576fca9ba9c3eb77ee2738e000653c0095054e391cd09d6e9`。旧包精确匹配时先保存字节相同的 `.bak` 再升级，沿用已有文件名，避免重复模型 ID。自定义或自行修改过的包仍不覆盖。

## 验证

- `tools/validate_fox_models.py`：源包校验、动画文件逐字节一致、全部动画骨骼引用、纹理尺寸、包内校验清单与署名许可通过。
- `tools/validate_black_fox.py`：独立 Boss 资源和既有战斗边界检查通过。Boss 基础动画字节与接入前构建产物一致。
- 完整构建通过。使用已有本地缓存入口 `tmp/shrine-cached-dependencies.init.gradle`，未更改依赖版本。
- `tmp/fox-polish-client-validation.log`：`BLACK_FOX_CLIENT_PASS`；七段 B、被动姿态、装备层实际绘制、资源重载、粒子纹理和能量 GLSL 检查通过。夹具不进入玩家存档。
- 正常测试端由 `tools/launch_fox_model_test.ps1` 启动，使用 `run` 原目录和本模组＋车万女仆＋拔刀剑配置；日志为 `tmp/fox-polish-play-client.log`。

自动资源/绘制检查不替代玩家观察。仍需游戏内查看正侧面贴合、行走/坐姿下的衣饰、黑狐透明观感及实际帧耗时；未声称 FPS 提升百分比。
