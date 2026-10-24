> 本文记录 PR #13/#14 的历史机制与当时验证。当前精简分支请阅读 [契约生命周期精简](../../development/CONTRACT_LIFECYCLE_SIMPLIFICATION.md) 和 [当前验证记录](../../validation/SIMPLIFICATION_VALIDATION.md)；旧备份回执/待审查流程已不再作为操作权限。

> 历史方案与实现记录，可能包含已撤回功能、旧数值和当时的测试结果。
> 当前说明从 [文档导航](../../README.md) 进入；本页不作为现行玩法指南。

# Stage three A: contract lifecycle, transfer and incident recovery

Base: PR #12 b9a8c74d44d52a8846926703596824f677fc51bd. PR #10 and #11
remain ancestors. No independent replacement for the Core storage or mastery system.

## Transfer concepts and compatibility

| Kind | Actual registered item / payload | Accepted use |
| --- | --- | --- |
| Empty soul talisman | TLM `smart_slab_empty`, no MaidInfo/emergency/seal | Binding Table extraction |
| Filled ordinary soul talisman | TLM `smart_slab_has_maid`, MaidInfo, optional `MaidWeaponSoulTransfer` receipt | Binding Table normal binding/migration |
| Fox talisman | `MaidWeaponFoxSoulSeal`, authenticated FoxSpiritLedger token | Dedicated existing fox transfer path only; old film-backed fox seals remain compatible by payload type |
| Emergency film | TLM `film` + `MaidWeaponEmergencyFilm` | Actual confirmed durability destruction, failed normal rescue, valid snapshot only; never normal table material |
| Unmarked native/old film | TLM `film`, possibly historical MaidInfo | Native resurrection remains supported; `/maidweapon recovery migrate-film <player>` is an explicit operator-only legacy import precheck |
| Automatic backup | TLM world/data/maid_backups | Server-side incident recovery only; never table material |

Historical TlmFilmService/TouhouLittleMaidHelper method names remain binary/source
entry points, but their ordinary-transfer predicates now recognize soul slabs. Old film-backed fox seals import safely and return an empty soul slab.
MaidInfo decoding, equipment, relationship archive, original UUID and safe storage codec
are preserved. Normal migration retains the original BindingId for the interior.
New transfer receipts rotate on capture/manifest/import/export and are persisted per maid UUID;
replayed seals and stale weapons cannot regain authorization. Receipt-bearing soul
slabs are table-only, as fox seals already were. Failed validation does not consume
inputs or clear original storage. Unknown legacy bindings are not guessed.

Legacy *unbound* owned films can be converted to a slab by the operator command,
after UUID/owner/live-carrier checks; bound ambiguous films and emergency films are
retained unchanged. They are not accepted silently by the table. This deliberately
restricts automatic migration where provenance cannot be demonstrated.

## Lifecycle facts and protection

`ContractAnomalyRecoveryService.state` derives STORED from the existing codec,
MANIFESTED from exact UUID lookup, TRANSITIONING from the current synchronous Core
operation, SUSPECT_MISSING from absent loaded authority, RECOVERY_PENDING from a
confirmed matching journal incident, and UNBOUND from cleared/superseded contracts.
The journal records incident facts/receipts, not a parallel entity inventory.

Death interception distinguishes RECALLED from LIVE_PROTECTED_RECALL_FAILED.
`preventContractDeath()` is true only after successful storage/recall. TLM and Forge
death handlers separately cancel death in the failed-recall protection case. The
entity stays alive at at least 1 health; encoding failure restores the original
binding and leaves storage/live entity untouched. Failed retry interception has a
40-tick budget. Normal forceRecall still owns the 300-tick deployment cooldown.

Actual EntityLeaveLevel KILLED/DISCARDED reasons are observed separately from
cancelable death events. A healthy directly discarded maid (or an item-free killed maid) can be retained from her
current verified snapshot, including inventory, without replaying an old backup.
Native ToItem transfers and Core capture transitions are excluded. Unload, player
unload and changed dimension are not death evidence. Offline destructive removal
records Owner/Maid/Binding facts for subsequent carried-contract reconciliation;
special identity must be associated with the current ledger later.

Confirmed incidents clear stale runtime follower/recovery state. An ordinary
contract is unbound only with matching owner/maid/binding, no live/stored maid,
valid authority, no fox identity, and an explicitly absent backup. Unavailable APIs,
IO failure, malformed backups and unknown data remain pending. A known incident
with a usable but unsafe backup releases the dead follower slot while retaining its
contract. Fox identity is never removed by generic cleanup. Cleanup is idempotent
and touches only the active contract channel, keeping native blade data, unrelated
NBT, dormant channels and global mastery.

## Backup bridge and anti-duplication boundary

Actual TLM **1.5.3** bytecode was inspected: MaidBackupsManager has getMaidIndexMap,
getMaidBackupFiles and getMaidBackFile, and saves compressed entity NBT beneath the
**overworld data/maid_backups/OwnerUUID/MaidUUID** directory asynchronously.
Its file query uses the player's current dimension; its read is unbounded. Our
read-only adapter validates those API signatures and reads the verified overworld
format with bounded NbtAccounter on one daemon IO worker. It never edits upstream
files, and never converts them to films/photos. At most 16 queued reads, 128
candidate entries and the newest 3 named backup files are examined per request;
compressed size, expanded NBT budget, depth, id, UUID and Owner are checked.

A backup restore is operator-initiated, following `/maidweapon recovery preflight
<player>` and explicit `restore <player>`. The operator selects an actual player's
held carrier; no network packet accepts an arbitrary UUID or entity NBT. Previews
expire after 1200 ticks. The commit rechecks current authority, incident evidence,
UUID, binding, token/origin/ledger, current stored/live entity and talisman authority.
A snapshot missing later identity fields is linked only by the proven current
owner+MaidUUID+binding incident, never model/name. Contradicting identity fields fail.

TLM death can deposit equipment and an original film in a tombstone. Thus an old
backup with equipment, backpack contents or unknown capabilities is **blocked**:
we cannot establish that loot was consumed, and do not roll it back or delete it.
Empty equipment and the verified numeric SlashBlade state capabilities allow the bounded recovery path. Successful
restoration stores the original entity NBT/UUID in the real current carrier, then
uses the normal manifest/capture pipeline. Its name/model/progress are not rebuilt.
A backup's newer progress is not promised: progress after its timestamp may be lost.

If a directly removed entity cannot fit in a malformed carrier, its independently bounded current snapshot is retained in the incident journal for operator repair. This is the last rescue source, not an ongoing copy of live inventory.

Before publishing restored storage, a COMMITTING/spent receipt is flushed. Player
storage is then saved and completion persisted. Interrupted/spent operations never
replay; an encoder rejection explicitly proven to occur before publication releases its reservation and retains data for a controlled retry. They require operator review, even if that conservatively blocks a further
backup rescue for the same binding. This is fail-closed recovery, not an atomic
transaction across Minecraft's independent files. Current healthy removed snapshots
can still be saved without an old inventory rollback. Native ToMaid identity recovery
checks authority and prevents replay after a spent receipt; rejected special source
payloads cannot join the world as new authorized maids.

## Carrier destruction and emergency film delivery

The existing durability consumption mixins remain the destruction evidence. A
SlashBlade Broken flag, generic destruction notification, transfer or absent item
is insufficient. Existing loss journal snapshots are retained for failed rescues. A destruction snapshot missing historical binding identity is quarantined under a recovery-task key (not a fabricated binding). Rescue attempts stop after 12 tries; snapshots remain available in operator diagnostics.
A film builder requires an exact confirmed journal snapshot and cannot accept a
live maid/death event. Normal release is attempted first. Film delivery reserves a
durable single-use receipt before publishing to inventory/drop and saving the player.
If delivery is interrupted or fails, the last snapshot stays quarantined in the loss
journal and is not retried as a second film. A fox's destroyed-weapon permission is
revoked; its identity is retained on the released entity/emergency receipt. Native
emergency film recovery verifies the canonical full payload retained in the server receipt, restores the verified Forge capability API and ForgeData in addition to native fields, preserves the UUID and consumes its receipt once. Canonical payloads remain quarantined audit data if the native operation fails; spent receipts never replay.

A free fox after actual carrier destruction keeps her identity but has no original
weapon authority. Rehoming this special destruction case needs an operator/future
explicit identity-transfer extension; it does not silently mint another reward blade.

## Budgets and remaining validation

Carried-contract consistency checks run every 100 player ticks, at most 8 contracts
with a rotating cursor. Exact UUID lookups across loaded dimensions replace global
entity enumeration. Combat does not query disk backups. No added mixin, full-world
scan, per-tick entity deserialization, attribute creation or copied mastery level.

Validation results and limitations are recorded separately in STAGE_THREE_VALIDATION.md.
Interrupted delivery and nonempty inventory backups intentionally require manual
review; no unsafe automatic override command is supplied.
