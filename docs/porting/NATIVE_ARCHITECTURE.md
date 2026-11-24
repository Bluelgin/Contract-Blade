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
