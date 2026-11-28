# 注册、Mixin与可选联动的实际边界

正式master的运行时注册快照来自本轮Forge真实客户端，原始行在 `evidence/forge-client.log.gz` 的 `VISUAL_AUDIT_REGISTRY`；其组合含SlashBlade，因此不代表缺可选前置的所有注册组合。源码位于 `src/main/java/com/maidweapon/forge`。没有将文件名或配置出现当作运行注册成功。

## 旧版实际观察与新平台源码核对

|注册类型|master本轮实际观察到的 `maid_weapon:` ID（省略前缀）|#38生产状态|
|---|---|---|
|items|awakening_vein、cat_blade、contract_emblem、contract_interior_key、dog_blade、endless_contract、endless_vein、flowing_vein、guard_knot、guardian_ribbon、heartbound_knot、maid_fast、maid_heavy、maid_injector、maid_sword、manka_talisman、oath_ring、resonance_sword_tassel、spirit_crystal、test_slashblade|只注册 `maid_weapon_port_probe:` 下 contract_bauble、contract_carrier、vein_one/two/three、oath_ring、endless_contract、spirit_crystal、white_fox_crystal；不是正式ID迁移|
|blocks / menus|maid_injector / maid_injector|无正式对应注册/GUI|
|entity_type|soul_anchor、black_fox_boss、black_fox_core、black_fox_minor_cut、black_fox_sword_core、black_fox_sword_bolt、black_fox_sword_actor|无本Mod对应正式实体；TLM已提供EntityMaid，不应另造假女仆实体代替|
|mob_effect / particle_type|oath_backlash / black_fox_parry|未正式迁移；使用原版效果/粒子只是当前部分实现|
|recipe_serializer / creative tab|weapon_upgrade / main|无正式对应注册|
|structure_piece / structure_type / structure_processor|shinkitsu_shrine / shinkitsu_shrine / shrine_entity|未迁移世界生成注册与数据|
|自有声音/BlockEntity|本次运行未观察到 `maid_weapon:` 项；旧sounds.json为空、MaidInjector为普通Block|不能把不存在的旧注册当缺口；应追踪实际原版/TLM音源和菜单链|
|components / attachments|旧版用NBT/Capabilities|Probe `contract_reference`、`contract_channels`与`maid_binding`已接入；合法类型/Codec不代表全部契约配置已迁移|
|网络|旧SimpleChannel与各玩法消息|仅native `status_request`、`status`、`oath_toggle`；有限状态HUD/O键，不含正式菜单/轮盘/全技能协议|

`test_slashblade` 是旧版明确保留的开发物品。是否继续保留其旧存档兼容，应单列决定，不把它作为普通玩家玩法验收目标。CSV中模型资源与此实际ID集合需逐项闭合；未注册的贴图不能当作可获得物品。

## Mixin不能直接复制加载

master实际打包四份配置：`maid_weapon.mixins.json`（契约耐久/存在性、伤害观察/取消/金额、投影菜单/背包/装备、客户端弹幕渲染）、`maid_weapon.slashblade.mixins.json`（原生招架/伤害/目标/时间线/剑驱动、祭坛刀架、来源凭证与颜色）、`maid_weapon.tacz.mixins.json`（枪械/脚本/发射/取出/消耗弹药）、`maid_weapon.soul-boss.mixins.json`（Boss伤害观察/计时）。各配置的真实成员、required和plugin以源JSON为准。

#38目前没有把它们加载为新平台Mixin。旧 `SoulSlashMixinPlugin`、`TaczMixinPlugin`、`SoulBossMixinPlugin` 门控是平台/依赖版本的一部分，不能把旧required配置换个文件名就开启。尤其Boss插件对版本与字节码形状做检查；新版本必须重新证明目标/伤害阶段，未知组合维持安全行为。

## 可选联动逐项追踪

|旧版入口/模块|实际依赖与作用链|1.21.1现状与下一验证|
|---|---|---|
|TouhouLittleMaidCompat/Helper、TlmReflection、compat/tlm|女仆owner/背包/任务/饰品/生命周期、native power、万华弹幕及渲染|固定TLM1.5.3 native API已部分接入；装备投影/工作/任务/弹幕完整链未完成。已有native快照和生命周期证据不能外推所有任务|
|SlashBladeCompat、BlackFox*、compat/fox、AkatsukiCombatCompat|刀数据/成长/原生战斗/SA、任务提供者、黑狐Boss与颜色/Shader/伤害凭证|对应版本发布元数据已确认，Neo adapter未移植；原型只验证Shader/基础狐外观。旧版本轮还出现模型predicate类型错误，须先隔离复现|
|BladeTetraStoryCompat|故事/获得与赤月等联动；不是SlashBlade基础包自带API|旧项目仍以1.20.1为已核对基准；未证明新API存在。记录具体版本调查边界，不能虚构坐标，也不能阻断其余核心|
|EpicFightCompat|`epicfight` **及 `ef_tlm` 同时存在**才认定原任务联动，近战分类/投影/任务|Neo未接入；EpicFight本体有版本不证明ef_tlm链可用，需分别固定版本和任务/动作验收|
|TaczCompat、compat/tacz|弹药投影权限、原装备与枪械状态、射击/扣弹Mixin、女仆任务/伤害|Forge#39原生投影CI通过；Neo没有这些adapter。完整枪械/重装/死亡/掉落/多玩家验收待做|
|GoetyCompat、IronsSpellsCompat|法器/法书/施法与任务、护盾/伤害联动|两本体有对应发布元数据；Neo执行/任务/装备/施法动画未接入|
|WanFaJieTongCompat、TripleMagicCompat|实际modId `touhou_little_maid_spell`，组合Irons/Goety权限/法器与任务路由|不能由两个本体版本推导桥接Mod已兼容；对应版本/API仍待核实，无Neo执行证据|
|BossLimitAdapter、DomainTargetCompatibility、SoulBossMixinPlugin|Mowzie's、Cataclysm、Legendary Monsters、Goety与衍生Revelation等：保护阶段、实际伤害观察/限伤、领域目标兼容|旧版插件含严格版本/指令形状门控（例如Mowzie1.8.2、Goety2.5.58.4、Cataclysm3.31、Legendary2.1.15），**这些不是1.21.1支持版本**。逐Boss做原生凭证/阶段负向实验，不识别就保守，不跨阶段强行追偿|
|TlmPecoVoiceBridge/voice policy、Patchouli手册|TLM语音来源/播放策略与游戏内引导|Neo未完整接入，需依赖缺失/实际音频/页面与资源验收|

发布版本链接见 [COMPATIBILITY_1_21_1.md](../COMPATIBILITY_1_21_1.md)（2026-11-27查询记录）；那是发布元数据，不是API实验或兼容承诺。本轮没有把任何未核实组合标成“兼容通过”，也没有把“我们尚未实现”改写成“外部依赖阻塞”。
