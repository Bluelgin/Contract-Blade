# 黑狐 · 连击 B 动作样片

只展示连击 B，不加入旧样片的振刀、拔刀和收刀演出。

- `combo-b-full.gif`：完整 B1–B7，正常速度。
- `combo-b-half.gif`：B1–B3，在第四段前结束，供黑狐第一阶段使用。
- `fox_black_combo_b.bbmodel`：可编辑副本；动画名称为 `animation.contract_blade.combo_b_preview` 和 `animation.contract_blade.combo_b_half_preview`。
- `timing.json`：动作来源与各阶段参考时间。

动作参考本地 SlashBlade_2 的 `assets/slashblade/model/pa/player_motion.vmd`。
连击 B 的动作映射取自 `PlayerAnimationOverrider.java`；各段输入门槛取自 `ComboStateRegistry.java`，按 `TimeValueHelper` 转换为游戏刻。
保留原生手臂动作节奏，身体、头部和衣发尾巴针对狐模型调整；并非逐像素复刻玩家渲染。
原生动作所属项目许可保留在 `SlashBlade-LICENSE.txt`。

画面中的简化刀、刀鞘是 MCP 添加的临时装备参照，不属于模型几何；不是实际游戏中的拔刀剑渲染。
当前只做 Blockbench 观感验证，未接入战斗控制器。真正游戏接入必须按服务端原生连击状态同步，不能只按固定时长播放。
原工程和原有动作均保留；没有修改模组运行资源。
