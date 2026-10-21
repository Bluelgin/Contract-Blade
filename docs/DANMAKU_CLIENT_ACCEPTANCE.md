# 万华御札：验收方法与证据入口

最终逐项结果由同一HEAD的真实客户端任务生成 `acceptance-report.md`，随 `manka-client-gameplay` Artifact归档；其中每项使用PASS / FAIL / BLOCKED / NOT RUN，不把静态、JUnit、FakePlayer和真实客户端混为一类。运行入口：[Core CI](https://github.com/Bluelgin/Contract-Blade/actions/workflows/core-ci.yml)。Artifact保留14天，用户交付副本另行保存；MP4不进入Git历史。

## 已确认的正式资源与渲染

PR #27基线 `2f8756e` 与美术PR #28 `261b2c2` 通过双亲提交 `72b3305` 保留历史，进入原功能分支。master没有合并本PR。14张正式PNG与SVG、40帧32×1280御札和mcmeta保持原字节。路径、职责、独立四格UV和性能预算见 [接口文档](DANMAKU_CODE_INTEGRATION.md) 与 [资源清单](../art/danmaku/SPELLCARD_ASSETS.md)。

已归档基准：[CI 39541098151](https://github.com/Bluelgin/Contract-Blade/actions/runs/39541098151) 全部通过。其 [真实录像Artifact](https://github.com/Bluelgin/Contract-Blade/actions/runs/39541098151/artifacts/12319673430) 包含H.264原速视频：完整演示134.73秒、幻想特写21.63秒、性能85.90秒，均为1280×720；精确编码FPS、大小、捕获来源和时间戳见video-validation.json。该归档到期2026-11-04 04:04UTC。44套159项JUnit零失败、零跳过；原生Manka40种等级/符式组合及32颗真实幻想实体通过，明确使用FakePlayer，不能替代多人。

基准单客户端性能视频内，7个完整10秒窗口的平均渲染FPS为28.611（限帧30），最低窗口26.714；最差p99帧65.139ms。服务器8个采样平均19.999TPS、3.421MSPT。Mesa llvmpipe软件渲染，不能外推硬件GPU或8/16人。36次请求中30次实际启动波次，不把请求数当成功施法数。源码附带统计工具可按捕获时间过滤原始日志。

## 实机复现

Minecraft1.20.1、Forge47.4.10、Java17、TLM1.5.3、SlashBlade1.9.65；依赖准备沿用CI，不改生产构建配置。安装Xvfb、Mesa、xdotool、FFmpeg，以及Python nbtlib2.0.4、mss10.1.0。

```bash
./gradlew clean build
./gradlew test
python tools/validate_ordinary_danmaku.py
python tools/validate_architecture.py
python tools/validate_danmaku_visuals.py
python art/danmaku/export_spellcards.py --check
python tools/run_manka_native_validation.py --output /tmp/manka-seed
xvfb-run -a -s '-screen 0 1280x720x24 -extension MIT-SHM' \
  bash tools/run_manka_client_capture.sh \
  '/tmp/manka-seed/server/Manka Native Validation' /tmp/manka-client
```

真实窗口通过MSS XGetImage采集，再以FFmpeg墙钟VFR编码，保留演出速度，不输入SVG或合成帧。三主视频展示真实动态物品、正式轮盘、基础协奏、两翼灵针、散华、真实箭拦截、四波完整幻想。追加视频使用第二个独立Forge JVM的真实观察画面及同一vanilla Wither；开发身份和输入由脚本控制，不冒称两名真人。

`/mankatest` 仅在显式JVM属性 `contractblade.danmaku.acceptance=true` 时注册，权限2，仅用于可丢弃测试世界。setup重建场地和真实普通女仆/饰品契约；bauble调整实际原生槽；cast走正常服务端入口；arrows生成有真实敌方所有者的箭。lan仅对隔离集成服务器关闭本测试LAN认证，不改变专服或生产默认值。轨迹和请求的诊断日志不改变伤害、权限或余额。

## 当前自动验收覆盖与限制

逐项断言、原始值及FAIL原因以每次运行的 `qa/runtime-qa.json`、`acceptance-report.md` 和实际截图为准。当前脚本覆盖：Lv1/2自动实体数，Lv3权限，Lv4原生追踪更新，Lv5/7/9散华数量和追踪，Lv6/7真实拦截，Lv8连续近战，32颗四波幻想，原生背包饰品拒绝，短/长按与改键，GUI1/2和854×480，低画质，两个真实JVM，原始契约飞行途中换手、死亡/复活、维度、断线、缺失纹理降级与重载，以及vanilla Boss免疫和同Boss全局两tick窗口。

覆盖列表是可执行用例，不代表每次运行都通过。截图需要审阅才能判定可读性。一次失败归档 `39542488014` 记录了测试脚本问题：上个用例残余箭在Lv7采样窗口开始前已被拦截3次，窗口内又拦截5次；第二客户端实际进服，但日志路径不一致造成观察脚本超时。后续提交清理旧箭、从施法前采样，并隔离工作目录/使用明确stdout日志；旧失败不会被改写成通过。

8/16个真实客户端、所有可选Boss完整阶段、三狐/赤月借权的完整客户端组合、灵脉/显现所有体感组合、30分钟以上压力仍需按最终报告逐项标记，不能从构建或2客户端结果外推。8/16复现必须部署相应数量独立真实客户端，并分别采集FPS、内存、TPS/MSPT、弹幕与队列、同Boss命中归属，不以FakePlayer替代。

额外遍历历史校验脚本时，未改动的 `validate_black_fox.py` 与黑狐控制器旧字符串断言不符；部分非CI旧脚本需要历史源压缩包或本机javac/发布JAR。这些额外诊断不能写成通过；当前正式CI和原生回归结果独立归档，不能通过改三狐战斗代码绕过。
