package com.maidweapon.port.storage;

import java.util.UUID;
import com.maidweapon.port.domain.resonance.SpiritVeinRules;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Server-owned player progression; upgrade item/cost integration is deliberately absent. */
public final class NativeSpiritVeinData extends SavedData {
    public static final String KEY="maid_weapon_port_spirit_veins";
    private final CompoundTag players;
    private final CompoundTag unsupported;
    public NativeSpiritVeinData(){this(new CompoundTag(),null);}
    private NativeSpiritVeinData(CompoundTag players,CompoundTag unsupported){this.players=players;this.unsupported=unsupported;}
    public static NativeSpiritVeinData get(MinecraftServer server){
        if(!server.isSameThread())throw new IllegalStateException("Server thread required");
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(NativeSpiritVeinData::new,NativeSpiritVeinData::load,null),KEY);
    }
    public static NativeSpiritVeinData load(CompoundTag root,HolderLookup.Provider lookup){
        if(!root.contains("schema",Tag.TAG_INT)||root.getInt("schema")!=1||!root.contains("players",Tag.TAG_COMPOUND))
            return new NativeSpiritVeinData(new CompoundTag(),root.copy());
        return new NativeSpiritVeinData(root.getCompound("players").copy(),null);
    }
    @Override public CompoundTag save(CompoundTag root,HolderLookup.Provider lookup){
        if(unsupported!=null)return unsupported.copy();
        root.putInt("schema",1);root.put("players",players.copy());return root;
    }
    public int stage(UUID owner){
        if(unsupported!=null||owner==null)throw new IllegalStateException("Unknown progression schema or owner");
        String key=owner.toString();
        if(!players.contains(key))return 0;
        if(!players.contains(key,Tag.TAG_INT))throw new IllegalStateException("Malformed progression");
        int stage=players.getInt(key);SpiritVeinRules.multiplier(stage);return stage;
    }
    /** Trusted service entry point; future upgrade item must pay its own cost before calling. */
    public SpiritVeinRules.Upgrade upgrade(UUID owner,int requested){
        var result=SpiritVeinRules.evaluate(stage(owner),requested);
        if(result==SpiritVeinRules.Upgrade.SUCCESS){players.putInt(owner.toString(),requested);setDirty();}
        return result;
    }
}
