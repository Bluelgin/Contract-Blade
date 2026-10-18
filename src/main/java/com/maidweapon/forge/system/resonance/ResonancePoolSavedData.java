package com.maidweapon.forge.system.resonance;

import com.maidweapon.common.ContractRulesConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.UUID;

/**
 * Authoritative server-owned balances. Carrier NBT is a one-time legacy import source, never a
 * write-through mirror. Each maid has one pool within her owner's account, regardless of blade.
 */
public final class ResonancePoolSavedData extends SavedData {
    public static final int SCHEMA = 1;
    private final CompoundTag players;
    private int legacyMaximum;
    private CompoundTag unsupported;

    private ResonancePoolSavedData(CompoundTag players, int legacyMaximum) {
        this.players = players;
        this.legacyMaximum = legacyMaximum;
    }
    public ResonancePoolSavedData() { this(new CompoundTag(), 0); }

    public static ResonancePoolSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ResonancePoolSavedData::load,
                ResonancePoolSavedData::new, "maid_weapon_resonance_pools");
    }
    public static ResonancePoolSavedData load(CompoundTag root) {
        var data = new ResonancePoolSavedData(root.getCompound("Players").copy(),
                root.getInt("LegacyMaximum"));
        if (root.getInt("SchemaVersion") > SCHEMA) data.unsupported = root.copy();
        return data;
    }

    private CompoundTag account(ResonanceContract context) {
        return players.getCompound(context.owner().getUUID().toString());
    }
    private CompoundTag existing(ResonanceContract context) {
        return account(context).getCompound(context.maid());
    }
    private boolean writable(ResonanceContract context) {
        return unsupported == null
                && account(context).getInt("SchemaVersion") <= SCHEMA
                && existing(context).getInt("SchemaVersion") <= SCHEMA;
    }
    private void ensureLegacyMaximum() {
        if (legacyMaximum > 0) return;
        // The pre-migration server configuration is kept under its original key.
        legacyMaximum = Math.max(1, ContractRulesConfig.MAX_RESONANCE.get());
        setDirty();
    }
    /** Full or empty legacy pools remain full or empty after conversion. */
    public static int convertLegacy(int old, int oldMaximum, int newMaximum) {
        if (oldMaximum <= 0 || newMaximum <= 0) throw new IllegalArgumentException("Invalid resonance maximum");
        long bounded = Math.max(0L, Math.min((long) old, (long) oldMaximum));
        return (int) Math.min(newMaximum, (bounded * newMaximum + oldMaximum / 2L) / oldMaximum);
    }
    private CompoundTag resolve(ResonanceContract context) {
        if (!writable(context)) return null;
        var old = existing(context);
        if (old.contains("Current", Tag.TAG_INT)) {
            // The authoritative record remains owned by the same UUID even after a valid
            // weapon transfer; the current carrier is independently verified by resolve().
            if (old.getInt("SchemaVersion") > SCHEMA) return null;
            if (!context.binding().equals(old.getString("Binding"))) {
                var replacement = old.copy();
                replacement.putString("Binding", context.binding());
                publish(context, replacement);
                return replacement;
            }
            return old;
        }
        ensureLegacyMaximum();
        var legacy = context.channel().getCompound("MaidData");
        int raw = legacy.contains("ContractResonance", Tag.TAG_INT)
                ? legacy.getInt("ContractResonance") : legacyMaximum;
        var initial = new CompoundTag();
        initial.putInt("SchemaVersion", SCHEMA);
        initial.putInt("Current", convertLegacy(raw, legacyMaximum, maximum()));
        initial.putString("Binding", context.binding());
        initial.putBoolean("Imported", true);
        publish(context, initial);
        return initial;
    }
    private void publish(ResonanceContract context, CompoundTag record) {
        var ownerKey = context.owner().getUUID().toString();
        var account = players.getCompound(ownerKey).copy();
        account.putInt("SchemaVersion", SCHEMA);
        account.put(context.maid(), record.copy());
        players.put(ownerKey, account);
        setDirty();
    }
    public static int maximum() { return ContractRulesConfig.maximumResonance(); }

    /** Returns -1 for an unsupported future schema; callers must fail closed. */
    public int rawCurrent(ResonanceContract context) {
        var record = resolve(context);
        return record == null ? -1 : Math.max(0, record.getInt("Current"));
    }
    public int current(ResonanceContract context) {
        int raw = rawCurrent(context);
        return raw < 0 ? -1 : Math.min(maximum(), raw);
    }
    public boolean set(ResonanceContract context, int amount) {
        var record = resolve(context);
        if (record == null) return false;
        int value = Math.max(0, Math.min(maximum(), amount));
        if (record.getInt("Current") == value) return true;
        var updated = record.copy();
        updated.putInt("Current", value);
        publish(context, updated);
        return true;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        if (unsupported != null) return unsupported.copy();
        tag.putInt("SchemaVersion", SCHEMA);
        tag.putInt("LegacyMaximum", legacyMaximum);
        tag.put("Players", players.copy());
        return tag;
    }
}