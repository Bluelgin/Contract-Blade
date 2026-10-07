# 拔刀剑共用动作 · 第一段预览

动作顺序：战斗待机 → 腰侧取刀 / 横斩 → 接刀停顿 / 返斩 → 收刀。

## 可查看文件

- `black-swordplay.gif`、`white-swordplay.gif`：Blockbench 实际逐帧渲染的预览。
- `fox_black_preview.bbmodel`、`fox_white_preview.bbmodel`：可编辑副本，原有模型与动画保留。
- `contract_swordplay.animation.json`：按骨骼名导出的四段共用动作，不依赖黑白狐的工程 UUID。
- `verification.json`：骨骼引用、时间范围、原始文件校验记录。

在 Blockbench 的动画列表中选择 `animation.contract_blade.swordplay_preview` 可看完整片段。
四个独立动作分别为 `ready`、`draw_slash`、`parry_return`、`sheath`，使用相同前缀。

## 当前范围

这是供确认动作风格的样片，还没有接入女仆或 Boss 的运行控制器，也没有修改游戏战斗逻辑。
独立动作的衔接、真实拔刀剑模型的握持方向与刀鞘偏移，需要在游戏接入时校准。
动作展示不代表已复现拔刀剑原生连击 B 的攻击行为；攻击判定仍应沿用原生兼容桥。

预览中的简化刀 / 刀鞘是临时 THREE 装备参照物，不属于模型几何，不写入 bbmodel 或导出动画。
重新打开工程后，动画仍可编辑；临时装备参照物需通过 MCP 执行 `equipment-preview.js` 恢复。
原始工程中的备用狐形和旧头冠仅在预览副本中隐藏，不覆盖游戏内现有模型资源。

## 复现

`preview-setup.js` 隐藏预览中不用的部件。
`equipment-preview.js` 添加可拆除的装备参照物。
`build-library.cjs` 从 MCP 编辑后的黑狐样片派生共用动作和白狐副本。
`render-preview.ps1` 从当前 Blockbench 工程截图并生成动图，需要本地 MCP 服务和 ffmpeg。
`bb-mcp.ps1` 中的会话标识需按当前 MCP 初始化结果更新。
