# 契约拔刀剑精炼 / 女仆复活回归

## 已确认的边界问题

- TLM 胶片复活使用 `readAdditionalSaveData`，不是 `Entity.load`。原来的实体 UUID 与 `ForgeData.MaidWeaponBindingId` 不会自动恢复；契约仍指向旧实体，会持续占用伙伴名额。
- 原濒死处理只查玩家物品栏、鼠标及当前菜单，无法查到附近挂刀台上的真实载体。
- 在铁砧输入内收回女仆后，需要重新计算结果预览，否则结果可能保留显现期间的旧状态。

不能据此断言玩家那次死亡由精炼数值造成。尚需玩家的版本、精炼方式、当时女仆是否显现，以及日志/存档；刀内序列化数据本身没有可受攻击的实体。未改精炼上限、攻击力或拔刀剑原生修理规则。

## 实现边界

`ContractMaidRebirthBridge` 只在安装 TLM 时注册。在原生 `MaidDeathEvent` 的早期阶段沿用已有契约濒死收回；`LivingDeathEvent` 仍保留兼容兜底。`ContractEmergencyCarrier` 先复用常规载体定位，再按实体所在维度、32 格范围、主人、女仆 UUID 和契约 binding 查找唯一挂刀台载体。没有全地图搜索或每 tick 扫描。

收回仍使用既有原子序列化边界；失败不删除女仆。挂刀台重新发布物品数据；菜单通过原生槽位变更重算预览，不把输入覆盖到精炼结果，不改刀的能力状态。

复活只使用胶片保存的原 UUID、Owner 和契约标记，与玩家的真实契约载体相互验证。没有已显现同 UUID 实体、没有武器中的重复实体快照、契约未被替代时，才恢复 UUID 和 binding，并刷新恢复定位。旧胶片缺少 binding 时，仅允许 UUID 的唯一匹配。不同主人、重复活实体或武器已收存的旧胶片不参与重新关联。不凭名称/模型绑定女仆，也不复制所有 ForgeData 或装备投影。

## 验证

独立测试世界：`runServer -PcompatTest=slashblade -PcontractRebirthTest=true -PcompatWorld=contract-rebirth-fixture`，本地夹具使用临时端口，不改玩家存档。

`tmp/contract-rebirth-server.log` 的 `CONTRACT_REBIRTH_PASS` 验证：100 次原生最高级耀魂精炼调用后契约、女仆血量及物品数据未变；真实铁砧菜单输入及旧结果预览存在时可濒死收回，并刷新预览；模拟 TLM 胶片的 `readAdditionalSaveData` 加载路径恢复 UUID / binding；拒绝他人胶片及重复活实体；复活女仆可以正常收回，另一份契约能够显现；附近 ItemFrame 载体按相同绑定执行收回并发布数据。后者验证载体定位与存储，不等同于复现玩家实际敲挂刀台过程。测试没有复现精炼直接导致死亡。

旧版本已复活、丢失原 UUID 和契约标记的实体无法凭现有信息安全地自动归并。需要原胶片 / 备份或受影响存档进一步核验，不能保证此次更新会自动修复这类旧实体。

## 载体销毁判定限定为耐久耗尽

生产环境只从 `ItemStack.hurtAndBreak` 的实际消耗位置，以及拔刀剑 `ItemSlashBlade.damageItem` 的实际消耗位置调用救援。`PlayerDestroyItemEvent` 也会被正常物品转移触发，因此不再作为证据。移除了挂刀台交互证明与容器排除适配；普通玩家 tick 不会根据武器缺席启动救援。

仅在耐久代码真正准备消耗最后一个物品时保存快照，并在消耗后确认物品为空。日常磨损、创造模式和不可破坏物品不复制女仆数据。拔刀剑仅进入 `Broken` 状态不触发救援，显现/收回不检查此标记；可损毁木刀真正耗尽才触发。保留 40 tick 延迟与批量恢复预算，相同 binding 的载体在延迟期间重新出现时取消救援。

新任务保存 `DurabilityConfirmed` 标记。旧的缺席记录和 `DestroyedEvent` 记录都无法证明耐久消耗，保留快照但不自动救援。武器契约 NBT 格式不变。范围限于常规耐久路径及拔刀剑专用路径，不覆盖火烧、掉落物过期、指令删除或第三方绕过上述路径的自定义消耗。

专项夹具 `ContractDestructionValidation` 使用真实折断能力状态、菜单外存储、ItemFrame 载体和原生耐久消耗 / Forge 破坏事件。仅在隔离验证模式运行，不改玩家存档；原有批量救援配额和重试退避保留。

`tmp/contract-destruction-server.log` 通过 `CONTRACT_DESTRUCTION_PASS`、`CONTRACT_REBIRTH_PASS`、`MULTI_CARRIER_LOSS_VALIDATION_PASSED` 和 `CONTRACT_SERVER_BUDGET_VALIDATION_PASSED`：验证上述事件边界、复活回归、32 份丢失载体独立救援和跨玩家服务器配额。额外覆盖数量已归零但 NBT 尚未清除的真实耐久消耗路径；`ContractWeaponLocator.index` 不再把这种空栈计入存活载体。
