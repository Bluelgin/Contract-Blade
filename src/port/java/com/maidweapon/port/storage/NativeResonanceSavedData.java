package com.maidweapon.port.storage;

import com.maidweapon.port.domain.resonance.ResonanceRules;
import com.maidweapon.port.domain.resonance.ResonanceRules.Payment;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Small server-owned pool records, separate from expensive entity snapshots.
 * Access through the authority-resolving service; never from client input.
 */
public final class NativeResonanceSavedData extends SavedData {
    public static final String KEY = "maid_weapon_port_resonance";
    private final CompoundTag accounts;
    private final CompoundTag unsupported;
    public NativeResonanceSavedData() { this(new CompoundTag(), null); }
    private NativeResonanceSavedData(CompoundTag accounts, CompoundTag unsupported) {
        this.accounts=accounts; this.unsupported=unsupported;
    }
    public static NativeResonanceSavedData get(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Server thread required");
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(NativeResonanceSavedData::new, NativeResonanceSavedData::load, null), KEY);
    }
    public static NativeResonanceSavedData load(CompoundTag root, HolderLookup.Provider lookup) {
        if (!root.contains("schema",Tag.TAG_INT) || root.getInt("schema")!=1 || !root.contains("accounts",Tag.TAG_COMPOUND))
            return new NativeResonanceSavedData(new CompoundTag(),root.copy());
        return new NativeResonanceSavedData(root.getCompound("accounts").copy(),null);
    }
    @Override public CompoundTag save(CompoundTag root, HolderLookup.Provider lookup) {
        if (unsupported!=null) return unsupported.copy();
        root.putInt("schema",1); root.put("accounts",accounts.copy()); return root;
    }
    private CompoundTag record(UUID maid, UUID owner, UUID binding, ResonanceRules rules) {
        if (unsupported!=null) throw new IllegalStateException("Unknown resonance schema");
        Objects.requireNonNull(maid); Objects.requireNonNull(owner); Objects.requireNonNull(binding);
        String key=maid.toString();
        if (!accounts.contains(key)) {
            CompoundTag fresh=new CompoundTag();
            fresh.putUUID("owner",owner); fresh.putUUID("binding",binding); fresh.putInt("current",rules.maximum());
            accounts.put(key,fresh); setDirty(); return fresh;
        }
        if (!accounts.contains(key,Tag.TAG_COMPOUND)) throw new IllegalStateException("Malformed account");
        CompoundTag r=accounts.getCompound(key);
        if (!r.hasUUID("owner") || !owner.equals(r.getUUID("owner"))
            || !r.hasUUID("binding") || !binding.equals(r.getUUID("binding"))
            || !r.contains("current",Tag.TAG_INT) || r.getInt("current")<0)
            throw new IllegalStateException("Invalid resonance account");
        return r;
    }
    public int current(UUID maid,UUID owner,UUID binding,ResonanceRules rules) {
        return Math.min(rules.maximum(),record(maid,owner,binding,rules).getInt("current"));
    }
    public Payment pay(UUID maid,UUID owner,UUID binding,ResonanceRules rules,int cost,boolean waived) {
        if (cost<0) return Payment.INVALID_COST;
        CompoundTag r=record(maid,owner,binding,rules);
        int available=Math.min(rules.maximum(),r.getInt("current"));
        Payment result=ResonanceRules.evaluate(true,waived,available,cost);
        if (result==Payment.PAID && cost>0) { r.putInt("current",available-cost); setDirty(); }
        return result;
    }
    public int recover(UUID maid,UUID owner,UUID binding,ResonanceRules rules,int amount) {
        if (amount<0) throw new IllegalArgumentException("Negative recovery");
        CompoundTag r=record(maid,owner,binding,rules);
        int before=Math.min(rules.maximum(),r.getInt("current"));
        int next=(int)Math.min(rules.maximum(),(long)before+amount);
        if (next>before) {r.putInt("current",next);setDirty();}
        return next;
    }
}
