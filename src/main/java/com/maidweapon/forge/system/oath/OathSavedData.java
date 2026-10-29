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
    public CompoundTag session(UUID owner) { return root.getCompound(owner.toString()).copy(); }
    public void put(UUID owner, CompoundTag session) { root.put(owner.toString(), session.copy()); setDirty(); }
    @Override public CompoundTag save(CompoundTag tag) { tag.merge(root); return tag; }
}
