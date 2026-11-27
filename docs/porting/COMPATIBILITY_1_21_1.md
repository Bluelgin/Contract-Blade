# 1.21.1 可选依赖核对（2026-11-27）

下面只确认发布元数据，不表示契约之刃适配成功。查询同时限定 Minecraft 1.21.1 与 NeoForge；未引入任何未验证坐标。API、Mixin 目标和实际联机测试仍须分别完成。

|项目|已确认版本|发布页|契约之刃状态|
|---|---|---|---|
|touhou-little-maid|1.5.3-neoforge+mc1.21.1|[版本](https://modrinth.com/mod/touhou-little-maid/version/tXG1TkGx)|已接入固定 TLM API，完整行为未验收|
|slashblade-resharped|2.2.1-1.21.1|[版本](https://modrinth.com/mod/slashblade-resharped/version/IEvIPjvJ)|尚未移植适配；版本存在，不能标为依赖未发布|
|timeless-and-classics-zero|1.2.0-hotfix|[版本](https://modrinth.com/mod/timeless-and-classics-zero/version/tJiZL0He)|尚未移植适配；版本存在，不能标为依赖未发布|
|epic-fight|21.17.3.1-mc1.21.1-neoforge|[版本](https://modrinth.com/mod/epic-fight/version/8HHhJt6i)|尚未移植适配；版本存在，不能标为依赖未发布|
|goety|3.2.3|[版本](https://modrinth.com/mod/goety/version/9DZs33ZX)|尚未移植适配；版本存在，不能标为依赖未发布|
|irons-spells-n-spellbooks|1.21.1-3.16.4|[版本](https://modrinth.com/mod/irons-spells-n-spellbooks/version/Gzh2DpBL)|尚未移植适配；版本存在，不能标为依赖未发布|
|patchouli|1.21.1-93-neoforge|[版本](https://modrinth.com/mod/patchouli/version/BIogJv2D)|尚未移植适配；版本存在，不能标为依赖未发布|

Blade Tetra：本轮查询仓库当前第一页分支与最新 Release；[v1.5.9](https://github.com/Bluelgin/Blade-Tetra-Forged-Styles/releases/tag/v1.5.9) ；同时读取 main 的 `gradle.properties`，实际为 `minecraft_version=1.20.1`、`forge_version=47.4.0`。未验证 NeoForge API。不能因 SlashBlade 已有 NeoForge 版推断 Blade Tetra 联动可用，也不能仅凭此查询证明未来不存在移植分支。

正式 Forge 还存在 TripleMagic、WanFaJieTong、TLM Native Power、SlashBlade 派生项目和 Boss 专用适配。它们没有在本轮完成版本与 API 核验，继续标为未移植/待核实，不能计为兼容通过。

当前 NeoForge 构建只有 TLM 为必需第三方依赖；本轮未添加这些可选 Mod 的无条件类加载。无可选 Mod 的原生服务端 smoke 才能证明该组合可启动，不能代替逐项联动验收。
