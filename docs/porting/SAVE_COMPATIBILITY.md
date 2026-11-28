# Save contract, requirements only (NOT yet implemented)

Three independent versions must not be confused:
1. The old 1.20.1 ItemStack tag is no longer the primary 1.21.1 item API.
   Read DataComponents.CUSTOM_DATA only at the legacy migration boundary. New native carriers use a typed reference and world-owned snapshot; mixed/unmigrated carriers are rejected without modification. Do not delete
   unrelated SlashBlade data, components, equipment or third-party NBT.
2. The existing business contract identity uses MaidUUID, MaidBindingId,
   OwnerUUID, MaidData and world-wide mastery. Preserve these identifiers.
3. MaidEntityDataFormat v1 is gzip with CRC32 over COMPRESSED bytes and
   MaidEntityDataUncompressedSize from ContractNbtGuard.serializedSize.
   The size field is NOT the raw gzip-inflated byte count.

Never create a new maid for a failed decode. Fail read-only for unknown schema.
On manifest, validate owner, UUID and cross-dimension world-presence fact.
Confirm successful spawn before consuming the stored snapshot. On capture,
verify the full entity snapshot before removing the live maid. A missing chunk
must not count as entity death, and copied items must not duplicate UUIDs.

The present CompressedSnapshotEnvelope is ONLY a transport preflight:
it does not deserialize old NBT or restore TLM/Forge capabilities.

Golden fixtures required: ordinary contracts, dormant channels, SlashBlade
carriers with 3rd-party data, fox contracts, full TLM inventories and models,
owner and binding records, old SavedData, cancelled spawn, server restart,
data corruption, duplicate UUID and multi-dimensional unload/reload.

**No current evidence of automatic 1.20.1 world upgrade compatibility.**
Test only on duplicate/backup worlds until all gates pass.
