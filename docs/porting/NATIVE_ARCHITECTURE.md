# NeoForge native architecture 2.0 — experimental foundation

Do not merge into master, publish, or use in existing worlds.

## Implemented in the isolated probe
- One atomic immutable `contract_reference` component (maid/owner/binding/ticket), with persistent Codec and fixed 64-byte StreamCodec. A new ticket is issued for each capture. Identity and ticket must be checked against server authority, never trusted from a client.
- Persistent `maid_binding` entity attachment; no automatic death copy or client sync. TLM native entity serialization must round-trip it.
- NativeContractStore: overworld SavedData holds phase, transaction, ticket and full native TLM NBT snapshot in the same record. The carrier never contains the snapshot. No compression format inherited from 1.20.1. Old-format readers remain isolated to compatibility tests and rejection of unmigrated carriers.
- Owner, binding, ticket, phase and operation checks; loaded dimensions checked before publication. Pending records are blocked, not automatically reset. Once publication is attempted, even an exception/negative result leaves the snapshot pending; only pre-publication failures can return to STORED automatically.
- Staged TLM transform event carrier copy avoids side effects on real items during the probe; third-party event mutations to that copy are NOT yet merged, and require production policy and integration tests.

## Deliberate limits / release blockers
- SavedData dirty marking is not a synchronous durable transaction. Entity, player, chunk and world data files can persist in different orders; restart/crash recovery, durable journal, snapshot backup retention, unloaded cross-dimension lookup and missing carrier recovery must be implemented before production. Never auto-spawn a missing entity based solely on a loaded-chunk search.
- Current storage is a single prototype SavedData file; shard and quota snapshots for large worlds, bound aggregate storage, and measure save latency before release.
- Existing experimental probe saves are NOT migrated to the native store. No old-world automatic import; need actual ForgeCaps and nested item DataFixer fixtures, staged validation and explicit migration. Unknown or mixed legacy carriers are rejected, not erased.
- Probe component namespace is temporary. Do not register production IDs until parity review.
- No general client payload/HUD, runtime combat state, channel/spirit configuration, skill or asset port is claimed. Add typed data only where real gameplay requires it, avoid placeholder components.
- Native item references are not portable across worlds. A separately authorized export/import flow is a future decision.

## Acceptance
Compile/test CI, real TLM capture/manifest, attachment round trip, no item snapshot, duplicate & stale ticket rejection, foreign components unchanged, then restart/crash and multiplayer fixtures. The new head has NOT been verified until its own CI succeeds. Smoke test uses authenticated loopback RCON instead of relying on Gradle stdin; the previous CI failure only established that success markers were absent, not the underlying cause.

## Resonance domain increment
Pure rules and a small independent native balance store now exist, with server authorization in stored/live forms and single-use payment attempts. The disposable world test exercises balance continuity across two captures. No automatic recovery ticks, oath waiver, client payload or gameplay connection yet. See PARITY_MATRIX.md for the full feature gap.

## Durable intent increment (probe only)
Per-maid compressed intent backup is atomically replaced and fsynced before destructive item/entity publication. Prior-session or corrupt journals block transfer and resonance; even a normal restart intentionally quarantines the record pending an operator reconciliation protocol. This is not full crash recovery, does not prove unloaded UUID absence, and is not a playable restart workflow. Filesystems lacking atomic rename or directory fsync fail closed. The journal is not deleted automatically. Aggregate quota, indexed lookup, explicit reconciliation and durable completion checkpoints remain release gates. CI tests exercise actual write/read and simulated session changes.

## Typed sealed channel groundwork
ContractChannels is a bounded, immutable external/intrinsic reference collection with selected intrinsic pointer; no root NBT projection and no embedded snapshot. Same-owner, unique spirit/maid/binding and selection validation; persistent and stream codecs. This is not yet a usable multi-channel carrier: the current single-reference lifecycle and resonance explicitly reject ANY channels component (including mixed carriers). Live identity, origin authority, recall, switching, detach and transfer must be implemented before enabling it. Current-session durable journal checks use an in-memory set after successful fsync rather than decompressing snapshots on every resonance read; external modification of a journal during server execution is not supported.

## Shared core and native adapter increment
Java 17 shared source boundary, generic ability engine and server-only no-op verification skill with real resonance payment/cooldown, native read-only status request/reply payload (held carrier only, rate limited), and permission-4 read-only contractportinspect command. No client HUD/input, real spirit abilities, automatic recovery, or loaded/unloaded reconciliation is claimed. Reference NeoForge 1.21.1 networking docs: https://docs.neoforged.net/docs/1.21.1/networking/payload/ .

## Restricted live reconciliation
`contractportacklive <maid UUID>` (permission 4) reauthorizes only an observed, matching, committed LIVE contract from a prior MANIFEST intent. No entity/item writes, no spawn and no restoration from a backup; missing or mismatched loaded entity, pending/STORED phase, CAPTURE intent and corrupt records stay blocked. Acknowledgment is session-local, not a proof of global absence or full crash reconciliation. Administrator must verify the world/backup before use. Prior-session quarantine is cached to avoid repeatedly decompressing snapshots; successful explicit acknowledgment clears the cache. Normal restart of stored contracts remains blocked.

## Sealed-only channel execution
NativeChannelService validates all external/intrinsic references against the server ledger and journal, and can select a sealed channel by immutable pointer. Resonance and the verification skill now share this resolver; mixed single-reference/channel carriers remain rejected. No intrinsic creation, item transfer, live recall or multi-channel manifestation is enabled. The server smoke exercises shared balance/cooldown, wrong-owner and stale-reference rejection. This is not full intrinsic spirit parity.

## Selected channel lifecycle
The selected channel can now manifest and retain a server-validated live ticket, recall into the same channel, rotate its ticket, and switch when all channels are sealed. Empty single carriers cannot capture a leased live channel. Sneak-use in air converts a sealed single reference or cycles channels. The smoke fixture exercises two actual TLM maids with separate inventories and resonance balances. Intrinsic origin creation and addon registration are not implemented: only the test constructs a multi-channel carrier. Cross-session recovery limitations still apply.

## Client and live resonance
Client-only HUD subscribes to NeoForge 1.21.1 tick/render events. Protocol v2 reports selected ticket, stored/live phase and balance, 26 bytes when authorized; outdated or mismatched responses are hidden. Requests limited to held reference and ten-tick intervals. The held selected live channel can recover at the idle rate; combat, oath, offhand and unheld recovery are NOT implemented. Real client acceptance is required. Dedicated-server smoke now requires a separate two-channel lifecycle marker.
