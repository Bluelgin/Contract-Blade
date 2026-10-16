package com.maidweapon.forge.system.mastery;

import com.maidweapon.common.data.ContractMasteryData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.UUID;

/** One world-scoped authority, keyed by player UUID. Death never clones or deletes these records. */
public final class ContractMasterySavedData extends SavedData {
    private final CompoundTag players;
    private final CompoundTag sources;
    private CompoundTag unsupported;
    public ContractMasterySavedData() { this(new CompoundTag(), new CompoundTag()); }
    private ContractMasterySavedData(CompoundTag players, CompoundTag sources) { this.players = players; this.sources = sources; }
    public static ContractMasterySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ContractMasterySavedData::load,
                ContractMasterySavedData::new, "maid_weapon_contract_mastery");
    }
    public static ContractMasterySavedData load(CompoundTag tag) {
        var result = new ContractMasterySavedData(tag.getCompound("Players").copy(), tag.getCompound("LegacySources").copy());
        if (tag.getInt("SchemaVersion") > ContractMasteryData.SCHEMA_VERSION) result.unsupported = tag.copy();
        return result;
    }
    public boolean writable(UUID owner) {
        boolean supported = unsupported == null && players.getCompound(owner.toString()).getInt("SchemaVersion") <= ContractMasteryData.SCHEMA_VERSION;
        if (!supported) com.maidweapon.forge.compat.CompatDiagnostics.warnOnce("mastery-schema:" + owner,
                new IllegalStateException("Newer mastery schema preserved read-only; restore matching mod version"));
        return supported;
    }
    public ContractMasteryData read(UUID owner) {
        return writable(owner) ? ContractMasteryCodec.read(players.getCompound(owner.toString())) : new ContractMasteryData();
    }
    void write(UUID owner, ContractMasteryData data) {
        if (!writable(owner)) throw new IllegalStateException("Cannot overwrite newer mastery schema");
        CompoundTag replacement = ContractMasteryCodec.write(data); // Prepare before publishing.
        players.put(owner.toString(), replacement); setDirty();
    }
    public boolean hasSource(String source) { return sources.contains(source); }
    /** Snapshot and receipt are one SavedData transaction, saved together by vanilla world persistence. */
    boolean importSource(UUID owner, String source, ContractMasteryData legacy) {
        if (!writable(owner) || hasSource(source)) return false;
        var merged = read(owner); merged.mergeLegacy(legacy);
        var record = ContractMasteryCodec.write(legacy); record.putUUID("Owner", owner);
        write(owner, merged); sources.put(source, record); setDirty(); return true;
    }
    public boolean matchesSource(UUID owner, String source, ContractMasteryData legacy) {
        var record = sources.getCompound(source);
        if (!record.hasUUID("Owner") || !record.getUUID("Owner").equals(owner)) return false;
        var expected = record.copy(); expected.remove("Owner");
        return expected.equals(ContractMasteryCodec.write(legacy));
    }
    @Override public CompoundTag save(CompoundTag tag) {
        if (unsupported != null) return unsupported.copy();
        tag.putInt("SchemaVersion", ContractMasteryData.SCHEMA_VERSION);
        tag.put("Players", players.copy()); tag.put("LegacySources", sources.copy()); return tag;
    }
}
