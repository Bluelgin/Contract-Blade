package com.maidweapon.port.storage;

import com.maidweapon.port.tlm.VerifiedTlmSnapshot;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Native 1.21.1 proof of concept: snapshot and authority in the same SavedData
 * record. No entity bytes are stored in or synced with the carrier.
 * setDirty is NOT a durable commit across world/entity/player files. Pending
 * transactions fail closed; no automatic recovery or production upgrade yet.
 */
public final class NativeContractStore extends SavedData {
    public static final String WORLD_KEY = "maid_weapon_port_native_contracts";
    public static final int SCHEMA = 1;
    public enum Phase { LIVE, CAPTURING, STORED, MANIFESTING }
    public record View(UUID maid, UUID owner, UUID binding, Phase phase, UUID ticket, UUID operation) {}
    private final CompoundTag records;
    private final CompoundTag unsupported;

    public NativeContractStore() { this(new CompoundTag(), null); }
    private NativeContractStore(CompoundTag records, CompoundTag unsupported) {
        this.records = records;
        this.unsupported = unsupported;
    }
    public static NativeContractStore get(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Server thread required");
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(NativeContractStore::new, NativeContractStore::load, null), WORLD_KEY);
    }
    public static NativeContractStore load(CompoundTag root, HolderLookup.Provider lookup) {
        if (!root.contains("schema", Tag.TAG_INT) || root.getInt("schema") != SCHEMA
                || !root.contains("records", Tag.TAG_COMPOUND))
            return new NativeContractStore(new CompoundTag(), root.copy());
        return new NativeContractStore(root.getCompound("records").copy(), null);
    }
    @Override public CompoundTag save(CompoundTag target, HolderLookup.Provider lookup) {
        if (unsupported != null) return unsupported.copy();
        target.putInt("schema", SCHEMA);
        target.put("records", records.copy());
        return target;
    }
    private void writable() {
        if (unsupported != null) throw new IllegalStateException("Unsupported native contract schema");
    }
    private CompoundTag entry(UUID maid) {
        writable();
        String key = Objects.requireNonNull(maid).toString();
        if (!records.contains(key, Tag.TAG_COMPOUND)) throw new IllegalStateException("Missing or malformed contract");
        return records.getCompound(key);
    }
    public View inspect(UUID maid) {
        writable();
        if (!records.contains(maid.toString())) return null;
        CompoundTag e = entry(maid);
        if (!e.hasUUID("owner") || !e.hasUUID("binding")) throw new IllegalStateException("Corrupt identity");
        Phase phase = Phase.valueOf(e.getString("phase"));
        UUID ticket = e.hasUUID("ticket") ? e.getUUID("ticket") : null;
        UUID operation = e.hasUUID("operation") ? e.getUUID("operation") : null;
        boolean pending = phase == Phase.CAPTURING || phase == Phase.MANIFESTING;
        boolean sealed = phase != Phase.LIVE;
        if ((sealed != (ticket != null)) || (pending != (operation != null))
                || (sealed != e.contains("snapshot", Tag.TAG_COMPOUND))
                || (e.contains("ticket") && ticket == null)
                || (e.contains("operation") && operation == null)
                || (!sealed && e.contains("snapshot")))
            throw new IllegalStateException("Corrupt contract transition");
        return new View(maid, e.getUUID("owner"), e.getUUID("binding"), phase, ticket, operation);
    }
    public void registerLive(UUID maid, UUID owner, UUID binding) {
        if (inspect(maid) != null) throw new IllegalStateException("Duplicate maid");
        CompoundTag e = new CompoundTag();
        e.putUUID("owner", Objects.requireNonNull(owner));
        e.putUUID("binding", Objects.requireNonNull(binding));
        e.putString("phase", Phase.LIVE.name());
        put(maid, e);
    }
    private void put(UUID maid, CompoundTag e) {
        records.put(maid.toString(), e);
        setDirty();
    }
    private View require(UUID maid, UUID owner, UUID binding, Phase phase) {
        View v = inspect(maid);
        if (v == null || v.phase != phase || !v.owner.equals(owner) || !v.binding.equals(binding))
            throw new IllegalStateException("Contract identity or phase mismatch");
        return v;
    }
    private View require(ContractReference ref, Phase phase) {
        View v = require(ref.maid(), ref.owner(), ref.binding(), phase);
        if (!ref.ticket().equals(v.ticket)) throw new IllegalStateException("Stale carrier ticket");
        return v;
    }
    public ContractReference beginCapture(UUID maid, UUID owner, UUID binding, UUID operation,
                                          CompoundTag snapshot) throws IOException {
        require(maid, owner, binding, Phase.LIVE);
        VerifiedTlmSnapshot.requireIdentity(snapshot, owner, maid);
        ContractNbtBoundsGuard.validate(snapshot);
        UUID ticket = UUID.randomUUID();
        CompoundTag next = entry(maid).copy();
        next.put("snapshot", snapshot.copy());
        next.putUUID("ticket", ticket);
        next.putUUID("operation", Objects.requireNonNull(operation));
        next.putString("phase", Phase.CAPTURING.name());
        put(maid, next);
        return new ContractReference(maid, owner, binding, ticket);
    }
    public CompoundTag readStored(ContractReference ref) throws IOException {
        require(ref, Phase.STORED);
        CompoundTag snapshot = entry(ref.maid()).getCompound("snapshot");
        ContractNbtBoundsGuard.validate(snapshot);
        VerifiedTlmSnapshot.requireIdentity(snapshot, ref.owner(), ref.maid());
        return snapshot.copy();
    }
    public void completeCapture(ContractReference ref, UUID op) {
        finish(ref, op, Phase.CAPTURING, Phase.STORED);
    }
    public void abortCaptureWithLiveEvidence(ContractReference ref, UUID op) {
        finish(ref, op, Phase.CAPTURING, Phase.LIVE);
    }
    public void beginManifest(ContractReference ref, UUID op) {
        require(ref, Phase.STORED);
        CompoundTag next = entry(ref.maid()).copy();
        next.putString("phase", Phase.MANIFESTING.name());
        next.putUUID("operation", Objects.requireNonNull(op));
        put(ref.maid(), next);
    }
    public void completeManifest(ContractReference ref, UUID op) {
        finish(ref, op, Phase.MANIFESTING, Phase.LIVE);
    }
    public void abortManifestWithStoredEvidence(ContractReference ref, UUID op) {
        finish(ref, op, Phase.MANIFESTING, Phase.STORED);
    }
    private void finish(ContractReference ref, UUID op, Phase expected, Phase nextPhase) {
        View v = require(ref, expected);
        if (!Objects.requireNonNull(op).equals(v.operation))
            throw new IllegalStateException("Stale transaction");
        CompoundTag next = entry(ref.maid()).copy();
        next.putString("phase", nextPhase.name());
        next.remove("operation");
        if (nextPhase == Phase.LIVE) {
            next.remove("ticket");
            next.remove("snapshot");
        }
        put(ref.maid(), next);
    }
}
