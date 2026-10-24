# Player Contract Mastery and ability progression (phase one)

This document records the phase-one API at PR #11. The current phase-two branch
registers and executes the Black Fox route described in
[BLACK_FOX_SWORD_CORE_GROWTH.md](BLACK_FOX_SWORD_CORE_GROWTH.md); historical
statements below about an entitlement-only adapter refer to the phase-one baseline.

This branch depends on `codex/fox-native-sword-art` at `d10a309` (PR #10).
It changes the owner of growth, not Black Fox's native combat implementation.
No Black Fox milestone, level gate, cooldown, damage multiplier, duration change,
SA selection override, or phantom/B1–B7 replacement is enabled here.

## Authority and persistence

`world/data/maid_weapon_contract_mastery.dat` is overworld Forge `SavedData`.
`Players.<player UUID>` owns `SchemaVersion`, `Level`, `TotalKills`, `UnlockedTier`,
`EnderDragonKills`, `WitherKills`, and `ConfiguredUpgradeProgress` (indices 0–10).
Levels stay 1–10. Kill/boss statistics preserve the full nonnegative int range
and saturate at Integer.MAX_VALUE; configured progress stays bounded to the
existing maximum requiredKills (1,000,000). Existing `Growth.level1`–`level10` settings, legacy boss rules,
tier/entity requirements, required kill counts and damage overrides are reused.

`ContractMasteryService` is the gameplay mutation boundary. `getProgress(Player)`
returns a detached copy: changes to it never modify server progression. Mutation
calls prepare a complete NBT replacement before publishing. All mutation is on
the server thread; no client request can specify a level or progress payload.

UUID-scoped records are independent of the player entity and dimension. Death,
respawn, login, logout and dimension changes do not copy, reset or delete them.
Vanilla world save/shutdown saves them with the server; each world's data storage
is separate. Backups must include this `.dat`, not just playerdata/items.
Newer unsupported schemas are preserved read-only, with a diagnostic, rather
than being silently overwritten. Restore the matching newer mod version.

The owner receives a `PLAY_TO_CLIENT`-only snapshot on login, respawn, dimension
change, migration, admin change and credited kill. The client view is presentation
only and is attached to the current local player, avoiding cross-server caches.
No snapshot for another player is broadcast.

## Ownership of other data

`MaidData` remains contract/channel-local. Name, favorability, resonance, dormant
state, maid UUID, compressed entity/inventory data and archive metadata do not
move to the player record. The live TLM maid still owns native favorability;
capture mirrors it, and native interactions are unchanged. Player progression
never increases favorability or resonance, except the existing kill resonance
reward on the credited contract. Resonance is not a global resource.

Intrinsic/external channel switching and fox transfers preserve their exact
contract payloads. Metadata is observed before channel switching or transfer;
migration never recalls, summons, recreates, decompresses or copies a maid.
An old emergency-film archive is retained through successful recapture and then
observed, so that path cannot silently discard old growth.

## Legacy migration

There is no world/chest scan and no per-tick inventory level synchronization.
Login and menu close inspect the player's inventory once; pickup observes the
received item. Held contract use, combat, transfers and interior entry also
observe the actual carrier. The existing resonance tick checks only its selected
main/offhand carrier, not all weapons. An old boxed/distant item can be imported
later when encountered, even after the player's initial migration.

Every active/external/intrinsic/sealed channel is checked independently. A
source must have an explicit matching owner UUID, a valid maid UUID, a valid
binding UUID if present, bounded numeric growth fields, and not be superseded
or client-supplied. The maid UUID is the stable source even when an older contract later receives
its first binding ID. Ownerless/malformed legacy records are retained but not imported;
operators can repair a verified old save or explicitly set player mastery.
Fox identity/token ownership additionally uses `FoxSpiritLedger`; applicable
`ContractAuthorization` rules also remain mandatory.

Merge policy is conservative and deterministic:

* Level, total kills, unlocked tier and each boss count take componentwise MAX.
* Each configured progress index takes MAX; records are never summed.
* `LegacySources.<maid UUID>` stores the first imported snapshot and owner.
* A source is consumed once globally. Repeated discovery, copied archives,
  re-login, restart or transfer cannot reward it again. Different valid late
  sources can raise progression, never lower it.
* A receipt and merged player state are saved in the same SavedData file.
  Item NBT does not need to be cleared for migration to be idempotent.
* An admin's explicit downgrade cannot be undone by an already consumed source;
  a previously unseen legitimate higher-level source can still import later.

Old unsigned saves cannot prove that an administrator previously edited a file.
The trust boundary is existing server-held save data. Creative inventory packet
NBT is sanitized before server acceptance: unknown/changed growth snapshots are
marked `ClientSuppliedGrowth` and cannot be imported. Known exact snapshots are
harmless and their server receipts already prevent repeat rewards. Normal clients
have no serverbound mastery packet. Server operators and trusted mods can still
intentionally grant items or set levels; this is not a sandbox against server code.

`ContractCarrierData.setMaidData` now copies existing MaidData, preserves unknown
keys and any old growth archive, and writes only relationship fields plus
`ContractSchema=2`. It never writes levels/kills/progress from `MaidWeaponData`.
New contracts contain no growth fields. Old fields cannot overwrite player data.
Legacy accessors on `MaidWeaponData` are deprecated archive adapters, not authority.
Old addon code must switch to contextual APIs; editing an item's archived level
will not upgrade its player.

## Combat credit and challenge semantics

`ContractCombatCreditResolver.register(id, provider)` accepts trusted server
providers returning the actual player and actual carrier. Central validation
checks owner, supersession, projection copies and additional authorization.
Direct contract melee and real bound TLM maid melee are recognized. Native
SlashBlade projectiles are stamped at launch using their real `Projectile.owner`,
verified against native source code (Resharped 1.9.65, `6e2a0a0`): summoned swords
use `indirectMagic(projectile, shooter)`, and native area attacks preserve that
shooter. The existing native slash/judgement scoped trace preserves the projectile
even when areaAttack rewrites DamageSource to the shooter. A changed live shooter
invalidates the original launch credit. Black Fox actor credit resolves to the entitled original inventory blade;
the actor's copied item never becomes contract authority.

`ContractCombatCredits` records the hit on the actual target and the contract
identity at launch. One target death can settle once, only for the matching
last actual contract attacker/source. A foreign/noncontract last hit cannot
inherit someone else's credit. Switching held contracts cannot borrow earlier
hits; delayed native projectiles use their launch contract. Death/logout
invalidates outstanding target/launch proof. Earned level and existing stored
boss/custom counters persist, matching previous counter persistence; they are
not newly reset on death. Session proof is intentionally not saved across restart.

This removes the old player-wide bug where touching a dragon could let killing
an unrelated mob count as a dragon victory. Tier/entity/boss checks use the
actual entity that died, while configured upgrade requirements stay unchanged.

Unverified vanilla/offhand projectile launchers, indirect spell/gun sources and
other third-party entities need an explicit adapter; they are not guessed from
class names or the current main hand. Use `ContractCombatCredits.stampLaunch`
with the server-created entity and validated real carrier at actual release.
Do not submit target NBT, copied weapons or client declarations as credit.

## Attributes, UI and commands

`ContractGrowthAttributes.resolve(player, carrier)` separates authoritative
`masteryBonus` from that contract's `favorabilityMultiplier`. `additiveBonus()`
keeps the generic nonnegative formula; `dedicatedDamage(base)` keeps the existing
formula. A missing/unauthorized player context returns neutral values. There is
no fallback to an archived item level. Apply a returned bonus only once, in the
specific mod's own damage integration.

Audit finding: at PR #10 HEAD, the damage formulas had tooltip/validation callers
but no runtime damage application. This change makes the calculation contextual;
it deliberately does not add an attack interceptor or change native SA damage.
Manifested maid equipment projection does not copy the player's stats; it remains
unchanged. This preserves current real combat behavior while giving addons one
safe calculation API. The old context-free `MaidInfusion.getGenericDamageBonus`
is deprecated and neutral, because it cannot infer an authoritative player.

Tooltips read the synchronized player snapshot, explicitly label shared mastery,
and keep the selected maid's favorability/resonance separate. Context-free item
name/rarity no longer claim an archived weapon level; rarity is stable for bound
contracts. Interior space expansion uses player mastery at entry and retains
existing generated rings/buildings. Lowering mastery never removes built land.

* `/maidweapon mastery`: own level, kills, boss counters and next requirement/progress.
* `/maidweapon level`: operator own query.
* `/maidweapon level <1-10> [player]`: operator (permission 2) sets global mastery;
  usable from console with a target; no held weapon required.
* Existing favorability/resonance editing stays operator-only, with held owner checks.
* Existing `notifyUpgrade`, `notifyMonsterKilled`, and challenge-failed callbacks
  remain. Their upgrades now mean player mastery, with the same legacy weapon key.

## Generic ability API and datapacks

The framework never executes attacks. A mod registers its ability, entitlement
provider, vocabulary, parameter ranges and optional Java fallback during common
setup (`enqueueWork`). One carrier may expose multiple independent ability IDs (active spells, ranged
attacks, summons and passives). Each query selects one ID, and each provider
checks its own entitlement. No SlashBlade dependency is required:

```java
var ability = new ResourceLocation("yourmod:your_ability");
var basic = new ResourceLocation("yourmod:basic");
var ultimate = new ResourceLocation("yourmod:ultimate");
ContractAbilityRegistry.register(ability,
    (player, carrier) -> yourAbilityIdMatches(carrier) && yourEarnedEntitlement(player, carrier),
    Set.of(basic, ultimate), Map.of(),
    new ContractAbilityDefinition(ability, List.of(
        new ContractAbilityMilestone(1, Set.of(basic), Map.of()),
        new ContractAbilityMilestone(10, Set.of(ultimate), Map.of()))));

var result = ContractAbilityProgressionService.query(serverPlayer, realCarrier, ability);
if (result.status() == ContractAbilityProgressionService.Status.READY
        && result.capabilities().has(ultimate)) {
    // Your own skill executor decides what this feature does.
}
```

The provider must check the actual ability ID and independently earned entitlement;
mastery never grants a story reward. Common ownership checks cannot be replaced
by a permissive provider. Provider exceptions deny access with diagnostics.
Statuses distinguish `UNREGISTERED`, `DENIED`, `ENTITLED_WITHOUT_ROUTE`, `READY`.
An unregistered normal ability is unaffected; callers must not treat it as denied.
Result features/parameters are immutable and recalculated from current mastery.

For optional mods, register an adapter only after checking that mod is installed,
and keep its API references in the optional adapter class. The core ability package
imports only vanilla/Forge/project types, no black fox entities or third-party API.

Definitions live at `data/<namespace>/contract_ability_progression/<ability path>.json`.
File ID must match `ability`. Format:

```json
{
  "schema": 1,
  "ability": "yourmod:your_ability",
  "level_source": "player_mastery",
  "milestones": [
    {"level": 1, "features": ["yourmod:basic"]},
    {"level": 5, "features": ["yourmod:special"], "parameters": {"yourmod:radius": 8.0}},
    {"level": 10, "features": ["yourmod:ultimate"]}
  ]
}
```

Features and parameter keys must have been registered for this ability; parameters
must fall in registered `Parameter(minimum, maximum)` ranges. Namespaced IDs,
schema, level source, integral levels 1–10, duplicate milestones, finite numeric
values, unknown fields and size bounds are checked. Features accumulate; a later
milestone overrides that parameter's earlier value. JSON cannot load classes,
run scripts or invent executable abilities. Invalid files log a rejection and
use the Java fallback; no fallback means no route. Missing/removed files revert
to Java fallback. `/reload` atomically replaces routes, without caching item
levels or old resolved features. Normal datapack priority selects a file.

`tools/fixtures/mastery-datapack` is a non-shipped, noncombat example. Its ability is
registered only by the opt-in isolated validation fixture. It does not change
any production skill. `BlackFoxAbilityAdapter` currently registers entitlement only, with no
features, parameters or milestones; data cannot introduce unregistered powers.

## Phase two connection points

Register the Black Fox feature vocabulary/parameter ranges and a separately
reviewed fallback/datapack route. Its existing provider checks victory, selected
SA, original home and ledger/owner identity. Query `ContractAbilityProgressionService`
at native release; hand the read-only result to the existing executor. Keep
`ContractMasteryService` as the level source. Do not put level tests into every
phantom/actor/B combo class. In phase one the native release never calls this
query and keeps its full abilities at every level.

## Verification

`gradle build -Pci=true` includes JUnit tests for persistence/receipts, monotonic
merge, independent players, malformed sources, config rules, cap, data copies,
attributes, JSON registry and reload fallback. `tools/validate_contract_mastery.py`
checks source boundaries only; it is not a game test.

Disposable server fixture:
`gradle runServer -Pci=true -PgalleryPreview=true -PmasteryTest=true -PcompatRunDir=<empty isolated directory>`
with EULA accepted in that directory. It verifies actual Forge lethal kill
settlement, ownership, channel metadata, independent players, duplicate events,
admin updates, ability entitlement and pending challenge invalidation. The normal
server must never enable `masteryTest`. Native sword-core regression has a
separate `-PswordCoreTest=true -PcompatTest=slashblade` profile requiring the native
mod JAR. Refer to the accompanying validation report for what actually ran,
baseline failures and limits; source checks are not described as game validation.

Repeatable automation: `python tools/run_contract_mastery_tests.py --gradle gradle`
creates a fresh disposable world, verifies explicit PASS logs, then restarts that
same world and verifies persisted UUID records. Add `--native` with local
`libs/touhoulittlemaid-1.5.3-forge+mc1.20.1.jar` and
`tmp/compat-test/SlashBladeResharped-1.20.1-1.9.65.jar` to run native sword-core,
focused reward/entitlement, Boss, shrine/transfer and challenge-arena fixtures.
The full historical Boss and arena fixtures currently contain baseline failures;
see [MASTERY_VALIDATION.md](../validation/MASTERY_VALIDATION.md). `--offline` uses an already
populated Gradle cache. `-x downloadAssets` skips irrelevant client assets for
these dedicated-server tests. Inspect PASS/FAIL markers; Forge startup errors
can otherwise still leave Gradle reporting a successful JavaExec task.
