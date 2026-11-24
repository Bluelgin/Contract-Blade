package com.maidweapon.port.storage;

import com.maidweapon.port.init.PortDataTypes;
import com.maidweapon.port.init.ProbeItems;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

/** Sealed-only channel selection and shared authority resolution.
 * Never creates a contract, transfers ownership, or authorizes entity publication.
 * The single-reference lifecycle still rejects channel containers.
 */
public final class NativeChannelService {
    private static boolean valid(MinecraftServer server,UUID owner,ItemStack stack) {
        return server!=null && server.isSameThread() && owner!=null && stack!=null
            && stack.getCount()==1 && stack.is(ProbeItems.CONTRACT_CARRIER.get())
            && ContractStackDataAccess.readContract(stack).isEmpty();
    }
    private static boolean sealed(MinecraftServer server,UUID owner,ContractReference ref) throws java.io.IOException {
        if(ref==null || !owner.equals(ref.owner())) return false;
        var v=NativeContractStore.get(server).inspect(ref.maid());
        return v!=null && v.phase()==NativeContractStore.Phase.STORED && owner.equals(v.owner())
            && ref.binding().equals(v.binding()) && ref.ticket().equals(v.ticket())
            && NativeTransferJournal.get(server).permits(ref.maid());
    }
    private static boolean allSealed(MinecraftServer server,UUID owner,ContractChannels channels) throws java.io.IOException {
        if(channels.external().isPresent() && !sealed(server,owner,channels.external().get()))return false;
        for(var channel:channels.intrinsic()) if(!sealed(server,owner,channel.reference()))return false;
        return true;
    }
    public static ContractReference selected(MinecraftServer server,UUID owner,ItemStack stack) {
        try{
            if(!valid(server,owner,stack))return null;
            var single=stack.get(PortDataTypes.CONTRACT_REFERENCE.get());
            var channels=stack.get(PortDataTypes.CONTRACT_CHANNELS.get());
            if(channels==null)return sealed(server,owner,single)?single:null;
            if(single!=null || !allSealed(server,owner,channels))return null;
            return channels.selected().orElse(null);
        }catch(java.io.IOException|RuntimeException rejected){return null;}
    }
    public static boolean select(MinecraftServer server,UUID owner,ItemStack stack,Optional<ResourceLocation> id) {
        try{
            if(!valid(server,owner,stack)||stack.has(PortDataTypes.CONTRACT_REFERENCE.get()))return false;
            var old=stack.get(PortDataTypes.CONTRACT_CHANNELS.get());
            if(old==null || !allSealed(server,owner,old))return false;
            var next=old.select(id);
            if(next.selected().isEmpty())return false;
            stack.set(PortDataTypes.CONTRACT_CHANNELS.get(),next);
            return true;
        }catch(java.io.IOException|RuntimeException rejected){return false;}
    }
    private NativeChannelService(){}
}
