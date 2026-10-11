# 第一阶段验证记录

开发基线：PR #10 `d10a309fc03141bcf9852ca5fe10d9e23d3f6f63`。交付前再次查询，HEAD 未变化。
环境：Minecraft 1.20.1、Forge 47.4.10、Gradle 8.7、Java 17；原生兼容测试安装
SlashBlade Resharped 1.9.65 和 Touhou Little Maid 1.5.3。
所有运行使用新建的可丢弃世界，没有打开或修改用户存档。测试世界、运行日志和第三方 JAR 不提交。

## 已通过

| 验证 | 结果与范围 |
| --- | --- |
| 完整 Gradle `clean build -Pci=true` | 编译、资源、打包、重混淆及 JUnit 通过；7 项测试，0 失败 |
| JUnit 行为测试 | 单调/交换/幂等合并、不累加 Boss、晚发现来源、保存恢复、管理员覆盖、玩家隔离、损坏/未来版本数据、完整 int 统计范围、legacy/tier/entities 规则、自定义进度、Lv.10 上限、数据副本、属性配置、JSON 校验、真实 reload listener 安全回退 |
| 无可选 Mod 的实际 Forge 服务端 | `CONTRACT_MASTERY_VALIDATION_PASS`：多武器共享、外国所有权拒绝、好感度/共鸣元数据不变、通道迁移、真实致死伤害、重复死亡结算拒绝、Forge 玩家死亡事件使待结算挑战失效、重建玩家实体、维度上下文、实际创造物品包处理器防伪、同一载体两种独立能力及资格 |
| 同一世界停服再启动 | `CONTRACT_MASTERY_RESTART_PASS`：从磁盘恢复玩家 A 的 Lv.2/有效击杀和玩家 B 的独立 Lv.1 |
| 原生剑核战斗 | `SWORD_CORE_NATIVE_PASS`：真实幻影剑/幻影刃造成伤害，B1–B7 执行并造成伤害，友方保护，双模式反复切换；夹具敌人损失 274.1858 生命 |
| 实际原生 SA 释放 | `SWORD_CORE_RELEASE_PASS`：使用原生 `releaseUsing`，Lv.1 仍召唤完整剑核，20 耀魂召唤、30 耀魂切换，反复切换及 400 tick/20 秒生命周期 |
| 原生熟练度归因 | `SWORD_CORE_MASTERY_CREDIT_PASS`：真实原生幻影击杀在换手后仍用发射时契约；范围斩实际 tick 改写 DamageSource 后仍用原实体来源；改换射手不归因给原玩家 |
| 黑狐原刀/胜利/资格 | `CONTRACT_MASTERY_FOX_PASS`、`BLACK_FOX_SHRINE_RETURN_PASS`：真实挑战进入、实体生成、终局剑核伤害破坏、原刀托管及原锚点归还、所有权、胜利 SA 解锁、实际黑狐显现/收回和迁居；查询共享 Lv.10 且没有分级路线 |
| 真实狐灵/女仆回归 | `SHRINE_NATIVE_PASS`、`SHRINE_OFFERING_NATIVE_PASS`、`FOX_TRANSFER_NATIVE_PASS`、`FOX_COMPANION_NATIVE_PASS`：祭坛、原生契约台、狐灵与外部契约切换、不同关系数值、女仆显现/收回、好感度 278/共鸣 141/旧成长档案、迁居及模型保存 |
| 源码与资源检查 | 15 项架构/契约/NBT/权限/共鸣/载体恢复/内室/Home Life/兼容/手册/资源检查通过；打包后的 `validate_mod_presentation.py` 通过。源码检查不等同实际游戏验证 |

原生组合注册会实际调用 `getNext` / `getNextOfTimeout` 验证安全空闲出口；测试专服成功加载并释放。
没有运行图形客户端，不能将这些结果声称为客户端渲染、联网 Tooltip 或客户端超时路径验证。
玩家死亡/重建测试使用 Forge 死亡事件及 FakePlayer 上下文，不是双客户端手动完整死亡重登。
原刀胜利夹具直接进入终局并破坏真实剑核，不声称完成整场人工 Boss 战。

## 已知基线失败与未验证项

这些失败没有通过改动正式战斗或资源来消除：

* 实际完整 `BlackFoxBossValidation` 在“phase one never enters second-half B or finisher”断言失败。
  同样的真实 SlashBlade/TLM 环境，在干净 PR #10 工作树复现完全相同失败。其后续全场断言未执行。
* `FoxChallengeValidation` 在旧“barrier fighting floor”断言失败；当前 PR #10 的
  `FoxChallengeArena` 明确铺设 `POLISHED_BLACKSTONE`。后续旧景观/剧情断言未执行。
  原刀胜利与契约成长已由独立聚焦夹具执行，不跳过或伪装旧夹具结果。
* 干净 PR #10 上 `validate_black_fox.py`、`validate_black_fox_iaido.py`、
  `validate_black_fox_new_skills.py`、`validate_black_fox_slash.py` 已失败：旧时序断言或缺少新增源码编译依赖。
* `validate_fox_models.py` 在 PR #10 与本分支均失败：衍生包新增 `pack.png` 未进入旧白名单，
  另有包内与外部 notice 对照不一致；`validate_bladesmith_models.py` 缺少旧开发归档。
  不修改资源或删断言掩盖问题。现有 CI 的 fox model 检查因此仍可能失败；构建/行为测试先独立执行。
* 图形客户端、多真人玩家联网、额外枪械/法术/原版非 SlashBlade 间接攻击尚未实际验证。
  未验证的战斗来源需要显式服务端归因适配，不凭手持武器猜测。

## 复现

见 [CONTRACT_MASTERY.md](CONTRACT_MASTERY.md) 的依赖目录与 API。

```sh
gradle --no-daemon clean build -Pci=true
python tools/run_contract_mastery_tests.py --gradle gradle
python tools/run_contract_mastery_tests.py --gradle gradle --native
```

最后一条会报告全部原生夹具结果；已知完整 Boss/旧场景夹具仍会使总命令失败。
单独聚焦验证可用 `-PmasteryFoxTest=true -Pci=true -PtlmRuntimeTest=true -PcompatTest=slashblade`，
原生剑核可用 `-PswordCoreTest=true`。必须提供空的 `compatRunDir`，接受该测试目录的 EULA，
并检查明确 PASS/FAIL 标记，不能只看 Gradle 的 JavaExec 退出码。
