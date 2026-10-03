package com.maidweapon.forge.system.fox;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Token ownership, not a second copy of the maid's contract or inventory. */
public final class FoxSpiritLedger extends SavedData {
    private final CompoundTag entries;

    private FoxSpiritLedger() { this(new CompoundTag()); }
    private FoxSpiritLedger(CompoundTag entries) { this.entries = entries; }

    public static FoxSpiritLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                FoxSpiritLedger::load,
                FoxSpiritLedger::new, "maid_weapon_fox_spirit_tokens");
    }

    public static FoxSpiritLedger load(CompoundTag tag) {
        return new FoxSpiritLedger(tag.getCompound("Entries").copy());
    }

    public boolean owns(CompoundTag identity, String kind) {
        if (!FoxSpiritState.valid(identity)) return false;
        CompoundTag record = entries.getCompound(identity.getUUID("SpiritUUID").toString());
        return record.hasUUID("Token") && record.getUUID("Token").equals(identity.getUUID("Token"))
                && record.getString("Kind").equals(kind)
                && record.hasUUID("OwnerUUID")
                && record.getUUID("OwnerUUID").equals(identity.getUUID("OwnerUUID"));
    }

    public void record(CompoundTag identity, String kind) {
        CompoundTag record = new CompoundTag();
        record.putUUID("Token", identity.getUUID("Token"));
        record.putUUID("OwnerUUID", identity.getUUID("OwnerUUID"));
        record.putString("Kind", kind);
        entries.put(identity.getUUID("SpiritUUID").toString(), record);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.put("Entries", entries.copy());
        return tag;
    }
}
