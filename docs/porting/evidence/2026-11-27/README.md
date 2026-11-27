# 本轮真实客户端启动证据

2026-11-27 UTC，在本轮 Cloud Linux X11 桌面实际运行 `./gradlew --no-daemon runClient`。截图直接取自桌面，没有生成、拼接或模拟界面。

- Minecraft 1.21.1 / NeoForge 21.1.219 / Java 21.0.13.1。
- TLM 1.5.3-neoforge+mc1.21.1 与 Contract Blade port probe 0.0.0-port-dev。
- `main-menu.png`：实际主菜单，显示 Minecraft、NeoForge 版本和四个已加载 Mod。
- `mod-loaded.png`：实际 Mods 页面，显示契约之刃开发包 State: loaded 及明确的未完成移植说明。
- 被测游戏代码提交 `c2ceec539e287b08f8aed4315f13ba99b2465aa9`；后续证据/CI/夹具配置提交没有改变该客户端代码。
- 被测 JAR SHA256：`f275da6e498ffda2606520f569b8cfd379100713253cca8169d7d83c1065a177`。

只证明本轮实际客户端可启动、可显示并加载 Mod。未证明正式契约 UI、所有剑灵技能或内部空间可玩；这些功能缺口见功能矩阵。不能将本截图当作完整玩法验收。

开发客户端日志存在 Mojang 在线服务连接失败及 PipeWire 音频后端警告；未阻止到达主菜单和 Mods 页面。本轮没有验证在线账户、音频输出或多人客户端。
