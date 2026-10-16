# 契约武器背包语音 · LittleMaid PECO 复用

## 资源与依赖

使用车万女仆本体随包内置的 `littlemaid_peco`（LittleMaid PECO 音效包 v1.0.0，秋野かえで CV / Tamemaru）自定义音效缓存。**不在本模组中发布、复制或转码任何语音 OGG**。车万的 `CustomSoundLoader.getSoundCache("littlemaid_peco")` 和 `InitSounds` 提供声音映射；本项目的客户端声源从现有 `SoundCache` 取缓冲。若对应包或声音类别缺失，无声降级而不是播放空白默认音效。

`MAID_ITEM_GET` 为正常拿起，`MAID_HURT` 为短时间放回又拿起的特殊反应。PECO 为日语通用女仆音源，**不是酒狐专门录制的语义台词**；如需精确“你又拿我？”语义，仍需额外授权的语音文件。

## 客户端流程

`ScreenEvent.MouseButtonPressed.Post` / `MouseButtonReleased.Post`（仅玩家生存背包 `InventoryScreen`）读取 vanilla menu 的当前鼠标携带物。由独立纯 Java `InventoryVoiceGesturePolicy` 检测 **空手→持刀→空手→同一把刀**，1.8 秒内的再次拿起触发特殊音；正常 6 秒冷却、特殊 25 秒冷却。直接 Shift 点击、重复点击未放下、交换不同武器、创造物品复制不计入。采用客户端单调毫秒时钟，而不是服务端 tick；退出界面、断线会重置临时状态。按真正在鼠标上携带的武器绑定 ID 区分同一把刀，只读 `MaidInfusion.isInfused`、`ContractCarrierData.getBindingId`、`isOwner`、`isContractSuperseded`，缺少绑定 ID 拒绝发声。**不写 NBT、不生成实体、不访问服务端契约生命周期、不发包。**

`TlmPecoVoiceBridge` 仅 CLIENT 引用车万可选运行接口，扩展轻量 `AbstractTickableSoundInstance` 而非 `MaidSoundInstance`（后者强制绑定真实 EntityMaid）。在 `PlaySoundSourceEvent` 按车万相同方式附加缓存 `SoundBuffer`；玩家私有、只在本地播放，不广播到服务端或旁边玩家。新声替换旧声，登出清理。配置 `maid_weapon-voice-client.toml` 下 `inventoryVoice.enabled`、`inventoryVoice.volume`，另外仍尊重 Minecraft 的「友善生物 / Neutral」音量滑杆。

## 测试与人工验收

JUnit：初次拿起、快速拿起、不同武器、单次触发、节流、关 GUI / 登出状态复位。客户端实机：TLM 1.5.3 内置 PECO 语音 + 新旧背包、鼠标放回、Shift 移动、服务器无任何广播声、GUI 打开时暂停/音量设置、缺资源静默跳过、快速连续操作不叠音。需对 TLM 1.5.3 实际打包 JAR 校验接口一致性；源码参考 1.20 分支。
