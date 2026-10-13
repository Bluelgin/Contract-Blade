package com.maidweapon.forge.system.contract;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Current maid location/binding plus read-compatible legacy recovery archives. No incident authorizes a spawn. */
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
    public void clearIncident(String binding) {
        if (future == null && binding != null && incidents.contains(binding)) { incidents.remove(binding); setDirty(); }
    }
    /** UNKNOWN is legacy/unobserved data, not evidence that a maid exists or has died. */
    public enum Presence { UNKNOWN, PRESENT, ABSENT }
    public Presence presence(String maid) {
        var record = transfer(maid);
        return !record.contains("WorldPresent") ? Presence.UNKNOWN
                : record.getBoolean("WorldPresent") ? Presence.PRESENT : Presence.ABSENT;
    }
    public boolean isMaidPresent(String maid) { return presence(maid) == Presence.PRESENT; }
    public void markMaidPresent(String maid) { worldPresent(maid, true); }
    public void markMaidAbsent(String maid) { worldPresent(maid, false); }

    /** Write current destination without inventing presence for unobserved legacy entities. */
    public void destination(String maid, java.util.UUID owner, String binding, String kind,
                            Presence presence, CompoundTag fox) {
        if (!knownSchema() || maid == null || maid.isBlank()) return;
        var record = new CompoundTag();
        record.putUUID("Owner", owner);
        record.putString("Binding", binding == null ? "" : binding);
        record.putString("Kind", kind);
        if (presence != Presence.UNKNOWN) record.putBoolean("WorldPresent", presence == Presence.PRESENT);
        if (fox != null && !fox.isEmpty()) record.put("Fox", fox.copy());
        transfer(maid, record);
    }

    public void worldPresent(String maid, boolean present) {
        if (future != null || maid == null || maid.isBlank()) return;
        var record = transfer(maid);
        if (record.contains("WorldPresent") && record.getBoolean("WorldPresent") == present) return;
        record.putBoolean("WorldPresent", present);
        transfer(maid, record);
    }
    public CompoundTag incident(String binding) { return binding == null ? new CompoundTag() : incidents.getCompound(binding).copy(); }
    public void incident(String binding, CompoundTag value) { writable(); incidents.put(binding, value.copy()); setDirty(); }
    public CompoundTag transfer(String maid) { return maid == null ? new CompoundTag() : transfers.getCompound(maid).copy(); }
    public void transfer(String maid, CompoundTag value) { writable(); transfers.put(maid, value.copy()); setDirty(); }
    @Override public CompoundTag save(CompoundTag root) {
        if (future != null) return future.copy();
        root.putInt("Schema", 1); root.put("Incidents", incidents.copy()); root.put("Transfers", transfers.copy()); return root;
    }
}
