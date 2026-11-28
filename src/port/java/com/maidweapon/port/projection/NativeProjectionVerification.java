package com.maidweapon.port.projection;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.maidweapon.port.content.NativeContent;
import com.maidweapon.port.init.PortDataTypes;
import com.maidweapon.port.storage.NativeChannelService;
import com.maidweapon.port.storage.NativeContractStore;
import com.maidweapon.port.tlm.PortContractLifecycleTrial;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Actual TLM equipment, vanilla clicks, NeoForge handlers and native serialization; opt-in only. */
public final class NativeProjectionVerification {
    public static EntityMaid run(ServerPlayer owner, EntityMaid maid, ItemStack source) throws Exception {
        EquipmentSlot[] slots = { EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
        var oldMaid = new java.util.EnumMap<EquipmentSlot,ItemStack>(EquipmentSlot.class);
        var oldOwner = new java.util.EnumMap<EquipmentSlot,ItemStack>(EquipmentSlot.class);
        for (var slot : slots) { oldMaid.put(slot,maid.getItemBySlot(slot).copy()); oldOwner.put(slot,owner.getItemBySlot(slot).copy()); }
        var bauble0=maid.getMaidBauble().getStackInSlot(0).copy();
        var bauble1=maid.getMaidBauble().getStackInSlot(1).copy();
        var level=owner.serverLevel();var id=maid.getUUID();
        try {
            maid.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));
            maid.setItemSlot(EquipmentSlot.FEET,new ItemStack(Items.IRON_BOOTS));
            owner.setItemSlot(EquipmentSlot.FEET,new ItemStack(Items.DIAMOND_BOOTS));
            owner.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.DIAMOND_HELMET));
            owner.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.SHIELD));
            maid.getMaidBauble().setStackInSlot(0,new ItemStack(NativeContent.TASSEL.get()));
            maid.getMaidBauble().setStackInSlot(1,new ItemStack(NativeContent.OATH_RING.get()));
            check(NativeProjectionService.mode(maid)==NativeProjection.Mode.WEAPON,"first projection accessory order");
            check(com.maidweapon.port.tlm.NativeBaubleCapabilities.live(maid)==com.maidweapon.port.tlm.NativeBaubleCapabilities.Ultimate.OATH,"independent ultimate accessory");
            check(NativeProjectionService.maintain(owner,maid,source),"weapon projection authorization");
            var copy=maid.getMainHandItem();
            check(NativeProjection.isCopy(copy)&&copy.is(source.getItem())&&!copy.has(PortDataTypes.CONTRACT_REFERENCE.get())&&!copy.has(PortDataTypes.CONTRACT_CHANNELS.get()),"phantom retained contract authority");
            check(maid.getOffhandItem().isEmpty()&&maid.getItemBySlot(EquipmentSlot.FEET).is(Items.IRON_BOOTS),"weapon-only mode copied armor or shield");
            check(maid.getHandsInvWrapper().extractItem(0,1,false).isEmpty()&&NativeProjection.isCopy(maid.getMainHandItem()),"native TLM hand handler extraction");
            check(!maid.getHandsInvWrapper().insertItem(0,new ItemStack(Items.STICK),false).isEmpty(),"native hand insertion replaced projection");
            var oldFood=maid.getMaidInv().getStackInSlot(2).copy();
            float oldHealth=maid.getHealth();boolean oldAi=maid.isNoAi(),oldGravity=maid.isNoGravity();
            try {
                maid.getMaidInv().setStackInSlot(2,new ItemStack(Items.APPLE,3));
                maid.setHealth(Math.max(1,oldHealth-10));float wounded=maid.getHealth();
                maid.setNoAi(true);maid.setNoGravity(true);
                var meal=new com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidHealSelfTask();
                check(meal.tryStart(level,maid,level.getGameTime())&&maid.isUsingItem(),"native TLM meal did not start");
                check(NativeProjectionService.maintain(owner,maid,source)&&!NativeProjectionService.clear(maid)
                        &&maid.getUseItem().is(Items.APPLE)&&maid.getUseItem().getCount()==3,"active native meal replaced, consumed or recalled by projection");
                // Isolate the native item-use continuation from unrelated brain tasks/physics.
                for(int tick=0;tick<40;tick++)maid.tick();
                int food=0;
                for(int i=0;i<maid.getMaidInv().getSlots();i++)if(maid.getMaidInv().getStackInSlot(i).is(Items.APPLE))food+=maid.getMaidInv().getStackInSlot(i).getCount();
                if(maid.getOffhandItem().is(Items.APPLE))food+=maid.getOffhandItem().getCount();
                check(!maid.isUsingItem()&&food==2&&maid.getHealth()>wounded&&NativeProjection.isCopy(maid.getMainHandItem()),"native meal continuation, healing or exact food consumption changed");
            } finally {
                maid.stopUsingItem();maid.setNoAi(oldAi);maid.setNoGravity(oldGravity);maid.setHealth(oldHealth);
                maid.getMaidInv().setStackInSlot(2,oldFood);
            }
            var handler=new ItemStackHandler(1);handler.setStackInSlot(0,copy.copy());
            check(handler.extractItem(0,1,false).isEmpty()&&handler.insertItem(0,new ItemStack(Items.STICK),false).is(Items.STICK),"generic capability extraction/insertion");
            var chest=new SimpleContainer(27);chest.setItem(0,copy.copy());var menu=ChestMenu.threeRows(96,owner.getInventory(),chest);
            check(!menu.getSlot(0).mayPickup(owner)&&!menu.getSlot(0).mayPlace(new ItemStack(Items.STICK)),"vanilla slot guards");
            menu.clicked(0,0,ClickType.QUICK_MOVE,owner);
            check(NativeProjection.isCopy(chest.getItem(0))&&menu.getCarried().isEmpty(),"actual vanilla quick-move copied projection");
            var leaked=new ItemEntity(level,maid.getX(),maid.getY(),maid.getZ(),copy.copy());
            check(!level.addFreshEntity(leaked)&&level.getEntity(leaked.getUUID())==null,"projection drop entered real world");
            check(!com.maidweapon.port.tlm.NativeSoulSlabService.blankWeapon(copy),"projection accepted as blank binding-table weapon");
            maid.getMaidBauble().setStackInSlot(0,NativeProjection.copy(new ItemStack(NativeContent.OATH_RING.get())));
            maid.getMaidBauble().setStackInSlot(1,ItemStack.EMPTY);
            check(NativeProjectionService.mode(maid)==NativeProjection.Mode.NONE&&com.maidweapon.port.tlm.NativeBaubleCapabilities.live(maid)==com.maidweapon.port.tlm.NativeBaubleCapabilities.Ultimate.NONE,"projected accessory granted permissions");
            maid.getMaidBauble().setStackInSlot(0,new ItemStack(NativeContent.TASSEL.get()));
            maid.getMaidBauble().setStackInSlot(1,new ItemStack(NativeContent.OATH_RING.get()));
            var forged=copy.copy();forged.set(PortDataTypes.CONTRACT_CHANNELS.get(),source.get(PortDataTypes.CONTRACT_CHANNELS.get()));
            check(!com.maidweapon.port.item.NativeCarriers.accepts(forged)&&NativeChannelService.selectedLive(owner.getServer(),owner.getUUID(),forged)==null,"projection used as carrier");
            maid.getMaidBauble().setStackInSlot(0,new ItemStack(NativeContent.KNOT.get()));
            check(NativeProjectionService.maintain(owner,maid,source)&&NativeProjection.isCopy(maid.getItemBySlot(EquipmentSlot.FEET))&&maid.getItemBySlot(EquipmentSlot.FEET).is(Items.DIAMOND_BOOTS),"combined armor projection");
            var saved=new CompoundTag();maid.saveWithoutId(saved);
            var beforeUnload=maid.getPersistentData().copy();
            maid.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
            check(!NativeProjectionService.clear(maid)&&maid.getPersistentData().equals(beforeUnload),"removed entity delivered or altered originals after save");
            var loaded=new EntityMaid(level);loaded.load(saved);
            check(level.addFreshEntity(loaded)&&level.getEntity(id)==loaded,"native entity reload publication");
            check(loaded.getPersistentData().contains("maid_weapon_projection"),"join restored before publication acceptance");
            var tick=new net.neoforged.neoforge.event.tick.EntityTickEvent.Pre(loaded);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(tick);
            check(!tick.isCanceled()&&loaded.getMainHandItem().is(Items.IRON_SWORD)&&loaded.getItemBySlot(EquipmentSlot.FEET).is(Items.IRON_BOOTS)&&!loaded.getPersistentData().contains("maid_weapon_projection"),"native serialization/reload lost original equipment");
            maid=loaded;
            check(NativeProjectionService.maintain(owner,maid,source),"projection after native reload");
            var unknown=new EntityMaid(level);var future=new CompoundTag();future.putInt("schema",999);future.putString("future-field","preserve");
            unknown.getPersistentData().put("maid_weapon_projection",future.copy());
            check(!NativeProjectionService.clear(unknown)&&unknown.getPersistentData().getCompound("maid_weapon_projection").equals(future),"future schema was modified or guessed");
            // A genuinely supplied replacement is a second real item, even if it has equal data.
            maid.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.DIAMOND_SWORD));
            var inventory=new java.util.ArrayList<ItemStack>();
            for(int i=0;i<maid.getMaidInv().getSlots();i++){
                inventory.add(maid.getMaidInv().getStackInSlot(i).copy());
                maid.getMaidInv().setStackInSlot(i,new ItemStack(Items.DIRT,64));
            }
            var dropsBefore=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,maid.getBoundingBox().inflate(4));
            check(NativeProjectionService.clear(maid)&&maid.getMainHandItem().is(Items.DIAMOND_SWORD),"manual replacement overwritten or full backpack blocked maintained return");
            var originals=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,maid.getBoundingBox().inflate(4)).stream()
                .filter(item->!dropsBefore.contains(item)&&item.getItem().is(Items.IRON_SWORD)).toList();
            check(originals.size()==1&&originals.getFirst().getItem().getCount()==1,"overflow original not returned exactly once");
            check(NativeProjectionService.clear(maid),"second clear failed");
            check(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,maid.getBoundingBox().inflate(4)).stream()
                .filter(item->!dropsBefore.contains(item)&&item.getItem().is(Items.IRON_SWORD)).count()==1,"overflow original duplicated");
            originals.forEach(net.minecraft.world.entity.Entity::discard);
            for(int i=0;i<inventory.size();i++)maid.getMaidInv().setStackInSlot(i,inventory.get(i));
            check(NativeProjectionService.maintain(owner,maid,source),"reproject before capture");
            var position=maid.blockPosition();
            check(PortContractLifecycleTrial.capture(level,maid,owner.getUUID(),owner.getScoreboardName(),source),"capture projected maid");
            var ref=NativeChannelService.selected(owner.getServer(),owner.getUUID(),source);
            var stored=new EntityMaid(level);stored.load(NativeContractStore.get(owner.getServer()).readStored(ref));
            check(stored.getMainHandItem().is(Items.DIAMOND_SWORD)&&!NativeProjection.isCopy(stored.getMainHandItem())&&stored.getItemBySlot(EquipmentSlot.FEET).is(Items.IRON_BOOTS)&&!stored.getPersistentData().contains("maid_weapon_projection"),"snapshot retained phantom or lost original");
            maid=PortContractLifecycleTrial.manifest(level,position,owner.getUUID(),source);
            check(maid!=null&&maid.getUUID().equals(id)&&maid.getMainHandItem().is(Items.DIAMOND_SWORD),"real native re-manifest original equipment");
        } finally {
            var current=level.getEntity(id);
            if(current instanceof EntityMaid real){
                NativeProjectionService.clear(real);
                for(var slot:slots)real.setItemSlot(slot,oldMaid.get(slot));
                real.getMaidBauble().setStackInSlot(0,bauble0);real.getMaidBauble().setStackInSlot(1,bauble1);
                for(int i=0;i<real.getMaidInv().getSlots();i++)if(real.getMaidInv().getStackInSlot(i).is(Items.IRON_SWORD))real.getMaidInv().setStackInSlot(i,ItemStack.EMPTY);
            }
            for(var slot:slots)if(slot!=EquipmentSlot.MAINHAND)owner.setItemSlot(slot,oldOwner.get(slot));
        }
        com.mojang.logging.LogUtils.getLogger().info("NATIVE_PROJECTION_PASS: real TLM hands/armor, slot order, independent ultimate, native quick-move/capability/drop guards, original equipment serialization/manual replacement and capture roundtrip");
        return maid;
    }
    private static void check(boolean ok,String detail){if(!ok)throw new IllegalStateException("Native projection fixture: "+detail);}
    private NativeProjectionVerification() { }
}
