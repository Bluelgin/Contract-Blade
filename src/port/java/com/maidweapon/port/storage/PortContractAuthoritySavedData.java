package com.maidweapon.port.storage;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Fail-closed server/world authority ledger, separate from ItemStack storage.
 * An interrupted transition MUST be reconciled against actual entities and
 * carriers before changing state. This ledger is not connected to gameplay yet.
 */
public final class PortContractAuthoritySavedData extends SavedData {
    public static final String WORLD_KEY = "maid_weapon_port_contract_authority";
    public static final int SCHEMA_VERSION = 1;
    private static final String RECORDS = "Records";
    private static final String OWNER = "Owner";
    private static final String BINDING = "Binding";
    private static final String STATE = "State";
    private static final String TXN = "Transaction";

    public enum Phase { LIVE, CAPTURING, STORED, MANIFESTING }

    public record View(UUID maid, UUID owner, UUID binding, Phase phase, UUID transaction) {
        public boolean unsettled() { return phase == Phase.CAPTURING || phase == Phase.MANIFESTING; }
    }

    private final CompoundTag entries;
    private final CompoundTag unsupported;

    public PortContractAuthoritySavedData() { this(new CompoundTag(), null); }

    private PortContractAuthoritySavedData(CompoundTag entries, CompoundTag unsupported) {
        this.entries = entries;
        this.unsupported = unsupported;
    }

    public static PortContractAuthoritySavedData load(CompoundTag root, HolderLookup.Provider registries) {
        if (root == null) throw new IllegalArgumentException("Missing authority data");
        int version = root.getInt("SchemaVersion");
        if (version > SCHEMA_VERSION || (!root.isEmpty() && version != SCHEMA_VERSION)) {
            return new PortContractAuthoritySavedData(new CompoundTag(), root.copy());
        }
        return new PortContractAuthoritySavedData(root.getCompound(RECORDS).copy(), null);
    }

    public static PortContractAuthoritySavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(PortContractAuthoritySavedData::new,
                PortContractAuthoritySavedData::load, null), WORLD_KEY);
    }

    public boolean writable() { return unsupported == null; }

    /** Never creates a record for an unknown/reconstructed UUID automatically. */
    public void registerVerifiedLive(UUID maid, UUID owner, UUID binding) {
        requireWritable();
        requireNonNulls(maid, owner, binding);
        if (entries.contains(maid.toString()))
            throw new IllegalStateException("Contract already registered for maid " + maid);
        CompoundTag entry = new CompoundTag();
        entry.putUUID(OWNER, owner);
        entry.putUUID(BINDING, binding);
        entry.putString(STATE, Phase.LIVE.name());
        entries.put(maid.toString(), entry);
        setDirty();
    }

    public View inspect(UUID maid) {
        requireWritable();
        Objects.requireNonNull(maid, "maid");
        CompoundTag entry = entries.getCompound(maid.toString());
        if (entry.isEmpty()) return null;
        if (!entry.hasUUID(OWNER) || !entry.hasUUID(BINDING))
            throw new IllegalStateException("Corrupt authority identity for " + maid);
        Phase phase;
        try { phase = Phase.valueOf(entry.getString(STATE)); }
        catch (IllegalArgumentException failure) {
            throw new IllegalStateException("Unknown authority phase for " + maid, failure);
        }
        UUID txn = entry.hasUUID(TXN) ? entry.getUUID(TXN) : null;
        if ((phase == Phase.CAPTURING || phase == Phase.MANIFESTING) != (txn != null))
            throw new IllegalStateException("Missing or stale authority operation marker");
        return new View(maid, entry.getUUID(OWNER), entry.getUUID(BINDING), phase, txn);
    }

    public void beginCapture(UUID maid, UUID owner, UUID binding, UUID op) {
        begin(maid, owner, binding, op, Phase.LIVE, Phase.CAPTURING);
    }

    /** Call only after the committed item snapshot and live entity removal are verified. */
    public void completeCapture(UUID maid, UUID owner, UUID binding, UUID op) {
        finish(maid, owner, binding, op, Phase.CAPTURING, Phase.STORED);
    }

    /** The caller must verify the original live entity still exists. */
    public void abortCaptureWithVerifiedLiveEntity(UUID maid, UUID owner, UUID binding, UUID op) {
        finish(maid, owner, binding, op, Phase.CAPTURING, Phase.LIVE);
    }

    public void beginManifest(UUID maid, UUID owner, UUID binding, UUID op) {
        begin(maid, owner, binding, op, Phase.STORED, Phase.MANIFESTING);
    }

    /** Call only after world publication and authoritative store-state handoff. */
    public void completeManifest(UUID maid, UUID owner, UUID binding, UUID op) {
        finish(maid, owner, binding, op, Phase.MANIFESTING, Phase.LIVE);
    }

    /** The caller must verify no entity was published and the snapshot is intact. */
    public void abortManifestWithVerifiedStoredSnapshot(UUID maid, UUID owner, UUID binding, UUID op) {
        finish(maid, owner, binding, op, Phase.MANIFESTING, Phase.STORED);
    }

    private void begin(UUID maid, UUID owner, UUID binding, UUID op,
                       Phase expected, Phase next) {
        requireNonNulls(maid, owner, binding);
        Objects.requireNonNull(op, "operation");
        View current = require(maid, owner, binding, expected);
        CompoundTag entry = entries.getCompound(maid.toString()).copy();
        entry.putString(STATE, next.name());
        entry.putUUID(TXN, op);
        entries.put(maid.toString(), entry);
        setDirty();
    }

    private void finish(UUID maid, UUID owner, UUID binding, UUID op,
                        Phase expected, Phase next) {
        requireNonNulls(maid, owner, binding);
        Objects.requireNonNull(op, "operation");
        View current = require(maid, owner, binding, expected);
        if (!op.equals(current.transaction()))
            throw new IllegalStateException("Stale transfer operation " + op);
        CompoundTag entry = entries.getCompound(maid.toString()).copy();
        entry.putString(STATE, next.name());
        entry.remove(TXN);
        entries.put(maid.toString(), entry);
        setDirty();
    }

    private View require(UUID maid, UUID owner, UUID binding, Phase expected) {
        View current = inspect(maid);
        if (current == null || !owner.equals(current.owner())
                || !binding.equals(current.binding()) || current.phase() != expected)
            throw new IllegalStateException("Contract authority mismatch or unresolved transition");
        return current;
    }

    private void requireWritable() {
        if (!writable()) throw new IllegalStateException("Future authority schema is read-only");
    }

    private static void requireNonNulls(UUID maid, UUID owner, UUID binding) {
        Objects.requireNonNull(maid, "maid");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(binding, "binding");
    }

    @Override
    public CompoundTag save(CompoundTag target, HolderLookup.Provider registries) {
        if (unsupported != null) return unsupported.copy();
        target.putInt("SchemaVersion", SCHEMA_VERSION);
        target.put(RECORDS, entries.copy());
        return target;
    }
}
