# 黑狐「终式·封界剑核」成长（阶段二）

基础：PR #11 `ced992e8e9c12b0829ed432b8be7db8704ecdea4`，包含 PR #10 `d10a309`。
玩家全局 ContractMastery 是唯一等级来源；武器 MaidData、狐灵关系与女仆好感度不存储技能等级。

## 使用与成长

持续时间始终为 400 tick。原生蓄力释放召唤消耗 20 耀魂；Lv.5 起再次释放切换模式，
总消耗 30 耀魂（原生 20 + 原有额外 10）。保留部署 12 tick、最后 8 tick 不切换的窗口。
Lv.1–4 已有剑核时再次释放返回原生 NONE，不消耗切换资源。

| 等级 | 名称 | 新增行为（累积） |
| --- | --- | --- |
| 1 | 凝核 | 固定式基础紫色幻影剑阵，16 格范围，最多 8 个候选目标；每 12 tick 共 4 个投射物预算 |
| 2 | 追印 | 实际原生命中附加 80 tick 剑印；后续火力优先标记目标，紫色粒子显示剑印 |
| 3 | 护契 | 玩家 4 格内或正在以玩家为目标的敌人优先；不授予无敌或伤害拦截 |
| 4 | 连锁 | 解锁原生幻影刃；有有效剑印的敌人死亡后从死亡位置发射 2 枚弱追击幻影剑；40 tick 全核冷却 |
| 5 | 影袭 | 解锁连击 B，固定剑核，8 格索敌，1 名剑影执行完整原生 B1–B7 |
| 6 | 御核 | ANCHORED → OWNER_FOLLOW；两种攻击模式都使用移动中心；高度严格保持玩家脚部坐标 +7，水平平滑跟随 |
| 7 | 分锋 | 最多 3 名近战剑影分配不同目标；剑阵增加原生纵向幻影刃 |
| 8 | 敕令 | 读取原生 TargetEntityId，在当前模式合法范围内优先；剑阵增加原生波刀龙胆 |
| 9 | 逆刃 | 真正收到正数 Forge LivingDamage 后优先攻击合法范围内攻击者 60 tick；响应冷却 24 tick，紫色反馈 |
| 10 | 封界 | 切换后开启 24 tick 双模式协同；近战最多 3 名剑影，弹幕缩为每轮 1 枚基础剑，窗口结束回到所选主模式 |

参数可由数据包调整，但黑狐适配层继续强制广域半径大于近战半径、最多 8 个广域候选和 3 名剑影。
当前位置中心是剑核投影到玩家高度的 `centre`，不是悬空实体自身的 Y，避免 7 格高度吞掉近战范围。
正常水平跟随每 tick 收敛 35%；超过 24 格的瞬移直接重定位，保持 7 格高度。固定核保留原有 64 格主人距离清理。
移动不续期，模式切换也不重置寿命。

## 架构与数据

`BlackFoxAbilityAdapter` 使用既有 `maid_weapon:sealed_sword_core` 注册资格、feature、parameter 和 Java fallback。
正式路线位于 `data/maid_weapon/contract_ability_progression/sealed_sword_core.json`，与 fallback 等价。
通用 ContractAbility 模块继续不引用黑狐实体或 SlashBlade。

`BlackFoxSwordCoreGrowth` 定义功能词表、边界、累积 milestones 与只读 Profile。
`BlackFoxSwordArtCompat` 在原生 SlashArts chooser 中统一 query，只有 READY 且含 summon_core 才准备释放。
同次同步原生 click action 消费准备好的结果；剑核、剑影不自行查询等级，也没有逐级 if-else。
未注册、DENIED、无路线或路线缺少 summon_core 均安全拒绝执行。

活动剑核由入口服务每 20 tick 检查玩家全局等级/路线定义是否变化，变化时重新 query 并传入 Profile。
因此存续期间升到 Lv.6 也能进入 OWNER_FOLLOW；数据包重载生效，不必等待下一次召唤。
管理员降级会关闭不再允许的模式/功能；退回固定式时冻结当前中心。

攻击模式 `AttackMode.BARRAGE / COMBO_B` 与位置模式 `PositionMode.ANCHORED / OWNER_FOLLOW` 独立。
旧 `following()` accessor 仅作原有渲染/验证兼容，明确只表示 COMBO_B，不表示位置跟随。

默认目标优先级：受击攻击者 > 原生锁定 > 对主人构成威胁 > 剑印 > 距离。
先过滤模式范围、同世界、友方和视线，再排序；锁定/报复不能绕过 8 格近战范围。
剑阵一半火力给最高优先目标，其余分散压制。连击 B 中的连锁追击也使用独立的 8 格发射/命中范围。有效 B 剑影保留完整连击，不因距离排名小变动重启 B1；
明确锁定或受击报复可以抢占优先级较低的剑影。失效、出范围或友方目标立即取消。

## 原生攻击与归因

基础剑复用 BlackFoxPhantomCompat；幻影刃与纵向幻影刃调用原生 Drive.doSlash，分别使用 0/-90 度 roll；
波刀龙胆调用 WaveEdge.doSlash。原生 count=1 实际生成 2 枚，因此占用 2 个预算并对每枚使用一半系数，
不通过追加原生波刀制造无界火力。没有自行调用 hurt 补伤害或替换原生 B 连击。

原生 WaveEdge 在 setOwner 前 addFreshEntity；BlackFoxCoreNativeBarrage 用受限同步 ThreadLocal
暴露真实发射 actor，EntityJoinLevelEvent 只为该调用内的原生 drive 提前设置射手、颜色和 launch credit。
finally 恢复上下文，普通原生技能和其他玩家不受影响。

剑影复制召唤时的原刀，紫色修改只发生在私有副本和它的原生投射物上。原生低评级 SlashEffect
客户端去色分支对黑狐剑影应用紫色主体/暗紫边缘，不提高攻击评级。剑核/剑印/受击响应/协同光环统一紫色。

独立资格仍检查黑狐原刀、胜利标记、奖励主人/Origin、原居所、黑狐身份主人、当前所选 SA、狐灵账本 token，
并经过 ContractCombatCreditResolver 的真实契约/所有权/通道规则。Lv.10 本身无法授予 SA。
运行期以召唤 Origin + binding 在主人库存中定位真正载体；剑影副本从不成为契约权威。
所有原生发射仍使用 ContractCombatCredits.stampLaunch 和既有范围斩 provenance；死亡不推断当前手持武器。
换手后的新剑影也不会复制普通铁剑或另一个契约，友方保护继续作用于碰撞与原生 AABB 选择。

## 验证入口

- `gradle build -Pci=true`：既有熟练度 JUnit + BlackFoxSwordCoreGrowthTest。
- `python tools/validate_contract_mastery.py`：全局权威与实体执行器边界，仅源码检查。
- `-PswordCoreTest=true -Pci=true -PtlmRuntimeTest=true -PcompatTest=slashblade`：可丢弃专服中的真实原生释放、
  B1–B7、归因及 BlackFoxSwordCoreGrowthValidation。必须检查明确 PASS/FAIL 日志，不能只看 JavaExec 返回码。
- `-PmasteryFoxTest=true`：真实挑战终局胜利、原刀归还和契约初始化，更新为正式 READY/Lv.10 路线预期。

实际执行结果及未验证边界记录在 `SWORD_CORE_GROWTH_VALIDATION.md`。
