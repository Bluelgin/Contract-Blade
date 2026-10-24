# 黑白狐 YSM 玩家模型 / White & Black Fox player models

独立模型版本 **1.1.1**，与契约之刃 mod **1.2.0** 分开编号。
目标环境为 Minecraft Java 1.20.1 / Forge / Yes Steve Model 2.6.5。
不需要契约之刃或车万女仆，不附带 Boss、SA 或其他战斗技能；不包含暂缓发布的赤月外观。

## 下载与安装

- 双狐合集：`contract-fox-duo-ysm-1.1.1.zip`。
- 单独模型：`contract_white_fox-ysm-1.1.1.zip`、`contract_black_fox-ysm-1.1.1.zip`。
- 合集先解压一次，再把里面的角色 ZIP 原样放入当前实例的
  `config/yes_steve_model/custom`。不要把外层合集直接安装，也不要放进 mods 或 resourcepacks。
- 重启，或有权限时执行 `/ysm model reload`，Forge 默认 `Alt + Y` 选择模型。
  也可使用模型菜单的“打开模型文件夹”定位实例路径。
- 服务器由管理员安装，客户端与服务端需对应的 YSM；旧同名 ZIP 或目录先移走，避免重复。

三尾支持走跑、转身、停步跟随，普通飞行有旋转尾巴动作，并提供第一人称手臂。
极端姿势或第三方动作可能穿插，不宣称所有加载器、版本和可选动作都经过实测。

## 署名与许可

各角色包内附 `CREDITS.md` 和完整 CC BY-NC-SA 4.0 条款。保留原酒狐、
角色适配者、YSM 默认动作及大正酒狐飞行动作的署名。
衍生模型遵守署名、非商业、相同方式共享；默认基础动作仍为 CC0，旋转飞行动作不是 CC0。
不得用本仓库代码的 MIT 许可替代素材原许可。

## 维护与打包

源资源在 `art/ysm-test/models`，不会用测试 ZIP 直接充当正式附件。
在 PowerShell / Node.js 环境运行，输出目录必须不存在，避免覆盖旧发布包：

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

## English

These standalone cosmetic models target Java 1.20.1 / Forge / YSM 2.6.5.
They do not require Contract Blade or Touhou Little Maid and include no combat abilities or Akatsuki model.
Extract the outer duo bundle first, then install the individual model ZIPs intact
under the active instance's `config/yes_steve_model/custom`. Restart or reload with
permission and open the Forge model menu with Alt + Y. Server installation is managed
by the administrator. Avoid duplicate old model folders and ZIPs.

Full credits and CC BY-NC-SA 4.0 terms are inside each model. Base animations retain
CC0; spinning-tail flight has separate attribution and is not CC0. Package validation
does not certify every in-game animation or third-party mod combination.

官方安装帮助 / Official help: https://yesstevemodel.github.io/en/wiki/faq/

YSM download: https://modrinth.com/mod/yes-steve-model
