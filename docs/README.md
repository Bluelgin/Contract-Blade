# 文档导航 / Documentation

本目录同时包含玩家说明与开发记录。历史设计中的旧参数、已撤下功能不代表当前版本；
游玩规则优先参考游戏内手册、当前配置和更新日志，测试记录不等于所有整合包都已验收。

## 玩家与服主 / Players and server owners

- [更新日志（中文）](../CHANGELOG.md) · [Changelog (English)](../CHANGELOG.en.md)
- [1.2.0 玩家向完整介绍](CHANGELOG_1.2.0_PLAYER.md)
- [配置：人数、成长、伤害与自动显现](CONTRACT_CONFIGURATION.md)
- [共鸣恢复、灵脉及技能费用](SPIRIT_RESONANCE_COSTS.md)
- [契约内景与日常生活](HOME_LIFE.md)
- [神社与白狐](SHINKITSU_SHRINE.md)
- [YSM 双狐：安装、许可与独立打包](YSM_MODELS.md)

## 开发与维护 / Development and maintenance

- [仓库目录、素材与归档约定](REPOSITORY_GUIDE.md)
- [构建与分发](STABLE_RELEASE.md) · [发布验收边界](RELEASE_1_2_0_CHECKLIST.md)
- [总体架构与扩展边界](ARCHITECTURE.md)
- [万华御札当前规则](ORDINARY_CONTRACT_DANMAKU_REWORK.md)
- [黑狐剑核成长](BLACK_FOX_SWORD_CORE_GROWTH.md)
- [白狐守护成长](WHITE_FOX_BLESSING.md)
- [赤月领域](AKATSUKI_DOMAIN.md) · [主动魂核规则](AKATSUKI_ACTIVE_SOUL_CORE.md)
- [资源来源及许可](../ASSET_SOURCES.md)

其余专题文档保留原路径，避免破坏脚本、历史 PR 和相互引用。特别注意：
`BLACK_FOX_MUSIC.md` 是已撤下功能的历史记录；共鸣基础文档与较早领域文档包含
被后续章节替代的设计。需要精确数值时检查对应当前规则和代码，不从历史测试报告抄参数。

For current player guidance, use the handbook, configuration guide, and changelogs.
The remaining technical documents include historical designs and bounded test evidence.
They are not blanket compatibility guarantees or proof of an uploaded release.
