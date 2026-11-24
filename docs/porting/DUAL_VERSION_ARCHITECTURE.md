# Dual-version maintenance contract

Decision: independent release branches (master = Forge 1.20.1 Java 17, 1.21.1-neoforge = NeoForge Java 21), one logically shared pure-Java domain tree, native platform boundaries. Do not merge entire port branches into master. No production Forge changes in this PR.

References (design inspiration, not copied source):
- JEI https://github.com/mezz/JustEnoughItems : common/platform separation and per-version releases.
- Mekanism https://github.com/mekanism/Mekanism : modular API and versioned releases.
- Modern Industrialization https://github.com/AztechMC/Modern-Industrialization : native API, generated resources and optional integrations.
- Stonecutter https://github.com/Skidamek/stonecutter : useful for close-version differences, not a reason to force the Forge/NeoForge data and lifecycle gap into conditional source.

## Ownership
src/shared/java: pure domain rules, skill definition and execution sequencing; Java 17 compatibility enforced by javac --release 17, no Minecraft/loader/TLM imports. src/sharedTest: JUnit tests. Preserve package names during initial extraction. src/port/java: NeoForge adapters, registrations, persistence, lifecycle, payload, client, integrations. Legacy src/main stays excluded from the NeoForge probe.

After port stabilizes: separate shared-only PR to master, same source tree and shared tests added to ForgeGradle source sets. Forge adapter consumes shared rules only after behavioral regression tests. Shared-only commits cherry-pick both ways; mixed platform commits must never be cherry-picked wholesale. Use a common change ID in both PRs, parity checklist and release notes. Build and test each native branch independently; do not block one release on unrelated platform fixes. A later separate core repo or subtree is optional if backport drift becomes expensive, not required today.

Data definitions/translation keys/textures can be synchronized by manifest once format compatibility is demonstrated. Recipes, tags, worldgen, shader/rendering and registry serialization remain version-specific. Keep registry IDs stable where semantics match; no blanket copying data packs. Separate protocol/storage schema versions from mod version. Use upstream native TLM APIs and capability-based compatibility adapters.

Skill engine: pure definition/cooldown/payment/effect sequencing, bounded session maps, authorization before payment, failure does not auto-refund after possible partial effect. Actual platform adapters must construct cost from definition, revalidate owner and binding, never trust client-provided amounts. Cooldowns currently runtime-only, persistent cooldown semantics need separate design. No real spirit skill enabled by this generic core.

Release gates: shared Java 17 build + shared tests, native Forge/NeoForge build, native client/server tests, per-feature parity matrix, optional dependency matrix, migration safety. Current CI only verifies NeoForge and Java17 compatibility; actual Forge integration is NOT claimed. No probe publication.

## Selective synchronization tool
`python3 tools/porting/shared_patch.py BASELINE SOURCE` checks a path-limited diff from the target worktree; `--apply` changes only shared Java sources/tests and does not commit or push. For first Forge import, copy the script from the port branch into a separate temporary location or invoke it by absolute path; its working directory must be the clean Forge worktree. Review and add ForgeGradle source sets in a separate PR. Never apply platform code or storage migrations using this tool. The target baseline must match the actual previously imported shared revision.
