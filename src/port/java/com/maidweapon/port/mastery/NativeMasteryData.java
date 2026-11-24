package com.maidweapon.port.mastery;

import com.maidweapon.port.domain.mastery.MasteryProgress;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Probe-only authority. Keeps old production data untouched until a migration is verified. */
public final class NativeMasteryData extends SavedData {
    private final CompoundTag players;
    private final CompoundTag unsupported;
    public NativeMasteryData(){this(new CompoundTag(),null);}
    private NativeMasteryData(CompoundTag players,CompoundTag unsupported){this.players=players;this.unsupported=unsupported;}
    public static NativeMasteryData get(MinecraftServer server){
        if(!server.isSameThread())throw new IllegalStateException("Server thread required");
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(
            NativeMasteryData::new,NativeMasteryData::load,null),"maid_weapon_port_mastery");
    }
    public static NativeMasteryData load(CompoundTag root,HolderLookup.Provider lookup){
        if(!root.contains("schema",Tag.TAG_INT)||root.getInt("schema")!=1||!root.contains("players",Tag.TAG_COMPOUND))
            return new NativeMasteryData(new CompoundTag(),root.copy());
        return new NativeMasteryData(root.getCompound("players").copy(),null);
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookup){
        if(unsupported!=null)return unsupported.copy();
        tag.putInt("schema",1);tag.put("players",players.copy());return tag;
    }
    public MasteryProgress read(UUID owner){
        if(unsupported!=null||owner==null)throw new IllegalStateException("Unknown mastery schema");
        var key=owner.toString();
        if(!players.contains(key))return MasteryProgress.INITIAL;
        if(!players.contains(key,Tag.TAG_COMPOUND))throw new IllegalStateException("Malformed mastery");
        var p=players.getCompound(key);
        for(String k:new String[]{"level","kills","tier","dragon","wither"})
            if(!p.contains(k,Tag.TAG_INT))throw new IllegalStateException("Malformed mastery field "+k);
        if(!p.contains("progress",Tag.TAG_INT_ARRAY))throw new IllegalStateException("Malformed mastery progress");
        return new MasteryProgress(p.getInt("level"),p.getInt("kills"),p.getInt("tier"),p.getInt("dragon"),p.getInt("wither"),
            java.util.Arrays.stream(p.getIntArray("progress")).boxed().toList());
    }
    /** Caller must settle a genuine, once-only server combat credit. */
    public MasteryProgress credit(UUID owner,int tier,String id){
        var p=read(owner).credit(tier,id,null);
        var t=new CompoundTag();t.putInt("level",p.level());t.putInt("kills",p.totalKills());t.putInt("tier",p.unlockedTier());
        t.putInt("dragon",p.dragonKills());t.putInt("wither",p.witherKills());
        t.putIntArray("progress",p.progress().stream().mapToInt(Integer::intValue).toArray());
        players.put(owner.toString(),t);setDirty();return p;
    }
}
