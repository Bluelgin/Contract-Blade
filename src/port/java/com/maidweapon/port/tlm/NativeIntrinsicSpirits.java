package com.maidweapon.port.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidweapon.port.init.PortDataTypes;
import com.maidweapon.port.init.ProbeItems;
import com.maidweapon.port.storage.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Server-only origin registration and one-shot grant creation. No arbitrary client spirit ID.
 * Addon grants must be allocated by their own real awakening transaction and reused on retry.
 * This probe accepts only its own carrier, not production weapon registry IDs.
 */
public final class NativeIntrinsicSpirits {
    public static final ResourceLocation PROBE_ID=ResourceLocation.fromNamespaceAndPath("maid_weapon_port_probe","spirit");
    public static final ResourceLocation WHITE_TEST_ID=ResourceLocation.fromNamespaceAndPath("maid_weapon_port_probe","white_fox_test");
    public record Definition(ResourceLocation id,String displayName,BiPredicate<ServerPlayer,ItemStack> eligible,
                             Consumer<EntityMaid> initialize){
        public Definition{
            java.util.Objects.requireNonNull(id);java.util.Objects.requireNonNull(displayName);
            java.util.Objects.requireNonNull(eligible);java.util.Objects.requireNonNull(initialize);
            if(id.toString().length()>128||displayName.isBlank()||displayName.length()>128)
                throw new IllegalArgumentException("Invalid spirit definition");
        }
    }
    private static final Map<ResourceLocation,Definition> DEFINITIONS=new java.util.concurrent.ConcurrentHashMap<>();
    public static void register(Definition definition){
        if(DEFINITIONS.putIfAbsent(definition.id(),definition)!=null)throw new IllegalStateException("Duplicate spirit id");
    }
    public static void registerProbe(){
        register(new Definition(PROBE_ID,"Contract Spirit",
            (p,carrier)->p.getMainHandItem().is(ProbeItems.SPIRIT_CRYSTAL.get())||p.getOffhandItem().is(ProbeItems.SPIRIT_CRYSTAL.get()),
            maid->{}));
        register(new Definition(WHITE_TEST_ID,"White Fox (DEV)",
            (p,carrier)->p.getMainHandItem().is(ProbeItems.WHITE_FOX_CRYSTAL.get())||p.getOffhandItem().is(ProbeItems.WHITE_FOX_CRYSTAL.get()),maid->{}));
    }
    public static boolean createForGrant(ServerPlayer p,ItemStack carrier,ResourceLocation id,UUID grant){
        if(p==null||p.getServer()==null||!p.getServer().isSameThread()||!p.isAlive()||p.isSpectator()
            ||(p.getMainHandItem()!=carrier&&p.getOffhandItem()!=carrier))return false;
        var definition=DEFINITIONS.get(id);
        return definition!=null&&definition.eligible().test(p,carrier)&&create(p.serverLevel(),p.getUUID(),carrier,definition,grant);
    }
    /** Package-private for the native server fixture. Never publishes the candidate. */
    static boolean create(ServerLevel level,UUID owner,ItemStack carrier,Definition definition,UUID grant){
        if(level==null||owner==null||grant==null||carrier==null||!level.getServer().isSameThread()
            ||carrier.getCount()!=1||!carrier.is(ProbeItems.CONTRACT_CARRIER.get())||!ContractStackDataAccess.readContract(carrier).isEmpty())return false;
        try{
            var server=level.getServer();
            var single=carrier.get(PortDataTypes.CONTRACT_REFERENCE.get());
            var old=carrier.get(PortDataTypes.CONTRACT_CHANNELS.get());
            if(single!=null&&old!=null)return false;
            if((single!=null||old!=null)&&NativeChannelService.selected(server,owner,carrier)==null)return false;
            if(old==null)old=new ContractChannels(Optional.ofNullable(single),List.of(),Optional.empty());
            if(old.intrinsic().size()>=ContractChannels.MAX_INTRINSIC
                ||old.intrinsic().stream().anyMatch(c->c.id().equals(definition.id())))return false;
            var store=NativeContractStore.get(server);
            UUID maidId=NativeContractStore.intrinsicMaid(grant);
            if(store.inspect(maidId)!=null)return false;
            for(var world:server.getAllLevels())if(world.getEntity(maidId)!=null)return false;
            UUID binding=UUID.randomUUID(),op=UUID.randomUUID();
            var maid=new EntityMaid(level);maid.setUUID(maidId);maid.setOwnerUUID(owner);
            maid.setCustomName(Component.literal(definition.displayName()));
            maid.setData(PortDataTypes.MAID_BINDING,binding);
            definition.initialize().accept(maid);
            if(maid.isRemoved()||!maidId.equals(maid.getUUID())||!owner.equals(maid.getOwnerUUID())
                ||!binding.equals(maid.getData(PortDataTypes.MAID_BINDING))||level.getEntity(maidId)!=null)return false;
            var snapshot=TlmNativeTransformBridge.captureSnapshot(maid,carrier.copy());
            var ref=store.beginIntrinsic(owner,binding,grant,definition.id(),op,snapshot);
            NativeTransferJournal.get(server).writeIntent(ref,op,NativeTransferJournal.Kind.CAPTURE,snapshot);
            var entries=new ArrayList<>(old.intrinsic());entries.add(new ContractChannels.Intrinsic(definition.id(),ref));
            var selection=old.selected().isEmpty()?Optional.of(definition.id()):old.selectedIntrinsic();
            var next=new ContractChannels(old.external(),entries,selection);
            store.completeCapture(ref,op);
            carrier.set(PortDataTypes.CONTRACT_CHANNELS.get(),next);
            carrier.remove(PortDataTypes.CONTRACT_REFERENCE.get());
            return true;
        }catch(Exception failure){
            com.mojang.logging.LogUtils.getLogger().warn("Native intrinsic creation blocked; pending evidence retained",failure);return false;
        }
    }
    private NativeIntrinsicSpirits(){}
}
