# 黑白狐发色优化预览

本目录是待用户确认的编辑副本，未接入游戏，也未覆盖原始工程。

- `fox_white_polished.bbmodel`、`fox_black_polished.bbmodel`：贴图已嵌入的可编辑工程。
- `exterior-comparison.png`：外观，左旧右新。
- `inside-comparison.png`：临时隐藏外层发片后的基础头部对比，左旧右新；这是检查视图，不是最终外观。
- `verification.json`：几何不变、旧 UV 使用像素不被覆盖、基础头部非发色像素保留等检查结果。

基础头部残留的赤月发色改为对应角色的发色；外层发束采用七档低反差明暗。仅头部发色和 Hair/Bangs 下的发片重排 UV，采用未使用的贴图区域及一像素边缘扩展。没有更改眼睛、脸部皮肤、衣装、狐耳和狐尾。

没有发现完全重复的发块，因此没有删除几何；静态预览不能证明游戏里不存在其他重叠面的 Z-fighting。当前处理主要改善配色跳变与采样串色风险。

源文件来自 `E:/刃录/art/characters/{white,black}_fox/fox_{white,black}.bbmodel`。预览隐藏原工程中的旧头冠和备用狐形，保留其数据。确认接入时应将此次 UV/贴图改动合入现有运行资源，不能用源工程覆盖已有 Boss 动作或重新加入已删除的头冠。

`open-*.js` / `setup.js` / `polish.js` 在 Blockbench MCP 中执行，`cutaway.js` 与 `restore.js` 只用于检查视图。`mcp.ps1` 的会话 ID 需随 MCP 重启更新。
