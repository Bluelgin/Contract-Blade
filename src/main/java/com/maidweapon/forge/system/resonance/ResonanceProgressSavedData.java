package com.maidweapon.forge.system.resonance;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.UUID;

/** Independent world authority keyed by owner UUID, never copied to carriers/entities. */
public final class ResonanceProgressSavedData extends SavedData {
    private final CompoundTag players;
    private CompoundTag future;
    public ResonanceProgressSavedData() { players = new CompoundTag(); }
    private ResonanceProgressSavedData(CompoundTag players) { this.players = players; }
    public static ResonanceProgressSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ResonanceProgressSavedData::load,
                ResonanceProgressSavedData::new, "maid_weapon_resonance_progress");
    }
    public static ResonanceProgressSavedData load(CompoundTag tag) {
        var data = new ResonanceProgressSavedData(tag.getCompound("Players").copy());
        if (tag.getInt("SchemaVersion") > 1) data.future = tag.copy();
        return data;
    }
    public boolean writable(UUID owner) {
        return future == null && players.getCompound(owner.toString()).getInt("SchemaVersion") <= 1;
    }
    public int stage(UUID owner) {
        return writable(owner) ? Math.max(0, players.getCompound(owner.toString()).getInt("Stage")) : 0;
    }
    public boolean upgrade(UUID owner, int requested, int maximum) {
        if (!writable(owner) || ResonanceRecoveryPolicy.nextStage(stage(owner), requested, maximum) != requested
                || requested != stage(owner) + 1) return false;
        var record = players.getCompound(owner.toString()).copy();
        record.putInt("SchemaVersion", 1); record.putInt("Stage", requested);
        players.put(owner.toString(), record); setDirty(); return true;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        if (future != null) return future.copy();
        tag.putInt("SchemaVersion", 1); tag.put("Players", players.copy()); return tag;
    }
}
