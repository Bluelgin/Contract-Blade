package com.maidweapon.port.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitDataComponent;
import com.maidweapon.port.init.PortDataTypes;
import com.maidweapon.port.item.NativeContractSwordItem;
import com.maidweapon.port.storage.*;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Native TLM data component adapter. Never accepts a snapshot supplied by a client packet. */
public final class NativeSoulSlabService {
    public static boolean filled(ItemStack s){return s.getCount()==1&&BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals("touhou_little_maid:smart_slab_has_maid")&&s.has(InitDataComponent.MAID_INFO.get());}
    public static boolean empty(ItemStack s){return s.getCount()==1&&BuiltInRegistries.ITEM.getKey(s.getItem()).toString().equals("touhou_little_maid:smart_slab_empty");}
    public static boolean blankWeapon(ItemStack s){return s.getCount()==1&&!com.maidweapon.port.projection.NativeProjection.isCopy(s)&&s.getItem() instanceof NativeContractSwordItem&&!s.has(PortDataTypes.CONTRACT_REFERENCE.get())&&!s.has(PortDataTypes.CONTRACT_CHANNELS.get())&&!ContractStackDataAccess.hasOwnedData(s);}

    public static boolean importInto(ServerPlayer player,ItemStack weapon,ItemStack slab){
        if(player==null||!player.isAlive()||player.isSpectator()||!player.getServer().isSameThread()||!blankWeapon(weapon)||!filled(slab))return false;
        try{
            var raw=slab.get(InitDataComponent.MAID_INFO.get()).copyTag();
            ContractNbtBoundsGuard.validate(raw);
            if(!raw.hasUUID("UUID")||!raw.hasUUID("Owner")||!player.getUUID().equals(raw.getUUID("Owner")))return false;
            UUID maid=raw.getUUID("UUID");var server=player.getServer();var store=NativeContractStore.get(server);
            if(slab.has(PortDataTypes.CONTRACT_REFERENCE.get()))return importTransferred(player,weapon,slab);
            var known=store.inspect(maid);
            if(!NativeTransferJournal.get(server).permits(maid))return false;
            for(var level:server.getAllLevels())if(level.getEntity(maid)!=null)return false;
            var candidate=new EntityMaid(player.serverLevel());candidate.load(raw);
            if(!candidate.isAlive()||!player.getUUID().equals(candidate.getOwnerUUID())||!maid.equals(candidate.getUUID()))return false;
            UUID binding;
            if(known==null){if(candidate.hasData(PortDataTypes.MAID_BINDING))return false;binding=UUID.randomUUID();}
            else{
                if(known.phase()!=NativeContractStore.Phase.LIVE||store.hasLiveLease(maid)||!known.owner().equals(player.getUUID())||!store.originMatches(maid,java.util.Optional.empty())||!candidate.hasData(PortDataTypes.MAID_BINDING)||!known.binding().equals(candidate.getData(PortDataTypes.MAID_BINDING)))return false;
                binding=known.binding();
            }
            UUID op=UUID.randomUUID();candidate.setData(PortDataTypes.MAID_BINDING,binding);
            var snapshot=VerifiedTlmSnapshot.captureCandidate(candidate);
            // Journal first, then consume the server-observed slab and publish a reference.
            // Any interrupted pending import is quarantined; never fabricate success/retry.
            var ref=known==null?store.beginSlabImport(maid,player.getUUID(),binding,op,snapshot):store.beginCapture(maid,player.getUUID(),binding,op,snapshot);
            NativeTransferJournal.get(server).writeIntent(ref,op,NativeTransferJournal.Kind.CAPTURE,snapshot);
            slab.shrink(1);weapon.set(PortDataTypes.CONTRACT_REFERENCE.get(),ref);store.completeCapture(ref,op);
            return true;
        }catch(Exception rejected){com.mojang.logging.LogUtils.getLogger().warn("Native soul slab import rejected; unresolved intents remain quarantined",rejected);return false;}
    }
    private static boolean importTransferred(ServerPlayer player,ItemStack weapon,ItemStack slab) throws java.io.IOException {
        var server=player.getServer();var ref=NativeChannelService.selected(server,player.getUUID(),slab);
        if(ref==null||!NativeContractStore.get(server).originMatches(ref.maid(),java.util.Optional.empty()))return false;
        var store=NativeContractStore.get(server);var snapshot=store.readStored(ref);var op=UUID.randomUUID();
        var moved=store.beginCarrierTransfer(ref,op);
        NativeTransferJournal.get(server).writeIntent(moved,op,NativeTransferJournal.Kind.CAPTURE,snapshot);
        slab.shrink(1);weapon.set(PortDataTypes.CONTRACT_REFERENCE.get(),moved);store.completeCapture(moved,op);
        return true;
    }
    public static boolean canExtract(net.minecraft.world.entity.player.Player player,ItemStack weapon,ItemStack slab){
        if(!empty(slab)||!(weapon.getItem() instanceof NativeContractSwordItem))return false;
        var c=weapon.get(PortDataTypes.CONTRACT_CHANNELS.get());
        if(c!=null&&(c.selectedIntrinsic().isPresent()||c.external().isEmpty()))return false;
        if(player.level().isClientSide)return NativeChannelService.displayReference(weapon)!=null;
        var ref=NativeChannelService.selected(player.getServer(),player.getUUID(),weapon);
        return ref!=null&&NativeContractStore.get(player.getServer()).originMatches(ref.maid(),java.util.Optional.empty());
    }
    /** Export only the selected ordinary contract; dormant intrinsic channels stay on the sword. */
    public static ItemStack extract(ServerPlayer p,ItemStack weapon,ItemStack emptySlab){
        if(!p.isAlive()||p.isSpectator()||!canExtract(p,weapon,emptySlab))return ItemStack.EMPTY;
        try{
            var server=p.getServer();var store=NativeContractStore.get(server);
            var ref=NativeChannelService.selected(server,p.getUUID(),weapon);var snapshot=store.readStored(ref);
            var result=new ItemStack(BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("touhou_little_maid:smart_slab_has_maid")));
            // The native soul slab contains only a bounded preview, never the authoritative full snapshot.
            var preview=new net.minecraft.nbt.CompoundTag();preview.putUUID("UUID",ref.maid());preview.putUUID("Owner",ref.owner());
            // TLM 1.21.1 actually serializes its model under model_id.
            for(var key:new String[]{"CustomName","model_id","MaidName","YsmModelId","YsmModelTexture","YsmModelName"}){
                if(snapshot.contains(key,net.minecraft.nbt.Tag.TAG_STRING)){
                    String value=snapshot.getString(key);
                    if(value.length()<=(key.equals("CustomName")?2048:256))preview.putString(key,value);
                }
            }
            if(snapshot.contains("IsYsmModel",net.minecraft.nbt.Tag.TAG_BYTE))preview.putBoolean("IsYsmModel",snapshot.getBoolean("IsYsmModel"));
            result.set(InitDataComponent.MAID_INFO.get(),net.minecraft.world.item.component.CustomData.of(preview));
            var op=UUID.randomUUID();var moved=store.beginCarrierTransfer(ref,op);
            NativeTransferJournal.get(server).writeIntent(moved,op,NativeTransferJournal.Kind.CAPTURE,snapshot);
            result.set(PortDataTypes.CONTRACT_REFERENCE.get(),moved);
            var channels=weapon.get(PortDataTypes.CONTRACT_CHANNELS.get());
            if(channels!=null&&!channels.intrinsic().isEmpty())weapon.set(PortDataTypes.CONTRACT_CHANNELS.get(),new ContractChannels(java.util.Optional.empty(),channels.intrinsic(),java.util.Optional.of(channels.intrinsic().getFirst().id())));
            else weapon.remove(PortDataTypes.CONTRACT_CHANNELS.get());
            weapon.remove(PortDataTypes.CONTRACT_REFERENCE.get());emptySlab.shrink(1);store.completeCapture(moved,op);
            return result;
        }catch(Exception rejected){com.mojang.logging.LogUtils.getLogger().warn("Soul slab export rejected; pending transfers remain quarantined",rejected);return ItemStack.EMPTY;}
    }
    /** A stale native TLM slab copy cannot bypass the contract ledger via vanilla item use. */
    public static void interact(PlayerInteractEvent.RightClickBlock event){
        if(!(event.getEntity() instanceof ServerPlayer p)||!(event.getItemStack().getItem() instanceof com.github.tartaricacid.touhoulittlemaid.item.AbstractStoreMaidItem)||!event.getItemStack().has(InitDataComponent.MAID_INFO.get()))return;
        // A binding table handles the item before ordinary native item use. In
        // particular, a transferred slab must open the menu instead of releasing.
        if(p.level().getBlockState(event.getPos()).is(com.maidweapon.port.content.NativeContent.INJECTOR.get()))return;
        try{
            var stack=event.getItemStack();
            if(stack.has(PortDataTypes.CONTRACT_REFERENCE.get())){
                event.setCanceled(true);event.setCancellationResult(InteractionResult.FAIL);
                var maid=PortContractLifecycleTrial.manifest(p.serverLevel(),event.getPos().relative(event.getHitVec().getDirection()),p.getUUID(),stack);
                if(maid!=null){p.setItemInHand(event.getHand(),new ItemStack(BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse("touhou_little_maid:smart_slab_empty"))));event.setCancellationResult(InteractionResult.SUCCESS);}
                return;
            }
            var raw=event.getItemStack().get(InitDataComponent.MAID_INFO.get()).copyTag();
            if(raw.hasUUID("UUID")&&NativeContractStore.get(p.getServer()).inspect(raw.getUUID("UUID"))!=null){
                event.setCanceled(true);event.setCancellationResult(InteractionResult.FAIL);
                p.displayClientMessage(Component.translatable("maid_weapon.transfer.stale_slab"),true);
            }
        }catch(RuntimeException malformed){event.setCanceled(true);event.setCancellationResult(InteractionResult.FAIL);}
    }
    /** TLM's cancellable public hook runs before its native SlabClickEvent removes the maid. */
    public static void captureInteraction(com.github.tartaricacid.touhoulittlemaid.api.event.InteractMaidEvent event){
        if(!(event.getPlayer() instanceof ServerPlayer p)||!(event.getStack().getItem() instanceof com.github.tartaricacid.touhoulittlemaid.item.AbstractStoreMaidItem))return;
        try{
            var store=NativeContractStore.get(p.getServer());var id=event.getMaid().getUUID();var view=store.inspect(id);
            if(view!=null&&(view.phase()!=NativeContractStore.Phase.LIVE||store.hasLiveLease(id)||!store.originMatches(id,java.util.Optional.empty()))){
                event.setCanceled(true);p.displayClientMessage(Component.translatable("maid_weapon.transfer.recall_first"),true);
            }
        }catch(RuntimeException malformed){event.setCanceled(true);}
    }
    private NativeSoulSlabService(){}
}
