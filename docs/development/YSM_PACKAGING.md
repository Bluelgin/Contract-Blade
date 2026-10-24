# YSM 双狐打包与分发

源资源在 `art/ysm-test/models`，不会用测试 ZIP 直接充当正式附件。
从仓库根目录在 PowerShell / Node.js 环境运行，输出目录必须不存在，避免覆盖旧发布包：

```powershell
./tools/ysm/package-release.ps1 -ReleaseFolder './dist/ysm/1.1.1-release' -Version '1.1.1'
./tools/ysm/validate-release.ps1 -ReleaseFolder './dist/ysm/1.1.1-release' -Version '1.1.1'
```

打包只取声明的模型、贴图、动画和玩家说明，角色 ZIP 根目录直接包含 `ysm.json`。
检查资源哈希、骨骼引用、许可和开发内容混入；不把临时目录、历史源码、音乐或新截图混入。
静态验证不等于实机全动作验收。发布时提供展示截图或录屏，应使用实际游戏画面并注明环境。
基础动作署名记录在 `tools/ysm/release-sources.json`，打包不依赖本机临时文件夹。
如需同时对比女仆包改动前后的快照，可为检查命令添加 `-CheckMaidRegression`；
该项需要本机 `tmp/maid-tail-sync` 快照，默认不运行，也不计入 YSM 包验证通过项。
上传前确认中英文说明、来源、许可、游戏/YSM版本和已知限制；本脚本不会上传任何附件。

玩家安装与许可说明见[玩家指南](../player/YSM_MODELS.md)。
