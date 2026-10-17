# 契约武器 · PECO 女仆情绪互动

本 PR 是客户端纯表现功能。音频来自 Touhou Little Maid 内置 LittleMaid PECO v1.0.0 的已有自定义音效缓存，没有包含、复制、重新压制任何 .ogg 文件。本 PR **不是**酒狐专属新配音，也不承诺 PECO 中某句固定话术恰好为「又拿我」。

## 行为与角色感

- 首次用鼠标真正拿起本人有效契约武器：默认 **40%** 机会自然回应（`maid.mode.idle` 中已有的音频）。不再播放「获得物品」音。
- 1.8 秒内放回再拿起 **同一 binding**：默认 **80%** 机会疑惑回应（`maid.ai.tamed`）。不会误用受伤音频。
- 3 次或以上连续快速拿起：默认 **35%** 机会触发稀有活泼反应（`maid.ai.find_target`，具体语义待试听验证；如果想保持温柔可禁用这种情绪）。
- 普通 5 秒、疑惑 8 秒、稀有 30 秒独立冷却；无响应不会丢失连击进度。不会强行中断正在说话的前一段，也不会排队刷屏。
- Shift 整理、换刀、未真正放回、进入创造物品操作、关闭背包/重登、他人武器/失效绑定均不构成合法新动作。
- 触发概率及每类样本索引均为独立客户端配置。无额外网络、女仆实体、NBT 改动。

## 语音实现与试听限制

车万 PECO 缓存中存在多条 `idle`、`tamed`、`find_target` 录音；**分类只表示事件，不保证文本语义或情绪正确**。当前默认取类别中随机已有音频，避免通过类别 `item_get`、`hurt` 强制配音。提供 `greetingVariant`、`curiousVariant`、`playfulVariant`（-1 = 随机，0 及以上 = 指定录音索引），在人工试听后可直接锁定精选样本；越界索引静默跳过。此处无法对声音的日语语义进行有依据的人工确认，因此 **不把默认类别映射标为已试听精选**。

**游戏内客户端试听命令**（不发往服务器）：
- `/contractvoice list`：列出 3 个情绪族当前可用的 PECO 样本数。
- `/contractvoice preview greeting 0`、`/contractvoice preview curious 0`、`/contractvoice preview playful 0`：立即试听该类别的第 0 条。逐个试听所有索引。
- 在 `maid_weapon-voice-client.toml` 中，将 `greetingVariant` / `curiousVariant` / `playfulVariant` 改成试听选中的索引并重载客户端配置；留 `-1` 则按当前音包类别随机取样。输入索引越界时静默跳过，不会使用错误音效。
这份工作不能自动根据日语台词语义挑选完美的一句，因此最终情绪映射需要听过 PECO 内容后确认。

## 架构

`ContractInventoryVoiceEvents` 检查真实 cursor stack、owner 与 binding；`InventoryVoiceGesturePolicy` 是纯 Java 的互动状态机；`compat/tlm/TlmPecoVoiceBridge` 是车万自定义 PCM 声音缓存适配器，只在 TLM 加载时注册。音量尊重原版 Neutral 滑杆、默认客户端 0.6；语音播放中不抢声、不排队。缺声音包 / 不对应版本时静默。

## 需要实机确认的项目

- Minecraft 1.20.1 + Forge + Touhou Little Maid 1.5.3：确认语音实际能播放（车万使用自定义 `SoundBuffer` 非 vanilla 占位音）。
- 为 PECO 各索引实际试听后再判定问候、疑惑与稀有吐槽的中文理解是否匹配。尤其 `find_target` 可能偏战斗提示，不能未经试听宣称它一定是撒娇。
- 背包拿起/放回、连续 3 次、长音频不叠音、声音设置、TLM 缺失、服务器静默与多玩家不互相听到。

## 自动客观初筛（2026-10-17）

车万原始仓库 PECO v1.0.0 音频目录共核对 332 个 OGG，类别 `mode/idle` 78、`ai/tamed` 15、`ai/find_target` 18。**文件大小只可作为粗略长度线索，不等于已试听或识别日语内容。**

筛选策略不再让未经试听的 `find_target` 参与背包互动，亦不使用 `item_get` / `hurt` / `death` 等容易出戏的类别。运行时复用车万现成解码 PCM，分别从：
- **问候** `mode/idle` 的较自然中等长度声音选至多 8 条（1.5–4 秒）。
- **疑惑/亲昵候选** `ai/tamed` 的短至中等长度声音选至多 6 条（0.45–3.5 秒）。
- **调皮/短促回应候选** `mode/idle` 的短声选至多 5 条（0.28–1.45 秒），不再随机播战斗索敌音。

每条音频必须通过有限时长、有效 RMS 音量、静音比例及相同解码音频指纹去重的检查，再按目标时长/静音程度排序，集合在车万缓存更换时自动刷新。**这是客观声学初筛，不具备理解日语台词、情感语义的能力，情绪仍要在游戏里逐条试听。** 与未经验证的硬编码列表索引相比，该方法避免假装压缩资源加载顺序固定。

`/contractvoice list` 显示每种互动的真实 PCM 精选索引列表（并非假设的文件序号）。`/contractvoice preview greeting <index>` 等可试听。设置 `acousticShortlist=false` 可退回整个类别随机播放；设置 `greetingVariant`、`curiousVariant`、`playfulVariant` 为实际试听确认的索引可以覆盖自动初筛，索引更新依赖安装环境，修改语音包或版本后应重新确认。

**尚未完成的环节**：人工逐条试听、日语语义判定、与酒狐角色性格匹配的最终精选索引。只有这一步完成，才能叫“台词精选已确认”。
