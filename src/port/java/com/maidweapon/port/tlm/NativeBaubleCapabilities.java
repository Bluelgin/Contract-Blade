package com.maidweapon.port.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidweapon.port.init.ProbeItems;
import com.maidweapon.port.storage.ContractReference;
import com.maidweapon.port.storage.NativeContractStore;
import com.maidweapon.port.storage.NativeTransferJournal;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Only native equipped accessory slots, first ultimate in slot order wins.
 * Cache only immutable sealed tickets; no client-supplied accessory summaries.
 */
public final class NativeBaubleCapabilities {
    public enum Ultimate { NONE,OATH,ENDLESS }
    private record Entry(ContractReference ref,Ultimate ultimate,boolean guard){}
    private static final Map<MinecraftServer,Map<UUID,Entry>> CACHE=new WeakHashMap<>();
    private static Ultimate resolve(IItemHandler slots){
        if(slots.getSlots()>64)return Ultimate.NONE;
        for(int i=0;i<slots.getSlots();i++){
            var s=slots.getStackInSlot(i);
            if(com.maidweapon.port.projection.NativeProjection.isCopy(s))continue;
            if((s.is(ProbeItems.OATH_RING.get())||s.is(com.maidweapon.port.content.NativeContent.OATH_RING.get())))return Ultimate.OATH;
            if((s.is(ProbeItems.ENDLESS_CONTRACT.get())||s.is(com.maidweapon.port.content.NativeContent.ENDLESS.get())))return Ultimate.ENDLESS;
        }
        return Ultimate.NONE;
    }
    public static Ultimate live(EntityMaid maid){return maid==null?Ultimate.NONE:resolve(maid.getMaidBauble());}
    private static boolean guard(IItemHandler slots){
        if(slots.getSlots()>64)return false;
        for(int i=0;i<slots.getSlots();i++)if(!com.maidweapon.port.projection.NativeProjection.isCopy(slots.getStackInSlot(i))
            &&slots.getStackInSlot(i).is(com.maidweapon.port.content.NativeContent.GUARD.get()))return true;
        return false;
    }
    public static boolean liveGuard(EntityMaid maid){return maid!=null&&guard(maid.getMaidBauble());}
    public static Ultimate stored(MinecraftServer server,ContractReference ref){var entry=storedEntry(server,ref);return entry==null?Ultimate.NONE:entry.ultimate();}
    public static boolean storedGuard(MinecraftServer server,ContractReference ref){var entry=storedEntry(server,ref);return entry!=null&&entry.guard();}
    private static Entry storedEntry(MinecraftServer server,ContractReference ref){
        if(server==null||ref==null||!server.isSameThread())return null;
        try{
            var store=NativeContractStore.get(server);var view=store.inspect(ref.maid());
            if(view==null||view.phase()!=NativeContractStore.Phase.STORED||!ref.owner().equals(view.owner())
                ||!ref.binding().equals(view.binding())||!ref.ticket().equals(view.ticket())
                ||!NativeTransferJournal.get(server).permitsStored(store,ref))return null;
            var cache=CACHE.computeIfAbsent(server,s->new java.util.HashMap<>());
            var previous=cache.get(ref.maid());
            if(previous!=null&&previous.ref().equals(ref))return previous;
            var tag=store.readStoredBaubles(ref);
            var slots=new ItemStackHandler(tag.getInt("Size"));
            slots.deserializeNBT(server.registryAccess(),tag);
            var value=resolve(slots);
            if(cache.size()>=4096&&!cache.containsKey(ref.maid()))cache.clear();
            var entry=new Entry(ref,value,guard(slots));cache.put(ref.maid(),entry);return entry;
        }catch(java.io.IOException|RuntimeException rejected){return null;}
    }
    private NativeBaubleCapabilities(){}
}
