# 架构精修验证与交付记录

> 验证记录仅证明文中注明的版本、环境与场景；不代表当前提交或所有整合包均已验收。

日期：2026-10-13。基线：PR #15 `ef9847c473476213258734cfb92b10939d03c46b`。
提交前再次通过 GitHub 确认该 HEAD 未变化；没有合并、关闭或改写 #10–#15。
新 PR 的目标分支为 `codex/simplify-contract-lifecycle`。

## 执行结果

| 工作 | 结果 / 实际证据 |
| --- | --- |
| Java 17、Gradle 8.7、Forge 47.4.10 `clean build -Pci=true --offline` | 最终成功，包含编译、打包、重混淆、JUnit；10 个任务执行 |
| JUnit | 25 项，0 failure / 0 error / 0 skipped：Journal 9、Mastery 7、BlackFoxGrowth 5、WhiteFoxBlessing 4 |
| 核心 CI 校验 | 17 项通过，完整名单见下文 |
| 资源验证器拒绝测试 | 4 项通过：合法包、未知文件、已知资源篡改、重复 ZIP 条目；测试实际运行验证器，不依赖源码字符串 |
| 原生 Forge 专服 | 八组功能夹具均通过，另有两组同一世界的真实停服/重启通过 |
| 原版基线对照 | 在独立、干净 #15 工作树重新运行 recovery、blessing 及它们的实际重启；均通过。相同旧夹具在本分支也通过 |
| 模型 ZIP | 两个狐模型包及 Akatsuki 源包均未修改；原始包 SHA、原动画字节、正式三狐成员清单、内部 SHA 与许可镜像一致 |
| 可选联动 | 编译边界检查通过；实际专服加载 TLM 1.5.3 与 SlashBlade 1.9.65，未加载 BladeTetra |

实际 JAR SHA-256：

- TLM 1.5.3：`8c341567e57aa3c006f9fa85c7a5e3bc36a53b1cca4e3538c3db705eeeea1bbc`
- SlashBlade 1.9.65：`2a6eb8d2ead295cb3838b551b9f3cf0e81de97966dbc91b84cf2c7589f3a4361`

以上依赖仅供测试，不提交或打包。所有运行使用可丢弃世界，未读取用户存档。

## 原生夹具覆盖

| 组别 | PASS 标记 / 范围 |
| --- | --- |
| architecture（新增） | CONTRACT_ARCHITECTURE_PASS：编码拒绝保留活实体、武器/BindingId/原任务/作息回滚；捕获监听器异常；生成前异常及候选 UUID 篡改拒绝；Forge 取消生成；成功生成后监听器异常；权威快照清除；恢复完成观察者异常不重放；压缩字节/旧 NBT 原地修改立即失效；世界存在变化立即撤权；未知位置损毁不伪造存在事实，狐灵账本合法解除；管理员修复拒绝覆盖已加载 UUID、可修复明确审计后的陈旧事实 |
| recovery | CONTRACT_STAGE_THREE_PASS：正常缔约/显现/收回、魂符迁居、原生死亡保护、保存拒绝、旧事故/创造来源兼容、原生备份照片、损毁胶片、活动/休眠多通道；内景装备/位置/真实 TLM 背包交互及说明书安全夹具也通过 |
| recovery 重启 | CONTRACT_STAGE_THREE_RESTART_PASS：真实世界保存/停服/加载；旧事故归档清理、完整原 UUID 显现/收回、已显现实体的存在事实跨重启防重复 |
| rebirth | CONTRACT_REBIRTH_PASS / CONTRACT_DESTRUCTION_PASS / MULTI_CARRIER_LOSS_VALIDATION_PASSED / COMPANION_VALIDATION_PASS：原生复活、精炼/铁砧/挂刀台、实际耐久消费、释放原女仆、恢复预算、多载体及随行 |
| fox | BLACK_FOX_SHRINE_RETURN_PASS / CONTRACT_MASTERY_FOX_PASS：原黑狐刀、剧情胜利、原奖励归属、迁居及全局成长 |
| shrine | SHRINE_NATIVE_PASS / FOX_TRANSFER_NATIVE_PASS / FOX_COMPANION_NATIVE_PASS：黑白狐供奉/迁居、原 UUID 与内景绑定、实际显现/收回 |
| white-se | WHITE_FOX_SE_PASS：白狐既有原生净化能力 |
| blessing | WHITE_BLESSING_PASS：Lv.1–10 属性/伤害、主副手及复制刀非叠加、驻留/显现撤权、回血、图腾/誓约、真实耐久损毁释放、自由狐灵重新缔约 |
| blessing 重启 | WHITE_BLESSING_RESTART_PASS：同一个真实保存世界的誓约冷却保留 |
| sword | SWORD_CORE_NATIVE_PASS / SWORD_CORE_RELEASE_PASS / SWORD_CORE_MASTERY_CREDIT_PASS / SWORD_CORE_GROWTH_PASS / SWORD_CORE_MULTI_CREDIT_PASS：B1–B7、四类原生攻击、400 tick、十级成长、范围、七格跟随、双模式、协同及两位服务器玩家的归因隔离 |

这些是实际加载 Forge/TLM 的开发专服测试，使用 FakePlayer、真实实体、真实 NBT 和 Forge 事件处理器。
它们不是完整真人客户端验收。区块卸载路径包含 `UNLOADED_TO_CHUNK` 原生移除事件注入，
另有实际服务器保存/重新加载；没有声称完成真人跨区块/跨维度旅行。
成功后的异常通过实际 Forge 通知监听器注入；没有独立注入每一种第三方可选 Mod 的初始化异常。

复现命令（先准备正确本地 TLM/SlashBlade JAR）：

```sh
gradle --no-daemon clean build -Pci=true
python tools/run_stage_three_tests.py --gradle gradle --offline
python tools/test_fox_model_validator.py
```

执行期间遇到的环境/夹具问题均保留记录：冷缓存代理与 Minecraft 库下载失败后补齐依赖并重建；
新增缓存夹具最初绕过 EmbeddedSpiritApi 的合法建契约入口，已修正其初始化，未放宽权限或改动预期；
并行运行基线对照导致本分支白狐重启的 Gradle 缓存锁超时，服务端未启动。等待基线结束后，
串行重启原白狐世界并获得 RESTART_PASS，未删除锁文件或创建替代世界掩盖失败。
最终再执行干净 build，25 项 JUnit 通过。

## 源码与资源检查

核心 CI 全部通过：architecture、legacy_core_boundary、contract_mastery、contract_nbt_safety、
contract_permissions、contract_resonance、carrier_loss_recovery、patchouli、contract_interiors、
home_life、epicfight、item_models、projection_baubles、companion、shrine、player_tooltips、fox_models。
额外的 fox_challenge 与最终 JAR 的 mod_presentation 检查也通过。

`validate_fox_models.py` 在干净 #15 中首先独立复现。真实不一致包含 pack.png、尾巴动画、
三狐页面及版本号，还包括独立许可目录未同步 Akatsuki 署名与 SHA 清单。
本分支保留原始 ZIP/资源字节，修正验证器并同步已随正式 ZIP 附带的说明。
新增的外部逐文件 SHA 清单严格拒绝未知/重复成员及已知资源篡改；原始源包 SHA 校验、
原有动画不变断言、内部校验表和许可检查仍在。Akatsuki 资源与现有独立源包逐字节比较，
原生动画的已知可选服饰/武器缺失骨骼集合明确固定，没有泛化忽略未知资源。

以下额外旧检查在干净 #15 和本分支都失败，未通过改玩法或删除断言制造绿色结果：

| 旧检查 / 构建 | 已复现原因 |
| --- | --- |
| validate_black_fox.py | 老源码形态断言期待已变化的 `age == HIT` 写法 |
| validate_black_fox_iaido.py | 旧独立夹具的 unchanged hit timing 断言与基线时间线不符 |
| validate_black_fox_new_skills.py | 独立 javac 的源文件列表缺少现有依赖，编译失败 |
| validate_black_fox_slash.py | 独立 javac 未包含已引用的 BlackFoxIaidoTimeline 等依赖 |
| validate_bladesmith_models.py | 基线缺少 `art/bladesmith/archive/contract-bladesmith-1.0.0.zip` |
| stableRelease 构建 / validate_stable_release.py | prepare_stable_release.py 的裁剪未适配基线已增加的挑战引用，`fox.challenge.` 断言失败，故不存在可验证的稳定版 JAR |

这些相关玩法类和裁剪脚本本次没有修改，真实黑狐/白狐原生夹具通过。
上述失败属于保留的技术债，不纳入“全部检查绿色”的声明；核心 PR CI 仍按完整原检查运行。

## 事务、兼容及保留设计

完整流程与模块职责见 [ARCHITECTURE_REFINEMENT.md](../archive/contracts/ARCHITECTURE_REFINEMENT.md)。

- 捕获：入口身份 → 准备/原生清理 → 完整快照编码提交 → 原顺序驻留切换/删除实体 → 通知。
  编码/准备拒绝恢复武器、绑定、任务和作息，保留唯一活实体。临时投影清理与有效位置观察保留，
  不从旧幻影数据复原装备；原生任务回滚拒绝有日志。已观察的服务器全局历史成长迁移保持单调，
  失败不回退已获得的全局进度。
- 显现：入口/快照身份 → 构造与定位 → 可变回调后的提交检查 → 真实实体发布 → 原顺序运行时/归属/快照清除/事件。
  发布前失败保留快照；发布后初始化/通知异常不能把成功变成失败或遗留双权威数据。
- 恢复：调度只处理预算、轮转、重试及完成；单次救援返回命名结果，完成任务先退出队列再通知。
  UNKNOWN 存在事实独立于 FREE_MAID 目的地；旧诊断不会重新成为正常权限来源。
- 保留压缩/旧存储格式、UUID/BindingId、女仆完整数据、魂符/胶片、内景、原生任务、全局成长、
  通用能力与狐灵账本。没有新万能 Service 或自建备份管理器；无玩法数值、奖励、400 tick、七格跟随改动。
- 未删除仍承担兼容作用的状态枚举、事故档案、旧队列保留字段、公开方法或 Token 剧情身份。
  `record(...reason, confirmed)`、`cleanup(...record)`、`proofKey` 保留并注明兼容语义，内部改用明确入口。
  删除的是重复回滚写法、布尔救援完成含义、陈旧缓存授权方式与 UNKNOWN→PRESENT 猜测。

对于彻底绕过实体事件的删除，权限 2 操作者可在审计所有维度和未加载区块后使用
`/maidweapon recovery confirm-absent <player> after-world-audit`。它拒绝已加载 UUID 冲突，记录操作，
只修复存在事实；既不制造实体，也不消费/接管 TLM 备份。这不是自动死亡推断。

## 未验证与剩余债务

未执行图形客户端、真人多客户端、生产重混淆 JAR 启动、全场人工 Boss 战、断电/强杀、
所有可选 Mod 组合或旧生产世界整体迁移。双语 Tooltip 是源码/翻译键与格式参数检查，没有视觉验收。
原生照片拒绝生成后仍可能被 TLM 消费；跨文件持久化和物品交付不保证断电时精确一次。
可选投影和原生任务为可解释的 best-effort 回滚；完全绕过事件的外部删除仍需管理员审计。
授权快照结构比较增加了复制/比较成本，未完成大型整合包多玩家性能基准。
原始字符串目的地仍是公开/旧存档协议，内景/通道部分仍有直接 NBT 操作。
上述旧独立检查、稳定版裁剪及可选 Akatsuki 服饰骨骼清理仍待独立维护。

客观评价：生命周期主流程可顺序阅读，发布/提交和异常边界更明确；恢复调度与结果分离，
身份/存在协议收敛，能力缓存不再代表历史授权。通用能力框架保持，可扩展武器/法杖无需修改这些事务。
收益来自实际失败边界和测试，不来自减少类数/行数。该实现仍需原生兼容与运行测试，
不能把主观“9/10”当作已被证明的可靠性等级。

## 修改文件清单

- `.github/workflows/core-ci.yml`
- `build.gradle`
- `docs/archive/contracts/ARCHITECTURE_REFINEMENT.md`
- `docs/validation/ARCHITECTURE_REFINEMENT_VALIDATION.md`
- `src/main/java/com/maidweapon/forge/api/IntrinsicSpiritApi.java`
- `src/main/java/com/maidweapon/forge/compat/tlm/ContractArchitectureValidation.java`
- `src/main/java/com/maidweapon/forge/compat/tlm/ContractMaidKeys.java`
- `src/main/java/com/maidweapon/forge/compat/tlm/ContractMaidLifecycleService.java`
- `src/main/java/com/maidweapon/forge/compat/tlm/ContractMaidRebirthBridge.java`
- `src/main/java/com/maidweapon/forge/compat/tlm/ContractMaidStorage.java`
- `src/main/java/com/maidweapon/forge/compat/tlm/SoulTalismanService.java`
- `src/main/java/com/maidweapon/forge/compat/tlm/TlmEntityAdapter.java`
- `src/main/java/com/maidweapon/forge/event/ContractAnomalyEvents.java`
- `src/main/java/com/maidweapon/forge/event/ContractRecoveryCommand.java`
- `src/main/java/com/maidweapon/forge/mixin/ContractEntityPresenceMixin.java`
- `src/main/java/com/maidweapon/forge/system/contract/ContractAnomalyRecoveryService.java`
- `src/main/java/com/maidweapon/forge/system/contract/ContractRecoveryJournal.java`
- `src/main/java/com/maidweapon/forge/system/deployment/ContractCarrierLossService.java`
- `src/main/java/com/maidweapon/forge/system/deployment/ContractWeaponLocator.java`
- `src/main/java/com/maidweapon/forge/system/fox/FoxSpiritAbilityAuthorization.java`
- `src/main/resources/licenses/fox_models/AKATSUKI-CREDITS.md`
- `src/main/resources/licenses/fox_models/AKATSUKI-LICENSE-CC-BY-NC-SA-4.0.txt`
- `src/main/resources/licenses/fox_models/CREDITS.md`
- `src/main/resources/licenses/fox_models/MODEL_ASSET_SHA256SUMS.txt`
- `src/test/java/com/maidweapon/forge/system/contract/ContractRecoveryJournalTest.java`
- `tools/fox_model_runtime_manifest.json`
- `tools/run_stage_three_tests.py`
- `tools/test_fox_model_validator.py`
- `tools/validate_carrier_loss_recovery.py`
- `tools/validate_fox_models.py`
