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
        return (!record.hasUUID("OriginUUID") || record.getUUID("OriginUUID").equals(identity.getUUID("OriginUUID")))
                && (!record.contains("SpiritId") || record.getString("SpiritId").equals(identity.getString("SpiritId")))
                && record.hasUUID("Token") && record.getUUID("Token").equals(identity.getUUID("Token"))
                && record.getString("Kind").equals(kind)
                && record.hasUUID("OwnerUUID")
                && record.getUUID("OwnerUUID").equals(identity.getUUID("OwnerUUID"));
    }

    public String maid(CompoundTag identity) {
        return FoxSpiritState.valid(identity) ? entries.getCompound(identity.getUUID("SpiritUUID").toString()).getString("MaidUUID") : "";
    }
    public void rememberMaid(CompoundTag identity, String maid) {
        if (!FoxSpiritState.valid(identity) || maid == null || maid.isBlank()) return;
        var record = entries.getCompound(identity.getUUID("SpiritUUID").toString());
        if (!record.getString("MaidUUID").isEmpty()) return;
        record.putString("MaidUUID", maid); setDirty();
    }

    public void record(CompoundTag identity, String kind) {
        CompoundTag previous = entries.getCompound(identity.getUUID("SpiritUUID").toString());
        CompoundTag record = new CompoundTag();
        if (previous.contains("MaidUUID")) record.putString("MaidUUID", previous.getString("MaidUUID"));
        record.putUUID("Token", identity.getUUID("Token"));
        record.putUUID("OwnerUUID", identity.getUUID("OwnerUUID"));
        record.putUUID("OriginUUID", identity.getUUID("OriginUUID"));
        record.putString("SpiritId", identity.getString("SpiritId"));
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
