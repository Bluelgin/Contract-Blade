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
        if (e.contains("live_ticket") && (!e.hasUUID("live_ticket") || (phase != Phase.LIVE && phase != Phase.CAPTURING)))
            throw new IllegalStateException("Invalid live carrier lease");
        return new View(maid, e.getUUID("owner"), e.getUUID("binding"), phase, ticket, operation);
    }
    /** Only the retained selected carrier may recall this published entity. */
    public boolean hasLiveLease(UUID maid) {
        View v=inspect(maid);
        return v!=null && v.phase()==Phase.LIVE && entry(maid).hasUUID("live_ticket");
    }
    public boolean matchesLiveLease(ContractReference ref) {
        View v=inspect(ref.maid());
        return v!=null && v.phase()==Phase.LIVE && v.owner().equals(ref.owner()) && v.binding().equals(ref.binding())
            && entry(ref.maid()).hasUUID("live_ticket") && entry(ref.maid()).getUUID("live_ticket").equals(ref.ticket());
    }
    /** Intrinsic origin lives in the ledger, never inferred from item channel text. */
    public boolean originMatches(UUID maid,java.util.Optional<net.minecraft.resources.ResourceLocation> id){
        if(inspect(maid)==null)return false;
        var e=entry(maid);
        if(!e.contains("origin"))return id.isEmpty()&&!e.contains("grant");
        return e.contains("origin",Tag.TAG_STRING)&&e.hasUUID("grant")&&id.isPresent()
            &&maid.equals(intrinsicMaid(e.getUUID("grant")))
            &&id.get().toString().equals(e.getString("origin"));
    }
    /** Deterministic receipt identity prevents replay of the same server-issued grant. */
    public static UUID intrinsicMaid(UUID grant){
        return UUID.nameUUIDFromBytes(("contract-port-intrinsic:"+grant).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    public ContractReference beginIntrinsic(UUID owner,UUID binding,UUID grant,
        net.minecraft.resources.ResourceLocation origin,UUID operation,CompoundTag snapshot) throws IOException{
        Objects.requireNonNull(grant);Objects.requireNonNull(origin);
        if(origin.toString().length()>128)throw new IllegalArgumentException("Origin too long");
        UUID maid=intrinsicMaid(grant);
        if(inspect(maid)!=null)throw new IllegalStateException("Intrinsic grant already consumed");
        VerifiedTlmSnapshot.requireIdentity(snapshot,owner,maid);
        ContractNbtBoundsGuard.validate(snapshot);
        UUID ticket=UUID.randomUUID();
        var e=new CompoundTag();e.putUUID("owner",owner);e.putUUID("binding",binding);
        e.putString("origin",origin.toString());e.putUUID("grant",grant);
        e.putUUID("ticket",ticket);e.putUUID("operation",operation);e.put("snapshot",snapshot.copy());
        e.putString("phase",Phase.CAPTURING.name());put(maid,e);
        return new ContractReference(maid,owner,binding,ticket);
    }
    public void registerLive(UUID maid, UUID owner, UUID binding) {
        if (inspect(maid) != null) throw new IllegalStateException("Duplicate maid");
        CompoundTag e = new CompoundTag();
        e.putUUID("owner", Objects.requireNonNull(owner));
        e.putUUID("binding", Objects.requireNonNull(binding));
        e.putString("phase", Phase.LIVE.name());
        put(maid, e);
    }
    /** First import of a native TLM soul slab. No fictitious LIVE entity is registered. */
    public ContractReference beginSlabImport(UUID maid,UUID owner,UUID binding,UUID operation,CompoundTag snapshot) throws IOException {
        if(inspect(maid)!=null)throw new IllegalStateException("Maid already registered");
        VerifiedTlmSnapshot.requireIdentity(snapshot,owner,maid);
        ContractNbtBoundsGuard.validate(snapshot);
        var ticket=UUID.randomUUID();var e=new CompoundTag();
        e.putUUID("owner",owner);e.putUUID("binding",binding);e.putUUID("ticket",ticket);
        e.putUUID("operation",operation);e.putString("phase",Phase.CAPTURING.name());
        e.put("snapshot",snapshot.copy());put(maid,e);
        return new ContractReference(maid,owner,binding,ticket);
    }
    /** Move a sealed ordinary contract to another carrier, invalidating every old copy. */
    public ContractReference beginCarrierTransfer(ContractReference ref,UUID operation) throws IOException {
        require(ref,Phase.STORED);
        if(!originMatches(ref.maid(),java.util.Optional.empty()))throw new IllegalStateException("Intrinsic origin cannot become a native soul slab");
        var snapshot=readStored(ref);var next=entry(ref.maid()).copy();var ticket=UUID.randomUUID();
        next.putUUID("ticket",ticket);next.putUUID("operation",operation);
        next.putString("phase",Phase.CAPTURING.name());next.put("snapshot",snapshot);
        put(ref.maid(),next);return new ContractReference(ref.maid(),ref.owner(),ref.binding(),ticket);
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
    /** Narrow server-owned bauble view. A previously validated native snapshot
     * need not be copied or inflated in full for an accessory capability check. */
    public CompoundTag readStoredBaubles(ContractReference ref) {
        require(ref,Phase.STORED);
        var snapshot=entry(ref.maid()).getCompound("snapshot");
        String key=com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid.MAID_BAUBLE_INVENTORY_TAG;
        if(!snapshot.contains(key,Tag.TAG_COMPOUND))throw new IllegalStateException("Missing native baubles");
        var baubles=snapshot.getCompound(key);
        if(!baubles.contains("Size",Tag.TAG_INT)||baubles.getInt("Size")<0||baubles.getInt("Size")>64)
            throw new IllegalStateException("Malformed bauble inventory");
        return baubles.copy();
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
        completeManifest(ref, op, false);
    }
    public void completeManifest(ContractReference ref, UUID op, boolean retainCarrierLease) {
        finish(ref, op, Phase.MANIFESTING, Phase.LIVE);
        if (retainCarrierLease) {
            entry(ref.maid()).putUUID("live_ticket",ref.ticket());
            setDirty();
        }
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
        if (nextPhase == Phase.STORED) next.remove("live_ticket");
        if (nextPhase == Phase.LIVE) {
            next.remove("ticket");
            next.remove("snapshot");
        }
        put(ref.maid(), next);
    }
}
