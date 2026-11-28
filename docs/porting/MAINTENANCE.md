# Dual version branch and release policy

Stable lines:
- master = Minecraft 1.20.1 Forge, Java 17.
- 1.21.1-neoforge = Minecraft 1.21.1 NeoForge, Java 21.
Integration: port/1.21.1-neoforge -> 1.21.1-neoforge, NEVER -> master.

No wholesale merges between platform branches. Cross-port by isolated,
reviewable PR with the same behavioral test. Cherrypick only where APIs match.

Required across both lines: contract identity, permission, save corruption,
duplication, authoritative capture/manifest, security and server crashes.
Selective: combat balance, GUI, localization, visual content.
Platform-specific: no automatic backport without practical need.

Backport process: common issue ID -> reproduce independently -> targeted
patch or reimplementation -> both CI checks -> evidence per platform.

Release tags include loader, MC version and mod semver:
- forge/1.20.1/v1.2.2 (example, not a published build)
- neoforge/1.21.1/v1.3.0-beta.1 (example, not a published build)
Each release requires SHA256, tested dependency matrix, save migration
status, manual gameplay QA and a real server test. CI JAR != release.
