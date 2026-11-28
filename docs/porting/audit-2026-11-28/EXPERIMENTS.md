# 真实 Minecraft 客户端隔离实验

日期2026-11-28，原始 master `d1429a4` / #38 `2955432`，均用单独 detached 工作树。生产构建未引入诊断覆盖层。原型源代码和复现步骤见 [tools/porting/audit/visual](../../../tools/porting/audit/visual/README.md)。这是原始资源的真实游戏客户端绘制，不是概念图、HTML或生成图。

## 实验环境与可复核证据

|项目|Forge 对照|NeoForge 实验|
|---|---|---|
|平台|Minecraft1.20.1 / Forge47.4.10 / JDK17.0.21|Minecraft1.21.1 / NeoForge21.1.219 / JDK21.0.13|
|TLM|1.5.3 Forge|1.5.3-neoforge+mc1.21.1|
|可选依赖|SlashBladeResharped1.9.65（原生产 shader 注册需要）与现有 TaCZ1.1.8运行配置|无 SlashBlade；直接测试原生 shader API|
|世界|旧版原生 fixture 副本，仅在1.20.1运行|一次性 dedicated 测试世界，服务器为不含覆盖层的#38|
|画面|1180×812窗口，GUI scale2，云桌面软件OpenGL|同左；不是实体机GPU兼容性矩阵|
|截图|[重载完成后](evidence/forge-models-after-reload.png)|[重载前](evidence/neo-clean-before-reload.png)、[重载后](evidence/neo-clean-after-reload.png)|
|原始桌面短录屏|[12秒](evidence/forge-native-motion.mp4)，录制时包含资源重载画面，不单独证明重载完成|[重载后12秒](evidence/neo-native-motion.mp4)，覆盖至少三个初醒图标完整周期|
|运行日志|[forge-client.log.gz](evidence/forge-client.log.gz)|[neo-client.log.gz](evidence/neo-client.log.gz)|

屏幕上始终标出 `AUDIT PROTOTYPE - original assets, no production gameplay`。两只狐是 `EntityMaid` **客户端库存预览对象**，不是服务器上取得/绑定/战斗的剑灵。未改动其原模型和贴图。原型仅更新预览计时/行走动画输入以观察绘制，不代表真实 AI/战斗触发已实现。

## 结果、推荐和限制

|要判断的问题|最小实验/实际观察|推荐|仍未验证|
|---|---|---|---|
|旧 TLM 双狐包是否必须重做骨骼/格式|原 `contract-fox-1.0.1.zip` 原样置入两版本 `tlm_custom_pack`；真实 loader 注册两个原 ID；加载世界后两个模型实际可见，尾巴姿态随时间变化；资源重载后仍正常可见|复用原包和ID，移植正式安装/升级与实体modelId分配；无需凭猜测重做美术|主动作/特殊技能/TaCZ/ISS/IM/飞行/装备挂点所有组合；服务端身份与模型选择；首次分发升级/不覆盖用户修改包|
|1.21.1 native 动态纹理是否能保留|用原 `awakening_vein` PNG+mcmeta+item model，临时替换隔离开发物品模型；真实 atlas 图标持续运动，两版本外观相符；重载继续显示|保留原 native texture strip/mcmeta及导出源|其他九份条、手持/丢出/第三人称与全部分辨率；不能把这一样本标成十项全过|
|原能量 GLSL 是否必须重写|原 JSON/vsh/fsh 与 rift PNG 字节不改；Forge调用既有 BlackFoxEnergyShader，Neo以原生 RegisterShadersEvent+ShaderInstance注册，原POSITION_COLOR_TEX_LIGHTMAP格式、1.21新BufferBuilder写入；真实紫色能量动态绘制，shader linked，重载再链接成功|保留 GLSL，升级 Java 注册/Buffer/RenderType 适配；避免用近似原版粒子替换|世界空间刀光/遮挡、深度、半透明叠加/双面、年龄编码与长期运行、全部GPU/着色器包。原型直接 RenderSystem绘制不是生产状态封装|
|旧轮盘贴图能否按原设计继续使用|真实GuiGraphics绘制原256² wheel_base，两个版本外观可辨且无缺图|继续原设计，适配实际输入/选择/动画逻辑|轮盘交互、符式图标/文本、支付/真实弹幕与同步；静态底图不是完成轮盘|
|重载会不会使样本消失|两个客户端实际请求正常资源重载；日志有START→重新shader/模型加载→COMPLETE，完成后截图仍见样本|正式 renderer/cache 实现明确资源重载生命周期|所有战斗缓存/网络状态/多次重载、运行中更换模型包、完整客户端崩溃安全|

本轮不宣称完整动作和全技能实机通过，也未进行Boss伤害、赤月追偿顺序、万华真实投射物或正式内部空间的原型实验：相应Neo执行链尚不存在；它们仍是后续开发/验证任务。视觉API样本不能回答这些问题。

## 实际发现的问题

- 两客户端均记录 `Error starting SoundSystem. Turning off sounds & music`，因此音效/语音仅能审读触发与来源，**本轮未试听**。需要有音频设备的本地客户端补验。
- Forge选定 SlashBlade1.9.65 组合在模型加载/重载时报 `Expected slashblade:user to be a Float, was "exist"` 或 `"exists"`，涉及 slashblade/bamboo/silverbamboo/white/wood 模型。此错误发生在旧版实际客户端，是独立兼容缺口；不能称整个旧版SlashBlade视觉基线干净通过，也不能仅凭本样本断定是哪一依赖的bug。需固定相同组合做单Mod/资源覆盖排查后决定适配。
- Forge还有TLM `grant_book/first_join` 战利品解析警告、在线公钥/远程内容查询失败；未把它们默认为移植缺陷或证明可忽略。云端远程网络与可选依赖配置需分别排查。
- 旧/新资源重载能完成，但本轮日志中的 TLM 加载耗时受云端软件渲染、依赖包数与远程读取影响，不能外推正式性能达标或固定性能回归。
- 初次Neo实验测试玩家处于生存模式，被原版史莱姆杀死。[早期重载后截图](evidence/neo-models-after-reload.png)保留真实死亡UI；当时只是预览，没有绑定白狐能力，不能据此判白狐免死失效。随后测试世界明确设创造/和平/白天，重新连接与重载，提供上表干净截图/短录屏。未修图掩盖过程。
- 运行准备曾遇模拟距离3超出允许范围、带空格Gradle属性拆参、原型Java泛型编译错误；均在隔离环境修正。它们不是“游戏玩法测试失败后跳过断言”。完整工作树不加入发布，客户端/服务端仅测试副本，退出后停服。

## 证据边界

精确 master 正式 JAR 的Java17 build本轮成功，日志见 [master-build.log.gz](evidence/master-build.log.gz)；JAR哈希见 JAR_PROVENANCE。原型在不同 detached 工作树运行，构建的诊断类不进入该正式JAR。最新Neo生产JAR同样没有临时复制的美术。截图证明技术适配方向，**不改变当前生产美术“未迁入”状态**。

所有PNG为本轮原始X11桌面截屏，MP4为实际x11grab录制；未生成或拼接游戏画面。[EVIDENCE_SHA256.json](EVIDENCE_SHA256.json)记录证据哈希。日志是压缩的真实运行记录；自动重载由原型触发，但成功标记在真实 reload Future 完成后写出，不硬编码玩法测试通过。
