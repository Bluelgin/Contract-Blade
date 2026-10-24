> 本文记录 PR #13/#14 的历史机制与当时验证。当前精简分支请阅读 [契约生命周期精简](../development/CONTRACT_LIFECYCLE_SIMPLIFICATION.md) 和 [当前验证记录](SIMPLIFICATION_VALIDATION.md)；旧备份回执/待审查流程已不再作为操作权限。

> 验证记录仅证明文中注明的版本、环境与场景；不代表当前提交或所有整合包均已验收。

# 阶段三验证与交付记录

## 基线与环境

远端实际读取 PR #10/#11/#12，分别为 d10a309fc03141bcf9852ca5fe10d9e23d3f6f63、
ced992e8e9c12b0829ed432b8be7db8704ecdea4、b9a8c74d44d52a8846926703596824f677fc51bd。
PR A 基于 #12；PR B 必须叠在 A 上。未合并、未创建 Release。

Java 17.0.20 / Gradle 8.7 / Minecraft 1.20.1 / Forge 47.4.10。
真实专服加载 TLM 1.5.3 与 SlashBlade Resharped 1.9.65，不加载 BladeTetra。
本地测试依赖未提交；JAR 校验值：

- TLM：8c341567e57aa3c006f9fa85c7a5e3bc36a53b1cca4e3538c3db705eeeea1bbc。
- SlashBlade：2a6eb8d2ead295cb3838b551b9f3cf0e81de97966dbc91b84cf2c7589f3a4361。

上游备份接口与死亡/胶片转换通过目标 JAR 实际反编译和运行验证；Forge CapabilityProvider.deserializeCaps 已按实际目标字节码核对，未假定新版本 API。
备份桥接使用已核实格式的有界只读解析，后台执行，避免直接调用上游无界 NBT 读取。

## PR A 实测

| 验证 | 实际结果与边界 |
| --- | --- |
| #12 基线 Gradle build | 成功；原有 12 项 JUnit 结果为 0 failure/error |
| A clean build | 成功，编译、资源、JAR、重混淆、JUnit 全部执行 |
| A JUnit | 16 项、0 failure/error；新增 4 项验证事故/转移回执持久化与拷贝隔离、未知 schema 不覆写、狐灵账本的主人/token/origin/角色隔离 |
| 恢复新夹具 | CONTRACT_STAGE_THREE_PASS：真实耐久消耗 + 取消正常显现后交付紧急胶片，原生 ItemFilm.filmToMaid 保留 UUID/装备，重放拒绝，紧急释放后可重新缔约；实际四槽菜单普通魂符解除/迁居、UUID/BindingId 保留、回执重放拒绝、正确存储/显现状态、编码失败保留活实体、TLM/Forge 死亡事件投递与取消、合法损毁快照生成紧急胶片、普通胶片不能入正常台、旧 MaidInfo 预迁移不消耗输入、非主人拒绝、未知卸载保持 suspect、确认死亡释放名额与普通幂等清理 |
| 当前特殊角色救援 | 实际原生女仆直接删除后，从最新实体数据写回；原 UUID 与装备保留，重新显现/收回成功。身份是服务器显式构造的测试契约，不冒充实际剧情获取 |
| 原生备份 | 实际调用 TLM MaidBackupsManager.save，等待其异步落盘，用桥接读取实际文件；验证原 UUID、已绑定所有权、恢复后显现/收回与重复恢复拒绝。原生 SlashBlade 三个纯数字 cap 通过白名单，未修改备份内容绕过校验 |
| 专服重启 | CONTRACT_STAGE_THREE_RESTART_PASS：同一临时世界真实停服、重新启动，注入的 COMMITTING/spent 状态仍在；没有第二只女仆，显现重放被拒绝，最后的存储载荷保留并进入 REVIEW_REQUIRED。属于持久化中断状态注入，不是强杀进程或磁盘断电实验 |
| 精炼与复活 | CONTRACT_REBIRTH_PASS：100 次真实原生精炼、铁砧与挂刀台濒死收回、原生复活身份重关联、重复实体拒绝、第二契约可显现 |
| 真实损毁 | NATIVE_STAND_TRANSFER_PASS / CONTRACT_DESTRUCTION_PASS：实际原生挂刀台插入、交换、取出，误导性的 Forge 销毁通知不触发救援；Broken 拔刀仍可显现；实际木刀/普通剑耐久消耗救援；旧未证明损毁任务保留且不执行 |
| 批量与预算 | MULTI_CARRIER_LOSS_VALIDATION_PASSED / CONTRACT_SERVER_BUDGET_VALIDATION_PASSED：原有多契约、每玩家/全服预算及失败载荷保留夹具通过 |
| 黑狐奖励 | BLACK_FOX_SHRINE_RETURN_PASS / CONTRACT_MASTERY_FOX_PASS：原生终局奖励与返刀、所有权、模型、显现/收回、黑狐魂符迁居、全局熟练度资格通过 |
| 白狐/魂符/模型 | SHRINE_NATIVE_PASS / FOX_TRANSFER_NATIVE_PASS / FOX_COMPANION_NATIVE_PASS：神社真实模板与原刀、剧情进度不重播、白狐魂符完整数据往返、原刀迁出不重生、旧 token 拒绝、模型选取与装备、可见女仆合法范围内收回再迁居、保存重读后显现/收回通过 |
| 黑狐剑核回归 | SWORD_CORE_NATIVE_PASS / RELEASE_PASS / MASTERY_CREDIT_PASS / GROWTH_PASS / MULTI_CREDIT_PASS：保留原生 B1–B7、幻影/Drive/WaveEdge 伤害、20/30 耀魂、400 tick、Lv.1–10、16/8 格范围、Lv.6 +7 跟随、Lv.10 有限协同及多人归因 |
| 源码/资源检查 | 原有前 15 项边界检查通过；狐模型白名单检查在干净 #12 与 A 同样失败，详见下文 |

## 过程中实际发现与修正

- 首次编译发现 PlayerList.save 为 protected，改用公共 saveAll；修正一次变量重名。
- 原生 TLM 死亡事件应使用服务器 OwnerUUID 查找，而不是依赖 getOwner 的当前维度实体查询。
- 成功收回要结束失败退避记录，防止短时间继续被当作失败保护。
- 未加载夹具原来误用 DISCARDED；该理由已有明确销毁证据，改为真实 UNLOADED_TO_CHUNK 理由，保留 suspect/拒绝清理断言。
- 原生备份含 SlashBlade 的纯数字能力数据；核对实际文件后添加严格字段白名单，未知 cap/物品仍拒绝。没有删除备份数据使测试通过。
- 正常魂符转移回执也保存在武器上；交互限制已改为魂符实际类型，避免禁用武器基础右键/挂刀台。
- 原有迁居夹具仍把空 film 当魂符，改用实际 smart_slab_empty；原有身份、数据、装备、距离、幂等和复制断言全部保留。

## 既有基线失败

干净 #12 的 validate_fox_models.py 在 pack.png / 模型 ZIP 白名单断言失败；A 同样失败。
未修改模型/白名单或删除断言隐藏它。#11/#12 已记录的旧完整 Boss phase-one、旧场景/动作
检查问题不属于本次修复；本次原生终局夹具不等于真人完成整场 Boss 战。

## 明确保守边界与未执行项

- 实际损毁快照缺少身份字段时使用独立恢复任务键隔离，不伪造 BindingId；自动损毁救援最多 12 次，最终快照与交付回执保留在管理员诊断中。该缺失身份分支仅做代码审查，未执行独立游戏故障注入。
- 旧备份含装备、背包或未知 capabilities 时，物品是否已经进入墓碑/其他容器无法证明，恢复停止并诊断。当前健康移除快照优先，不做旧装备回档。
- 实体暂时未加载、跨维度/内景、正常转换和离线本身不触发备份复活或解除。身份字段丢失且无法可靠关联的旧数据原样保留。
- 编码器明确拒绝且尚未发布的恢复可保留快照后重试；未知中断提交、已消耗回执和紧急胶片交付中断不会自动重放。
- 被实际损毁载体释放的自由狐灵保留身份但不具有原刀权限；重新安置该特殊损毁状态需要后续明确转移接口/管理员处理，不发第二把原刀。
- 当前普通已确认死亡、有可信但不安全备份时释放显现名额并保留契约，等待管理员；没有可信备份时特殊刀灵保持 pending。
- 原生死亡事件是通过真正 Forge 事件总线与原生实体执行的服务器夹具；未加载测试覆盖移除理由，不等于实测所有第三方 Mod 或真实区块漫游。
- 没有图形客户端、真人多人联网、生产重混淆 JAR 启动、强杀/断电、所有外部死亡/删除 Mod、完整人工 Boss 战验收。相关结果不得写为通过。

## 复现

```sh
gradle --no-daemon clean build -Pci=true
python tools/run_stage_three_tests.py --gradle gradle
```

准备与基线文档相同的本地 TLM/SlashBlade 测试依赖。运行器新建仅监听回环地址、随机端口
的临时世界，检查每个显式 PASS 标记；Gradle 退出 0 不等于夹具通过。
可用 --fixture recovery/rebirth/fox/shrine/sword/blessing/white-se 选择场景，--offline 使用已齐备缓存。
日志、世界、原生备份与第三方 JAR 不提交。

## PR A 修改文件

- `.github/workflows/core-ci.yml`
- `build.gradle`
- `src/main/java/com/maidweapon/forge/MaidWeaponMod.java`
- `src/main/java/com/maidweapon/forge/compat/fox/BlackFoxShrineReturnValidation.java`
- `src/main/java/com/maidweapon/forge/compat/fox/FoxCompanionValidation.java`
- `src/main/java/com/maidweapon/forge/compat/fox/FoxSpiritTransferValidation.java`
- `src/main/java/com/maidweapon/forge/compat/tlm/ContractMaidLifecycleService.java`
- `src/main/java/com/maidweapon/forge/compat/tlm/ContractMaidRebirthBridge.java`
- `src/main/java/com/maidweapon/forge/compat/tlm/TlmEntityAdapter.java`
- `src/main/java/com/maidweapon/forge/compat/tlm/TlmFilmService.java`
- `src/main/java/com/maidweapon/forge/event/FoxSoulProtectionEvents.java`
- `src/main/java/com/maidweapon/forge/event/MaidBondCombatHandler.java`
- `src/main/java/com/maidweapon/forge/menu/MaidInjectorMenu.java`
- `src/main/java/com/maidweapon/forge/mixin/ContractCreativeGrowthMixin.java`
- `src/main/java/com/maidweapon/forge/system/contract/ContractCarrierData.java`
- `src/main/java/com/maidweapon/forge/system/contract/ContractChannelStorage.java`
- `src/main/java/com/maidweapon/forge/system/contract/ContractLifecycleService.java`
- `src/main/java/com/maidweapon/forge/system/deployment/ContractCarrierLossJournal.java`
- `src/main/java/com/maidweapon/forge/system/deployment/ContractCarrierLossService.java`
- `src/main/java/com/maidweapon/forge/system/deployment/ContractCompanionService.java`
- `src/main/java/com/maidweapon/forge/system/deployment/ContractWeaponLocator.java`
- `src/main/java/com/maidweapon/forge/system/fox/FoxSpiritLedger.java`
- `src/main/java/com/maidweapon/forge/system/fox/FoxSpiritState.java`
- `src/main/java/com/maidweapon/forge/system/fox/FoxSpiritTransferService.java`
- `src/main/java/com/maidweapon/forge/system/mastery/ContractMasteryCreativeGuard.java`
- `src/main/resources/assets/maid_weapon/lang/en_us.json`
- `src/main/resources/assets/maid_weapon/lang/zh_cn.json`
- `docs/archive/contracts/CONTRACT_LIFECYCLE_RECOVERY.md`
- `docs/validation/STAGE_THREE_VALIDATION.md`
- `src/main/java/com/maidweapon/forge/client/tooltip/ContractTransferTooltip.java`
- `src/main/java/com/maidweapon/forge/compat/tlm/ContractStageThreeValidation.java`
- `src/main/java/com/maidweapon/forge/compat/tlm/SoulTalismanService.java`
- `src/main/java/com/maidweapon/forge/compat/tlm/TlmBackupBridge.java`
- `src/main/java/com/maidweapon/forge/event/ContractAnomalyEvents.java`
- `src/main/java/com/maidweapon/forge/event/ContractRecoveryCommand.java`
- `src/main/java/com/maidweapon/forge/system/contract/ContractAnomalyRecoveryService.java`
- `src/main/java/com/maidweapon/forge/system/contract/ContractRecoveryJournal.java`
- `src/test/java/com/maidweapon/forge/system/contract/ContractRecoveryJournalTest.java`
- `tools/run_stage_three_tests.py`

## 阶段三 B：最终代码验证

基于 PR A #13 的远端 HEAD 3dc01db246d0626e3edc414592dc3408e95d312c，
分支 codex/white-fox-blessing-growth。能力/数值/兼容边界见 [白狐机制](../development/WHITE_FOX_BLESSING.md)。

- 最终 clean build 成功，JUnit **20 项，0 failure / 0 error**：原有熟练度 7、黑狐成长 5、
  恢复账本 4、新增白狐配置/累积效果/冷却持久化/未知 schema 4。
- 已重新执行 15 项源码/资源检查，全部通过；第 16 项狐模型 ZIP 白名单检查仍是上述 #12 基线失败。
- 中间构建曾在追加 schema 禁用断言后读取旧编译类导致一项失败；保留断言，以最终 clean build
  重新编译并验证通过。没有删除断言或将中间失败改写为通过。
- 服务端夹具使用实际 Forge 47.4.10、TLM 1.5.3、SlashBlade Resharped 1.9.65；没有安装 BladeTetra。
  属于 headless userdev 专服运行，不是生产重混淆 JAR 启动或图形客户端验收。

### 最终原生专服回归（7 组 + 2 次同世界重启）

| 场景 | 实际结果 |
| --- | --- |
| 异常恢复/重启 | CONTRACT_STAGE_THREE_PASS、CONTRACT_STAGE_THREE_RESTART_PASS；当前快照/真实 TLM 备份、胶片原生消费、原 UUID、拒绝重放、持久化隔离均通过 |
| 精炼/损毁/预算 | CONTRACT_REBIRTH_PASS、NATIVE_STAND_TRANSFER_PASS、CONTRACT_DESTRUCTION_PASS、CONTRACT_STORAGE_LIMIT_VALIDATION_PASSED、CONTRACT_SERVER_BUDGET_VALIDATION_PASSED、MULTI_CARRIER_LOSS_VALIDATION_PASSED、COMPANION_VALIDATION_PASS |
| 黑狐剧情/奖励 | BLACK_FOX_SHRINE_RETURN_PASS、CONTRACT_MASTERY_FOX_PASS |
| 白狐神社/魂符/完整女仆 | SHRINE_NATIVE_PASS、FOX_TRANSFER_NATIVE_PASS、FOX_COMPANION_NATIVE_PASS |
| 旧白狐净化 SE | WHITE_FOX_SE_PASS；为原有夹具补充实际合法驻留契约，保留原有攻击、武器数据断言 |
| 十级加护 | WHITE_BLESSING_PASS；Lv1–10 的实际属性与服务端伤害处理器、剧情未解锁拒绝、已有 Lv8 直接启用、主副手、双狐、复制冲突拒绝、显现/收回、脱战延迟/回血、撤下撤销均通过 |
| 黑狐活动剑核 | 同一加护夹具实际调用 SlashBlade releaseUsing；白狐副手共存，黑狐显现立即关闭原核心，收回后再次通过原生 SA 召唤 |
| 狐之誓约 | 新鲜冷却时图腾优先且不消耗冷却；致命事件只限伤一次；显现撤销自己的吸收护盾；WHITE_BLESSING_RESTART_PASS 确认同世界重启后冷却仍在 |
| #12 剑核十级回归 | SWORD_CORE_NATIVE_PASS、SWORD_CORE_RELEASE_PASS、SWORD_CORE_MASTERY_CREDIT_PASS、SWORD_CORE_GROWTH_PASS、SWORD_CORE_MULTI_CREDIT_PASS；原生 B1–B7、伤害/耀魂、400 tick、16/8 格、+7 跟随、有限协同与归因均保留 |

每组使用新建临时世界，重启组复用自己的同一世界；运行器检查所有显式 PASS 标记、退出码
与 FAIL 标记。没有向用户存档写入测试数据，也没有提交日志、世界或第三方 JAR。
伤害/图腾/誓约是实际服务端玩家的事件处理器注入测试；不是真人操作图腾的图形客户端实测。

### 未执行验收与下一阶段

- 未执行图形客户端、真人联网多人、生产重混淆 JAR 启动，以及真实登出/重登/死亡复生/
  维度旅行的白狐属性端到端验收；对应服务端撤销/重算处理器已实现，不能据此宣称实测通过。
- 未验证所有第三方保命事件优先级、墓碑、直接实体删除 Mod、强杀/断电或完整人工 Boss 战。
  旧备份有装备/背包/未知 cap、未知提交中断和不可关联身份仍保守停止；备份救援需要管理员预检查与显式恢复。
- 复制冲突测试覆盖可定位的同身份载体与旧授权/客户端复制保护；不会声称可以识别管理员
  任意离线改写文件或第三方在未加载容器中制造的每一份字节相同副本。
- 优先补充生产专服与图形/真人双狐验收；再设计损毁后自由狐灵重新安置接口、墓碑物品消费
  的可验证协议与整合包免死兼容。不要以重新发奖励刀或自动回滚装备代替这些协议。

### PR B 修改文件

- `build.gradle`
- `docs/validation/STAGE_THREE_VALIDATION.md`
- `docs/development/WHITE_FOX_BLESSING.md`
- `src/main/java/com/maidweapon/forge/MaidWeaponMod.java`
- `src/main/java/com/maidweapon/forge/api/IntrinsicSpiritApi.java`
- `src/main/java/com/maidweapon/forge/api/event/ContractMaidManifestedEvent.java`
- `src/main/java/com/maidweapon/forge/client/tooltip/FoxSoulTooltipHandler.java`
- `src/main/java/com/maidweapon/forge/compat/BlackFoxSwordArtCompat.java`
- `src/main/java/com/maidweapon/forge/compat/fox/BlackFoxSwordCoreValidation.java`
- `src/main/java/com/maidweapon/forge/compat/fox/WhiteFoxBlessingValidation.java`
- `src/main/java/com/maidweapon/forge/compat/fox/WhiteFoxSeValidation.java`
- `src/main/java/com/maidweapon/forge/compat/tlm/ContractMaidLifecycleService.java`
- `src/main/java/com/maidweapon/forge/system/contract/ContractAnomalyRecoveryService.java`
- `src/main/java/com/maidweapon/forge/system/contract/ContractRecoveryJournal.java`
- `src/main/java/com/maidweapon/forge/system/fox/FoxSpiritAbilityAuthorization.java`
- `src/main/java/com/maidweapon/forge/system/fox/WhiteFoxBlessingCooldownData.java`
- `src/main/java/com/maidweapon/forge/system/fox/WhiteFoxBlessingGrowth.java`
- `src/main/java/com/maidweapon/forge/system/fox/WhiteFoxBlessingService.java`
- `src/main/java/com/maidweapon/forge/system/fox/WhiteFoxPurifyingEdge.java`
- `src/main/resources/assets/maid_weapon/lang/en_us.json`
- `src/main/resources/assets/maid_weapon/lang/zh_cn.json`
- `src/main/resources/data/maid_weapon/contract_ability_progression/white_fox_blessing.json`
- `src/test/java/com/maidweapon/forge/system/contract/ContractRecoveryJournalTest.java`
- `src/test/java/com/maidweapon/forge/system/contract/WhiteFoxBlessingTest.java`
- `tools/run_stage_three_tests.py`
