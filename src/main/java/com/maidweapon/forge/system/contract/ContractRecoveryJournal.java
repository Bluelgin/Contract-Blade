package com.maidweapon.forge.system.contract;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Durable incident facts and single-use receipts. Never a second entity inventory or lifecycle authority. */
public final class ContractRecoveryJournal extends SavedData {
    private final CompoundTag incidents;
    private final CompoundTag transfers;
    private CompoundTag future;
    public boolean knownSchema() { return future == null; }
    private void writable() { if (future != null) throw new IllegalStateException("Unknown recovery schema retained; migration required"); }
    private ContractRecoveryJournal() { this(new CompoundTag(), new CompoundTag()); }
    private ContractRecoveryJournal(CompoundTag incidents, CompoundTag transfers) {
        this.incidents = incidents; this.transfers = transfers;
    }
    public static ContractRecoveryJournal get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ContractRecoveryJournal::load,
                ContractRecoveryJournal::new, "maid_weapon_recovery_journal");
    }
    public static ContractRecoveryJournal load(CompoundTag root) {
        var journal = new ContractRecoveryJournal(root.getCompound("Incidents").copy(), root.getCompound("Transfers").copy());
        if (root.contains("Schema") && root.getInt("Schema") != 1) journal.future = root.copy();
        return journal;
    }
    public boolean blocksAbility(String binding) {
        if (future != null) return true;
        var data=incidents.getCompound(binding);String status=data.getString("Status");
        return status.equals("COMMITTING")||status.equals("REVIEW_REQUIRED")||status.equals("PENDING")&&data.getBoolean("Confirmed");
    }
    public CompoundTag incident(String binding) { return incidents.getCompound(binding).copy(); }
    public void incident(String binding, CompoundTag value) { writable(); incidents.put(binding, value.copy()); setDirty(); }
    public CompoundTag transfer(String maid) { return transfers.getCompound(maid).copy(); }
    public void transfer(String maid, CompoundTag value) { writable(); transfers.put(maid, value.copy()); setDirty(); }
    @Override public CompoundTag save(CompoundTag root) {
        if (future != null) return future.copy();
        root.putInt("Schema", 1); root.put("Incidents", incidents.copy()); root.put("Transfers", transfers.copy()); return root;
    }
}
