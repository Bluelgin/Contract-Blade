package com.maidweapon.forge.system.oath;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.UUID;

/** Only identity, fixed-point remainder and a bounded last outcome. No entity/pool duplicates. */
public final class OathSavedData extends SavedData {
    private final CompoundTag root;
    private OathSavedData(CompoundTag root) { this.root = root; }
    public static OathSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                tag -> new OathSavedData(tag.copy()), () -> new OathSavedData(new CompoundTag()), "maid_weapon_oath");
    }
    public boolean writable() { return root.getInt("SchemaVersion") <= 1; }
    public long remainder(UUID owner, String maid) {
        return Math.floorMod(root.getCompound("Fractions").getCompound(owner.toString()).getLong(maid), OathBurn.SCALE);
    }
    public void remainder(UUID owner, String maid, long value) {
        if (!writable()) return;
        var all = root.getCompound("Fractions"); var account = all.getCompound(owner.toString());
        account.putLong(maid, Math.floorMod(value, OathBurn.SCALE));
        all.put(owner.toString(), account); root.put("Fractions", all); setDirty();
    }
    public CompoundTag session(UUID owner) { return root.getCompound(owner.toString()).copy(); }
    public void put(UUID owner, CompoundTag session) { if (!writable()) return; root.putInt("SchemaVersion", 1); root.put(owner.toString(), session.copy()); setDirty(); }
    @Override public CompoundTag save(CompoundTag tag) { tag.merge(root); return tag; }
}
