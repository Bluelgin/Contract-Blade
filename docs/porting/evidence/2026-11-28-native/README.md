# 真实原生开发构建验收 · 2026-11-28

不是完整移植验收。Minecraft 1.21.1 / NeoForge 21.1.219 / Java 21 / TLM 1.5.3 原生版，软件 Mesa 渲染。使用 `src/port` 正常客户端与专用服务端，**没有审计覆盖层、HTML 或生成图**。GUI/物品/显现动作使用 X11 真实键鼠输入；CUA 入口因 sandbox 初始化错误不可用。一次性测试世界；未读取或修改玩家正式存档。

## 真实截图

- `01-normal-crafting.png`：原木剑 + TLM 空魂符，真实 2×2 合成契约之刃，原属性 tooltip。
- `02-native-tlm-maid.png`：TLM 真正女仆，选用当前分发的原白狐外观；不是内置白狐剑灵获得/能力证明。
- `03-formal-binding-table.png`：原粉色四槽正式契约台，已缔结契约与空魂符；英语长度适配。
- `04-manifested.png`：普通正式剑实际显现该女仆，Manifested 1000/1000 HUD。
- `05-recalled.png`：实际右键空气归刃，Stored HUD。
- `06-real-player-rejoin.png`：正常保存停服、结束客户端，再运行最终编译代码、重连同一玩家，仍保留契约与 Stored 1000/1000。
- `07-soul-slab-extraction.png`：重进后在真实 GUI 点击提取，空剑 + 带原外观预览的迁居魂符输出。
- `08-soul-slab-rebound.png`：持有该魂符点击台，打开 GUI 而不是意外释放；再次输入空剑+魂符，实际点击 Bind，输出契约剑与空魂符。
- `09-transferred-maid-recalled.png`：重绑后显现同一 UUID 的女仆，再次通过正式剑归刃；最终 Stored。

01–05 使用本轮已构建的普通链路代码，随后只增加魂符 `model_id` 预览、台优先交互与模型包幂等修复；06–09 使用 `JAR.json` 对应最终编译输出。开发运行由 ModDevGradle 加载这些 classes/resources；没有把它描述成独立启动器安装 JAR 验收。最终 JAR 由同输出构建，CI 单独保存。

场景准备使用管理员命令：一次性平地、时间/天气、主人身份与 TLM 女仆外观、摆放测试契约台、机位传送和原材料。契约剑实际合成；捕获用 TLM 原生空魂符实际点击；缔结/提取/重绑、显现/归刃均是实际输入。测试契约台由命令摆放，不能称其配方已经实机合成。未使用测试水晶或管理指令创建这些契约。

机位准备中曾把角色传送出平台造成坠落死亡；随后回收掉落的**原**契约剑/空魂符（未生成副本），同一女仆仍存在，最终正常归刃。此操作不构成自动死亡/武器遗失恢复通过；这两条玩法仍有缺口。身份文本记录在本目录，能核对相同 UUID/binding 与转移后的新 ticket。

## 构建与服务端

- `neoforge-build.log`：Java 21 build，59 tests，零失败/错误/跳过。
- `forge-build.log`：Java 17 build，184 tests，零失败/错误/跳过；仅模型包安装幂等/用户编辑保留修复，旧正式分支未改动。
- `native-smoke.log`：真实专用服务器两次独立启动；全部必要标记实际通过，包括 NATIVE_FORMAL_ALTAR_PASS、NATIVE_CONFIGURATION_PASS、PORT_NATIVE_RESTART_PASS、PORT_NATIVE_ROUNDTRIP_READY。19 钻石/47 XP/875 共鸣、旧载体/魂符拒绝、距离/重复按钮、升级组件保留、真实 TLM 捕获和释放、用户配置均有断言。
- `client.log`、`client-server.log`：最终编译输出的客户端启动/连接与服务器运行；`JAR.json`：实际构建文件名、SHA256 与依赖版本。
- 客户端 OpenAL 设备不可用，因此**音效未试听**。Mojang 公钥和 TLM 在线列表网络请求失败，未阻止本地模型包加载/隔离离线服务器流程；不称在线账户联机验收。未完成全部渲染/动作/Shader/技能演出验证。

正常退出证明范围仅为可观察的正常保存和再次加载；不等于任意崩溃安全。完整缺口见两级功能矩阵和 NATIVE_GAMEPLAY_DELIVERY.md。
