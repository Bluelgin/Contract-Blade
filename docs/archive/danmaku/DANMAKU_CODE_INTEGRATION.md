# 万华御札：客户端美术整合接口

> 历史方案与实现记录，可能包含已撤回功能、旧数值和当时的测试结果。
> 当前说明从 [文档导航](../../README.md) 进入；本页不作为现行玩法指南。

整合基线：功能 PR #27 `2f8756e`，已确认美术 PR #28 `261b2c2`。美术提交通过双亲 merge 保留；14 张符式 PNG/SVG 保持原字节；物品贴图于2026-10-22按用户确认的朱樱封札第二稿更新，40帧时序不变。完整资源表见 [SPELLCARD_ASSETS.md](../../../art/danmaku/SPELLCARD_ASSETS.md)。

## 模块和权限

| 模块 | 职责 |
| --- | --- |
| `DanmakuWheelPainter / Geometry / Screen` | 256px 底图、独立图标、金樱呼吸、锁标、本地化文字和真实 renderItem；输入与图标共用方向几何 |
| `DanmakuPressGesture / Keys` | 短按施放、长按轮盘、松开只选择；保留短于一个客户端 tick 的点击 |
| `DanmakuVisualEvent / WorldEvents / Network` | 服务端确认的波次、已生成弹幕、真实伤害、真实拦截及清理事件；单向 S2C，协议2 |
| `DanmakuWorldVisuals` | 128个短演出、64个结界、256个投射物外观记录；维度、时效、实体 UUID、死亡及取消检查 |
| `DanmakuWorldPainter / RenderTypes` | 深度测试、透明颜色写入、空心结界、独立图集 UV 的四重花阵、局部命中碎光 |
| `DanmakuProjectilePainter / ContractDanmakuRenderMixin` | 仅替换服务器事件匹配的真实 TLM 实体外观；碰撞、运动和伤害继续由原生弹幕处理 |
| `DanmakuTextures` | 正式路径、资源缺失降级及资源重载缓存清理 |

美术位置统一为 `maid_weapon:textures/gui/danmaku/<name>.png`，不再使用早期草案的 `icons/` 或 `effects/` 子目录。所有14张正式纹理参与客户端组合。`fantasy_layers` 的四格分别为 UV `(0,0)`、`(.5,0)`、`(0,.5)`、`(.5,.5)`，每格跨度 `.5`。

世界事件仅向同维度48格附近玩家发送。普通事件每服务器 tick 全局256、每主人32；清理事件不被视觉预算压制。不添加视觉实体或客户端伤害回报。客户端每帧最多512个装饰四边形，低粒子设置144个；第四波仍保留完整四层。缺失纹理跳过装饰或保留原生 TLM 外观。使用 Minecraft 标准着色器，无额外 Shader 包。

## 保留规则

成长、共鸣数值、原始契约绑定、Boss受击保护、全局两 tick 命中窗、弹幕与队列上限保持原实现。服务端视觉回调通过中立 `DanmakuRuntimeApi` 调用，compat 不反向依赖领域层。真实健康值/吸收变化才发送命中碎光；拦截只在服务端实际删除敌方投射物并减少次数时发送。

## 可复现验收入口

```bash
./gradlew clean build -Pci=true
./gradlew test -Pci=true
python tools/validate_ordinary_danmaku.py
python tools/validate_architecture.py
python tools/validate_danmaku_visuals.py
python art/danmaku/export_spellcards.py --check
python tools/run_manka_native_validation.py --output /tmp/manka-native
python tools/run_manka_client.py --seed-world '/tmp/manka-native/server/Manka Native Validation' --output /tmp/manka-client
DISPLAY=:0 bash tools/record_manka_client.sh /tmp/manka_full_gameplay.mp4 180
# Already running fresh fixture client; records three genuine gameplay videos:
python tools/run_manka_capture_validation.py --log /tmp/manka-client/client.log --output /tmp/manka-videos
```

Java17、Gradle8.7、TLM1.5.3 与 SlashBlade1.9.65 的准备沿用现有 CI；不改生产依赖配置。真实客户端需要兼容 OpenGL 的显示环境，以及 FFmpeg、xdotool、`nbtlib==2.0.4` 和 `mss==10.1.0`。录屏通过 MSS 的 X11 XGetImage 实时读取窗口，交由 FFmpeg 编码为 H.264；采用实际墙钟时间戳与 VFR 保持原速，并导出 `.capture.json` 记录。没有离线图像输入或生成帧。可选 `--skip-assets` 仅用于已准备好官方资源缓存的机器。

`MankaAcceptanceCommands` 仅在 JVM 参数 `-Dcontractblade.danmaku.acceptance=true` 时注册，要求管理员权限2；`/mankatest setup` 只用于独立测试世界，会重建原点场地并创建真实原生饰品契约。`second` 在第三个物品栏槽创建另一个真实普通契约供换手测试；`cast 0..3` 走正常服务端施法入口，`arrows` 创建有真实敌方所有者的箭，`bauble 0/1/2` 调整真实存储饰品槽，`metrics` 记录真实在线人数/MSPT/弹幕/队列。客户端每10秒记录实际帧时间、堆内存和视觉记录数。

原生自动夹具明确使用 FakePlayer，覆盖十级×四式的服务器施放及扣费、实际32颗幻想实体、支付失败、无尽契约和背包饰品拒绝；它不能替代真人客户端操作、多人或性能测试。验收状态与真实录像另见客户端验收报告。
