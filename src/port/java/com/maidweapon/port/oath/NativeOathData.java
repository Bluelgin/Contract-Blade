package com.maidweapon.port.oath;

import com.maidweapon.port.domain.oath.OathRules;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent online-tick rest, backlash and fractional upkeep; no client writes. */
public final class NativeOathData extends SavedData {
    private final CompoundTag owners;
    private final CompoundTag unsupported;
    public NativeOathData(){this(new CompoundTag(),null);}
    private NativeOathData(CompoundTag owners,CompoundTag unsupported){this.owners=owners;this.unsupported=unsupported;}
    public static NativeOathData get(MinecraftServer server){
        if(!server.isSameThread())throw new IllegalStateException("Server thread required");
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(
            NativeOathData::new,NativeOathData::load,null),"maid_weapon_port_oath");
    }
    public static NativeOathData load(CompoundTag root,HolderLookup.Provider lookup){
        if(!root.contains("schema",Tag.TAG_INT)||root.getInt("schema")!=1||!root.contains("owners",Tag.TAG_COMPOUND))
            return new NativeOathData(new CompoundTag(),root.copy());
        return new NativeOathData(root.getCompound("owners").copy(),null);
    }
    @Override public CompoundTag save(CompoundTag root,HolderLookup.Provider lookup){
        if(unsupported!=null)return unsupported.copy();
        root.putInt("schema",1);root.put("owners",owners.copy());return root;
    }
    private CompoundTag owner(UUID id){
        if(unsupported!=null)throw new IllegalStateException("Unknown oath schema");
        var key=id.toString();
        if(owners.contains(key)&&!owners.contains(key,Tag.TAG_COMPOUND))throw new IllegalStateException("Malformed oath owner");
        return owners.getCompound(key).copy();
    }
    private void put(UUID id,CompoundTag t){owners.put(id.toString(),t);setDirty();}
    public long remainder(UUID owner,UUID maid){
        return Math.floorMod(owner(owner).getCompound("fractions").getLong(maid.toString()),OathRules.SCALE);
    }
    public void remainder(UUID owner,UUID maid,long value){
        var o=owner(owner);var f=o.getCompound("fractions");
        f.putLong(maid.toString(),Math.floorMod(value,OathRules.SCALE));o.put("fractions",f);put(owner,o);
    }
    public boolean resting(UUID owner,UUID maid){return owner(owner).getCompound("rests").getInt(maid.toString())>0;}
    public int penalty(UUID owner){
        var o=owner(owner);return o.getInt("backlash_ticks")>0?Math.max(0,Math.min(5,o.getInt("backlash_levels"))):0;
    }
    public void exhaust(UUID owner,UUID maid){
        var o=owner(owner);var rests=o.getCompound("rests");rests.putInt(maid.toString(),com.maidweapon.port.config.NativeRules.backlashTicks());
        o.put("rests",rests);o.putInt("backlash_levels",Math.min(5,penalty(owner)+1));
        o.putInt("backlash_ticks",com.maidweapon.port.config.NativeRules.backlashTicks());put(owner,o);
    }
    public void tick(UUID owner){
        var o=owner(owner);var rests=o.getCompound("rests");int ticks=o.getInt("backlash_ticks");
        if(ticks<=0&&rests.isEmpty())return;
        for(String k:java.util.Set.copyOf(rests.getAllKeys())){
            int remaining=rests.getInt(k)-1;
            if(remaining<=0)rests.remove(k);else rests.putInt(k,remaining);
        }
        o.put("rests",rests);o.putInt("backlash_ticks",Math.max(0,ticks-1));
        if(ticks<=1)o.remove("backlash_levels");
        put(owner,o);
    }
    public void clearRest(UUID owner){
        var o=owner(owner);o.remove("rests");o.remove("backlash_ticks");o.remove("backlash_levels");put(owner,o);
    }
}
