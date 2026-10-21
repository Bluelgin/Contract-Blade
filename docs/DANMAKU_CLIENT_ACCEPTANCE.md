# 万华御札客户端整合验收

当前为验收进行中的报告。PASS 只表示对应层级确实运行并通过；静态校验、JUnit、FakePlayer 原生夹具、真实客户端和真实联机分别列出，不能互相替代。尚未完成的项目不会归入通过。

## 版本与历史

- Minecraft 1.20.1 / Forge 47.4.10 / Java 17。
- Touhou Little Maid 1.5.3 / SlashBlade Resharped 1.9.65。
- 代码基线 PR #27 `2f8756ee258d0893ea62794f9d878abc1e5e359b`；美术 PR #28 `261b2c21e29e12c0d2c260287828bef48e229cac`。
- 双亲提交 `72b33059625cf278509112e31b4ee2fe2c83bf4f` 保留两个分支历史；整合快进进入原功能分支。master 保持 `7958f6e2173d36805fc6df47d5addc1b9a991ecf`，PR #27 保持 Draft。
- 原 SVG、14 张正式符卡 PNG、40 帧物品动画及 mcmeta 不变。资源职责和路径见 [清单](../art/danmaku/SPELLCARD_ASSETS.md) 与 [代码接口](DANMAKU_CODE_INTEGRATION.md)。

## 录像与真实性

[实机录制工作流](https://github.com/Bluelgin/Contract-Blade/actions/workflows/core-ci.yml)：`manka-real-client` 实际启动 Forge OpenGL 窗口，使用 Xvfb + Mesa 软件渲染；MSS XGetImage 直接读取运行中的窗口，FFmpeg 编码 H.264 / yuv420p / 1280×720。目标捕获 30 FPS，实际帧率以 ffprobe 与 `.capture.json` 为准，VFR 墙钟时间戳保留演出原速，无音轨。原始 MP4 不进入 Git 历史。

Artifact 名称 `manka-client-gameplay`，保存 14 天；包含视频、逐视频捕获记录、验证 JSON、原生客户端/服务端日志，以及实际客户端 QA 证据。到期后应使用随本次交付保存的视频副本，或重新执行工作流。

| 文件 | 计划内容 | 当前交付状态 |
| --- | --- | --- |
| `manka_full_gameplay.mp4` | 动态物品10秒、正式轮盘、自动协奏、第三人称灵针/散华/结界真实拦截/四波幻想、第一人称幻想 | 等待当前 CI 产物 |
| `manka_fantasy_closeup.mp4` | 真实敌人、第一及第三人称、完整四波，原速 | 等待当前 CI 产物 |
| `manka_performance_test.mp4` | 36次真实服务端施法、F3 调试画面、真实原生无尽饰品与零余额 | 等待当前 CI 产物 |
| `manka_two_clients.mp4` | 第二个真实独立 JVM 的观察视角，两客户端同时施法 | NOT RUN，追加 QA 后执行 |

先前会话曾生成三个实机 MP4，但当前工作环境重置后，本地文件和日志没有保留。它们不计入本次可交付证据；本报告仅使用重新执行并保存的产物。

## 验收矩阵

| 检查 | 状态 | 证据 / 局限 |
| --- | --- | --- |
| 14张正式素材字节、透明通道、尺寸、原动画 SHA、图集四格UV、路径 | PASS | 当前本地 `validate_danmaku_visuals.py`；资源导出 CI 已通过 |
| SVG → PNG 可复现导出 | PASS | CI `spellcard-resources` 实际运行 Inkscape 与 `--check` |
| Java clean build / JUnit | 执行中 | 当前 CI build 步骤已通过，完整结果由本轮 CI 归档 |
| 架构、原生饰品、权限、共鸣、本地化静态检查 | PASS（静态） | 当前本地对应校验；不表示实机权限矩阵已全部通过 |
| 十级×四式解锁、成功精确扣费一次、零余额拒绝、无尽免耗、背包物品拒绝、真实32颗幻想实体 | 执行中 | 原生 dedicated Forge/TLM 夹具，明确使用 FakePlayer，不是多人测试 |
| 真实客户端基本渲染及三视频 | 执行中 | 当前 CI 实际客户端录制任务 |
| Lv1自动3颗、Lv2自动4颗、Lv5/7/9散华18/24/30颗、Lv6/7真实拦截5/8次 | NOT RUN | 追加客户端 QA 使用实际 `MANKA_SHOT` / INTERCEPT 日志断言 |
| 中键短按、长按、松开不施法、G改键、GUI Scale1/2、854×480窗口、等级锁标 | NOT RUN | 追加真实窗口 QA 与截图；视觉正确性需审阅截图 |
| 两个真实客户端、他人四层法阵、低画质 MINIMAL、密集施法 | NOT RUN | 独立真实 JVM + LAN + 第二显示器；自动操作，不冒称两名真人 |
| Lv4实际追踪、Lv8连续合法命中6颗、Lv9实际加强追踪 | NOT RUN（实机） | 原有成长数学测试不能替代目标运动验收 |
| 灵脉倍率、显现恢复、三狐/赤月权限及各自机制回归 | 执行中 / 部分未测 | 原有 CI 原生夹具独立执行；视觉整合未修改它们的规则 |
| 旧弹幕换身份后伤害拒绝、死亡/维度/断线/切武器取消 | 部分未测 | 追加 QA 覆盖换契约后续波次；旧弹幕实际伤害、死亡与维度完整矩阵仍需独立实机验证 |
| Boss免疫/阶段/限伤，以及两人同Boss全局2tick命中抢占 | NOT RUN（实机） | 保留原伤害路径和全局窗口不等于已经实际验证所有Boss |
| 缺失纹理降级、F3+T热重载 | NOT RUN（实机） | 代码保留原生 TLM / 跳过装饰降级，不能把代码分支检查称为实测 |
| 长时重复施法与清理 | NOT RUN（长时） | 36次短场景只覆盖短时重复；不代表30分钟或更长稳定性 |
| 8人/16人真实密集战斗 | NOT RUN | 当前未建立8/16个真实独立客户端；不使用FakePlayer冒充 |
| 无Shader、无BladeTetra运行 | 执行中 | 录像客户端只装必要运行模组；独立服务端CI可选提供方测试另列 |

## 性能口径

`MANKA_CLIENT_METRICS` 每10秒采集真实渲染帧间隔，记录 averageFPS、p99frameMs、其倒数 p99FPS、JVM已用堆MiB、视觉记录数和真实粒子数。p99FPS 不等于独立统计的“1% low平均FPS”。`MANKA_TPS` 记录实际tick/墙钟时间和服务器平均tick时长，`realPlayers` 来自服务器玩家表；`MANKA_SERVER_METRICS` 另外记录真实48格弹幕与队列。

`summarize_manka_metrics.py` 生成原始采样及统计JSON，区分实际在线人数。软件光栅化和30/20FPS限帧必须明确记录，不能据此承诺普通硬件GPU的帧率或16人性能。空闲/菜单与密集阶段需按录像时间区间分别分析，不能只展示空闲最高FPS。

## 可复现流程与未测项目

完整依赖准备和命令以 `.github/workflows/core-ci.yml` 为准。显示环境可运行：

```bash
python tools/run_manka_native_validation.py --output /tmp/manka-seed
xvfb-run -a -s '-screen 0 1280x720x24 -extension MIT-SHM' \
  bash tools/run_manka_client_capture.sh \
  '/tmp/manka-seed/server/Manka Native Validation' /tmp/manka-client
```

运行前安装 Java17、Gradle8.7、Xvfb、xdotool、FFmpeg、Mesa，Python `nbtlib==2.0.4`、`mss==10.1.0`。真实录制过程需要几分钟；首次 Forge 依赖构建另计。`/mankatest` 仅由显式JVM属性打开并要求管理员权限2，测试场景不得用于正式存档。`lan` 仅允许集成服务器，为独立离线开发客户端关闭该隔离测试LAN认证，不改变专服或生产默认配置。

8/16人复现：在独立专服运行相同整合版本，为每名真实登录玩家各建合法普通契约和实际饰品，使用Lv10密集四波，所有客户端同时采集原始帧时间、服务器TPS/MSPT与队列；分别测试同Boss和多目标，并记录全局命中窗口的实际归属。不能以单线程假玩家模拟作为真实联机结论。

额外诊断：一次遍历运行了所有 `tools/validate_*.py`，包括不在现有CI中的历史脚本。`validate_black_fox.py` 在未改动的黑狐控制器旧字符串断言上失败；若干旧脚本依赖本机未安装的javac、历史源压缩包或尚未构建的发布JAR。它们不能写成通过，也没有通过更改三狐战斗代码来绕过。正式CI覆盖项与这些额外诊断分开记录。
