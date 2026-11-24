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
    private record Entry(ContractReference ref,Ultimate ultimate){}
    private static final Map<MinecraftServer,Map<UUID,Entry>> CACHE=new WeakHashMap<>();
    private static Ultimate resolve(IItemHandler slots){
        if(slots.getSlots()>64)return Ultimate.NONE;
        for(int i=0;i<slots.getSlots();i++){
            var s=slots.getStackInSlot(i);
            if(s.is(ProbeItems.OATH_RING.get()))return Ultimate.OATH;
            if(s.is(ProbeItems.ENDLESS_CONTRACT.get()))return Ultimate.ENDLESS;
        }
        return Ultimate.NONE;
    }
    public static Ultimate live(EntityMaid maid){return maid==null?Ultimate.NONE:resolve(maid.getMaidBauble());}
    public static Ultimate stored(MinecraftServer server,ContractReference ref){
        if(server==null||ref==null||!server.isSameThread())return Ultimate.NONE;
        try{
            var store=NativeContractStore.get(server);var view=store.inspect(ref.maid());
            if(view==null||view.phase()!=NativeContractStore.Phase.STORED||!ref.owner().equals(view.owner())
                ||!ref.binding().equals(view.binding())||!ref.ticket().equals(view.ticket())
                ||!NativeTransferJournal.get(server).permits(ref.maid()))return Ultimate.NONE;
            var cache=CACHE.computeIfAbsent(server,s->new java.util.HashMap<>());
            var previous=cache.get(ref.maid());
            if(previous!=null&&previous.ref().equals(ref))return previous.ultimate();
            var tag=store.readStoredBaubles(ref);
            var slots=new ItemStackHandler(tag.getInt("Size"));
            slots.deserializeNBT(server.registryAccess(),tag);
            var value=resolve(slots);
            if(cache.size()>=4096&&!cache.containsKey(ref.maid()))cache.clear();
            cache.put(ref.maid(),new Entry(ref,value));return value;
        }catch(java.io.IOException|RuntimeException rejected){return Ultimate.NONE;}
    }
    private NativeBaubleCapabilities(){}
}
