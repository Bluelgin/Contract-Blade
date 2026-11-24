package com.maidweapon.port.storage;

import com.maidweapon.port.init.PortDataTypes;
import com.maidweapon.port.init.ProbeItems;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

/** Sealed-only channel selection and shared authority resolution.
 * Never creates a contract or transfers ownership. Entity mutation belongs to the lifecycle.
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
    /** Validate dormant entries and selected live lease before recall. */
    public static ContractReference recall(MinecraftServer server,UUID owner,ItemStack stack,UUID maid) {
        try {
            if(!valid(server,owner,stack)||stack.has(PortDataTypes.CONTRACT_REFERENCE.get()))return null;
            var channels=stack.get(PortDataTypes.CONTRACT_CHANNELS.get());
            if(channels==null)return null;
            var ref=channels.selected().orElse(null);
            if(ref==null||!owner.equals(ref.owner())||!maid.equals(ref.maid())
                ||!NativeContractStore.get(server).matchesLiveLease(ref)||!NativeTransferJournal.get(server).permits(maid))return null;
            if(channels.external().isPresent()&&!channels.external().get().equals(ref)&&!sealed(server,owner,channels.external().get()))return null;
            for(var c:channels.intrinsic())if(!c.reference().equals(ref)&&!sealed(server,owner,c.reference()))return null;
            return ref;
        }catch(java.io.IOException|RuntimeException rejected){return null;}
    }
    /** Convert the existing sealed ordinary contract without touching foreign components. */
    public static boolean convertExternal(MinecraftServer server,UUID owner,ItemStack stack) {
        if(!valid(server,owner,stack)||stack.has(PortDataTypes.CONTRACT_CHANNELS.get()))return false;
        var ref=selected(server,owner,stack);
        if(ref==null)return false;
        stack.set(PortDataTypes.CONTRACT_CHANNELS.get(),new ContractChannels(Optional.of(ref),java.util.List.of(),Optional.empty()));
        stack.remove(PortDataTypes.CONTRACT_REFERENCE.get());
        return true;
    }
    public static boolean cycle(MinecraftServer server,UUID owner,ItemStack stack) {
        if(!valid(server,owner,stack))return false;
        var channels=stack.get(PortDataTypes.CONTRACT_CHANNELS.get());
        if(channels==null)return convertExternal(server,owner,stack);
        var options=new java.util.ArrayList<Optional<ResourceLocation>>();
        if(channels.external().isPresent())options.add(Optional.empty());
        for(var c:channels.intrinsic())options.add(Optional.of(c.id()));
        if(options.size()<2)return false;
        int index=options.indexOf(channels.selectedIntrinsic());
        return select(server,owner,stack,options.get((index+1)%options.size()));
    }
    /** Client-side display hint only. Never use to grant gameplay authority. */
    public static ContractReference displayReference(ItemStack stack) {
        if(stack==null || stack.isEmpty())return null;
        var single=stack.get(PortDataTypes.CONTRACT_REFERENCE.get());
        var channels=stack.get(PortDataTypes.CONTRACT_CHANNELS.get());
        return channels==null?single:single==null?channels.selected().orElse(null):null;
    }
    /** Resolve actual selected live maid on the server, with dormant channels validated. */
    public static com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid selectedLive(
        MinecraftServer server,UUID owner,ItemStack stack) {
        if(!valid(server,owner,stack)||stack.has(PortDataTypes.CONTRACT_REFERENCE.get()))return null;
        var ref=displayReference(stack);
        if(ref==null||recall(server,owner,stack,ref.maid())==null)return null;
        com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid found=null;
        for(var level:server.getAllLevels()){
            var entity=level.getEntity(ref.maid());
            if(entity==null)continue;
            if(found!=null||!(entity instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid m)
                ||m.isRemoved()||!m.isAlive()||!owner.equals(m.getOwnerUUID())||!m.hasData(PortDataTypes.MAID_BINDING)
                ||!ref.binding().equals(m.getData(PortDataTypes.MAID_BINDING)))return null;
            found=m;
        }
        return found;
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
