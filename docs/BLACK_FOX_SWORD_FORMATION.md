# 黑狐身旁凝剑（开发版）

普通齐射与一阶段幻影佯攻改为原生幻影剑阵。剑雨、黑洞随机散射仍沿用原来的发射方式；本次没有统一增加其他技能的数量、伤害或速度。

| 阶段 | 数量 | 首剑发射前停留 | 方向锁定 | 发射间隔 | 初速 |
| --- | --- | --- | --- | --- | --- |
| 一阶段 | 3 | 14 tick / 0.7 秒 | 发射前 4 tick | 3 tick | 1.2 格/tick |
| 二阶段 | 5 | 10 tick / 0.5 秒 | 发射前 4 tick | 2 tick | 1.5 格/tick |

未射出的剑跟随黑狐位置；锁定后保存玩家当时的目标点，发射后不再追踪。悬停时不造成伤害、不执行原生碰撞或消耗命中记录。硬直、潜入次元、阶段切换和会话结束清理未发射的剑；飞行弹幕沿用会话统一数量上限与到期清理。

服务端 `BlackFoxSwordFormation` 管理有限剑阵；`BlackFoxDomainAttacks` 统一登记悬停和飞行弹幕（总上限 60）。Boss 主控制器不添加剑阵执行逻辑。客户端 `BlackFoxSwordRifts` 只采样同步的开始时间、槽位掩码和朝向，复用 `BlackFoxRiftVisual` 的贴图、动态发光 shader 与降级路径。裂缝随凝剑展开、锁定时增亮、发射前收束，不新增贴图或持久化实体类型。

验证：`tmp/black-fox-sword-formation-validation.log` 中的 `BLACK_FOX_SWORD_FORMATION_PASS`、`BLACK_FOX_SECOND_FORMATION_PASS` 和 `BLACK_FOX_BOSS_PASS`。覆盖原生实体悬停、无攻击碰撞、瞬移跟随、锁定目标、逐剑发射、两阶段速度及清理，并复跑既有 Boss 战回归。美术观感仍需客户端实机确认。

该功能只属于开发版；公开 1.1.1 发布包保持不变，不包含黑狐 Boss。
