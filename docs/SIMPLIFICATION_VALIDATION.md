# 生命周期精简验证记录

日期：2026-10-13。开发基线：PR #14 `ca7f347b21508bb4a78b79738feeec895ebbccb6`。机制与模块审查见 [CONTRACT_LIFECYCLE_SIMPLIFICATION.md](CONTRACT_LIFECYCLE_SIMPLIFICATION.md)。

## 环境与执行方式

Java 17.0.20、Gradle 8.7、Minecraft 1.20.1、Forge 47.4.10、实际 Touhou Little Maid 1.5.3、SlashBlade Resharped 1.9.65。没有安装 BladeTetra；依赖与可选接口未改为强依赖。测试使用全新可丢弃世界，不打开用户存档。

执行 `gradle clean build -Pci=true --offline`，包含编译、资源处理、打包、重混淆及 JUnit。**22 项 JUnit，0 failure / 0 error**。新增/更新旧事故幂等清理、未来 schema 原样保留、实体存在事实保存/重载、狐灵原 UUID 随账本迁居保留等检查。

真实 Forge 专服使用：

```sh
python tools/run_stage_three_tests.py --gradle gradle --offline
```

它依次运行 recovery、rebirth、fox、shrine、white-se、blessing、sword 七组专服夹具，并在同一实际世界重启 recovery 与 blessing。不是只检查 Gradle 退出码：同时检查明确 PASS 标记、失败标记以及服务端 `logs/latest.log`。TLM 与 SlashBlade JAR 仅用于本地运行，不提交或打包。

| 夹具 | 实际结果与范围 |
| --- | --- |
| recovery | CONTRACT_STAGE_THREE_PASS；普通缔约/迁居、原生死亡保护、失败编码保留活实体、旧标记与回执兼容、卸载防重复、真实备份照片、耐久胶片、多通道损毁 |
| recovery 重启 | CONTRACT_STAGE_THREE_RESTART_PASS；完整快照旧 COMMITTING 记录清理、原 UUID 显现/收回；停服前真正显现的另一位女仆保留存在事实，重启后复制快照不能重复生成 |
| rebirth | CONTRACT_REBIRTH_PASS / CONTRACT_DESTRUCTION_PASS / MULTI_CARRIER_LOSS_VALIDATION_PASSED / COMPANION_VALIDATION_PASS；100 次原生精炼、铁砧/挂刀台、真实普通剑损毁、多个载体、全服预算、随行与正常原生复活 |
| fox | BLACK_FOX_SHRINE_RETURN_PASS / CONTRACT_MASTERY_FOX_PASS；原黑狐刀、终局胜利、原奖励与主人、魂符迁居、模型、全局成长 |
| shrine | SHRINE_NATIVE_PASS / FOX_TRANSFER_NATIVE_PASS / FOX_COMPANION_NATIVE_PASS；神社供奉、黑白狐迁居、UUID/内景绑定、真实女仆显现/收回 |
| white-se | WHITE_FOX_SE_PASS；既有白狐净化能力 |
| blessing | WHITE_BLESSING_PASS；每一级属性/伤害处理器、主副手只结算一次、复制刀仍可用且不叠加、驻留/显现切换、回血/图腾/誓约；狐灵在普通铁剑中真实耐久损毁后释放，再通过普通捕获缔约到新武器，保留原 UUID/剧情身份 |
| blessing 重启 | WHITE_BLESSING_RESTART_PASS；实际停服重启后誓约冷却保留 |
| sword | 五个 SWORD_CORE_*_PASS；原生 B1–B7、四类攻击/伤害、20/30 耀魂、400 tick、十级成长、16/8 格范围、+7 跟随、锁定、有限双模式协同、两个服务器玩家的归因隔离 |

追加运行现有 ContractHomeSafetyValidation：CONTRACT_HOME_SAFETY_VALIDATION_PASSED，覆盖居民位置存档/恢复、内景不使用战斗投影、真实 TLM 背包 pickup/shift/swap、食物与装备 NBT、说明书升级及床交互。它不等于图形客户端的完整内景旅行验收。

## 二十项场景覆盖

| 场景 | 覆盖与边界 |
| --- | --- |
| 1. 首次缔约、显现、收回 | 真实 TLM 实体及生命周期服务，PASS |
| 2. 更换武器、魂符迁居、旧契约 | 实际缔结台、稳定绑定、实体 UUID 与好感/模型/装备保留，PASS；复制普通魂符不再受消费回执限制 |
| 3. 创造获取、复制、移动 | 实际创造数据包成长防伪仍通过；复制契约/白狐原刀不封锁操作，主副手只结算一次；未操作图形创造背包 |
| 4. 旧不可信标记 | 原武器写入旧字段、REVIEW_REQUIRED、旧 EMERGENCY 归属记录后重新显现，PASS |
| 5. 临时 ItemStack 副本 | 无物理载体冲突扫描；迁居/成长视图与显现副本实测，PASS；副本不能重复生成真实 UUID |
| 6. 未加载区块 | 实际 Entity.remove(UNLOADED_TO_CHUNK) 注入；原存在事实保留，复制快照拒绝重生，PASS。另有实际服务器重启存在事实验证；不是完整真人跨区块旅行 |
| 7. 致命伤正常保护 | 实际 TLM MaidDeathEvent 与 Forge LivingDeathEvent 触发回收，PASS |
| 8. 保存失败不能删唯一实体 | 对真实武器注入过大载荷，编码拒绝，活女仆仍存活且死亡取消，PASS |
| 9. 绕过普通死亡处理 | 直接 KILLED / DISCARDED 和真实 EntityLeave 路径；最后当前快照救援/原生备份途径，PASS。没有运行每一种第三方死亡 Mod |
| 10. 已显现时武器损毁 | 真实载体损毁保留原实体并解除绑定，原有损毁夹具 PASS |
| 11. 储存状态武器损毁 | 原生耐久消费与普通显现/释放，PASS；新增活动+休眠多通道载体分别释放两个原 UUID，PASS |
| 12. 释放失败紧急胶片 | 实际取消原生生成、真实耐久消费、单胶片交付、ItemFilm 复活保留 UUID/装备，PASS |
| 13. 没有有效快照 | 全服预算下只完成原生备份提示/运行时清理，不制造默认实体或无限等待，PASS；丢失身份的旧快照仍留给管理员诊断 |
| 14. TLM 原生备份照片 | 实际 MaidBackupsManager 保存/读取 API、ItemCamera 生成照片、ItemPhoto.useOn 恢复原 UUID 和历史装备，PASS；生成照片不清除活实体存在事实 |
| 15. 恢复后重新缔约 | 原生照片恢复回原契约；紧急胶片恢复到新契约，PASS |
| 16. 狐灵身份与剧情 | 原账本授权、胜利/原刀资格、迁居与释放后重新安置，PASS；外观或普通新住处不授予奖励原刀能力 |
| 17. 黑狐/白狐各等级 | 黑狐五组真实原生夹具；白狐每级实际属性与伤害事件处理器，PASS |
| 18. 内景进入/退出/居住 | 源码/资源/时钟/位置与稳定内景绑定检查 PASS；额外内景安全夹具（原居民位置、背包/装备、床、投影与说明书）PASS。完整客户端进入/退出、家具演出及内景损毁后的玩家旅行仍需人工验收 |
| 19. 多人、重启、旧存档 | 两个真实服务端玩家对象的归因/权限隔离、同世界两组正常停服重启、旧字段注入/格式读写，PASS；没有真人客户端联网、真实登录退出或断电测试 |
| 20. 中英文 Tooltip | 客户端源码边界、自然语言文案、翻译键及格式参数匹配，PLAYER_TOOLTIP_PASS；尚未在图形客户端视觉验收 |

## 静态与资源检查

以下 16 项通过：contract_mastery、architecture、legacy_core_boundary、contract_nbt_safety、contract_permissions、contract_resonance、carrier_loss_recovery、patchouli、contract_interiors、home_life、epicfight、item_models、projection_baubles、companion、shrine、player_tooltips。

旧静态检查曾要求 Tooltip 显示已停用的 Part 档案、内景损毁必须制造胶片。本次按明确需求改为检查“档案仍保存但不进入玩家 Tooltip”和“内景不删除活居民、委托统一耐久救援”；没有删除实体、编码或能力行为断言掩盖失败。旧白狐复制断言按新设计改为实际非叠加与仍可用断言，所有各级数值检查保留。

`validate_fox_models.py` 的 ZIP 白名单与已有 `pack.png` 不匹配仍失败；在干净 #14 工作树独立复现同一失败，本次未改模型包或该断言。因此完整现有 CI 可能在这一既有步骤失败，不能称整条 GitHub CI 已通过。新增 PR base 分支触发与 player_tooltips 检查。

## 仍需人工/整合包验收

没有图形客户端、真人多客户端网络、发布重混淆 JAR 的生产专服启动、全场人工 Boss 战、强杀/断电，或全部可选 Mod 组合验证。专服 FakePlayer、事件处理器注入与原生方法调用不是这些场景的等价物。

已知设计边界：

- 只有确认的耐久最终消费自动安排损毁补偿；指令删除、随意扔掉物品或未知自定义销毁渠道不制造补偿。
- 卸载女仆不能凭“当前找不到”重建。直接改写存档、第三方彻底绕过实体移除/加入事件，或未曾被当前运行观察到的旧未加载实体不能由此夹具证明安全，应先定位原实体/检查原存档。不会自动删除身份或造默认女仆。
- 旧已预占交付任务保留而不重放；新路径没有复杂事务，不承诺断电时精确一次物品补偿。普通物品副本和历史装备回滚允许，真实 UUID 冲突仍拒绝。
- 原生 TLM 可能在被拒绝生成后消费照片/胶片；其备份文件仍由本体管理，可重新取得照片。本次不扩展为一套物品消费事务。
- TLM 旧版本若没有原生备份指令，需要自行更新本体或使用其已有救援方式；契约之刃不提供备份目录格式兼容器。
