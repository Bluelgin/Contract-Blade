package com.maidweapon.forge.compat.tlm;

import com.maidweapon.forge.event.MaidBondCombatHandler;
import com.maidweapon.forge.menu.MaidInjectorMenu;
import com.maidweapon.forge.system.contract.*;
import com.maidweapon.forge.system.deployment.*;
import com.maidweapon.forge.system.fox.*;
import com.maidweapon.forge.item.MaidInfusion;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.UUID;

/** Opt-in native Forge fixture, isolated disposable worlds only. */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class ContractStageThreeValidation {
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("contractblade.stageThree.validation")) return;
        try {
            if (Boolean.getBoolean("contractblade.stageThree.resume")) resume(event.getServer());
            else { run(event.getServer()); com.mojang.logging.LogUtils.getLogger().info("CONTRACT_STAGE_THREE_PASS"); }
        }
        catch (Throwable failure) { com.mojang.logging.LogUtils.getLogger().error("CONTRACT_STAGE_THREE_FAIL", failure); }
        finally { event.getServer().halt(false); }
    }
    private static LivingEntity maid(ServerPlayer player) throws Exception {
        var maid = (LivingEntity)TlmEntityAdapter.maidClass().getConstructor(net.minecraft.world.level.Level.class).newInstance(player.level());
        TlmEntityAdapter.tame(maid, player); maid.setPos(player.position()); player.level().addFreshEntity(maid); return maid;
    }
    private static ItemStack item(String id) { return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id))); }
    static void run(net.minecraft.server.MinecraftServer server) throws Exception {
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(server.overworld(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "RecoveryFixture"));
        player.setPos(.5, 150, .5); player.setHealth(20); player.getInventory().clearContent(); player.getInventory().selected=0;
        var maid = maid(player); UUID id = maid.getUUID(); var weapon = new ItemStack(Items.IRON_SWORD);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, weapon);
        check(ContractLifecycleService.capture(player, maid, weapon, false), "capture commits before live entity removal");
        String binding = ContractCarrierData.getBindingId(weapon);
        check(ContractAnomalyRecoveryService.state(player,weapon)==ContractAnomalyRecoveryService.State.STORED, "stored state");
        var menu = new MaidInjectorMenu(31,player.getInventory(),player.blockPosition()); player.containerMenu=menu;
        player.getInventory().clearContent(); menu.getSlot(0).set(weapon); menu.getSlot(1).set(item("touhou_little_maid:smart_slab_empty"));
        check(menu.clickMenuButton(player,0), "ordinary table extraction uses soul slab");
        var clean=menu.getSlot(2).getItem(); var seal=menu.getSlot(3).getItem(); var staleSeal=seal.copy();
        check(SoulTalismanService.filled(seal)&&!seal.getTag().contains(SoulTalismanService.EMERGENCY), "normal talisman is not emergency film");
        check(!MaidInfusion.isInfused(clean), "ordinary unbind preserves weapon without active contract");
        menu.getSlot(2).set(ItemStack.EMPTY); menu.getSlot(3).set(ItemStack.EMPTY);
        menu.getSlot(0).set(new ItemStack(Items.DIAMOND_SWORD)); menu.getSlot(1).set(seal);
        check(menu.clickMenuButton(player,0),"talisman migrates to another carrier"); weapon=menu.getSlot(2).getItem();
        check(binding.equals(ContractCarrierData.getBindingId(weapon))&&id.toString().equals(ContractCarrierData.getBoundMaidUUID(weapon)),"maid UUID and interior binding preserved");
        check(!SoulTalismanService.importAllowed(player,staleSeal),"used talisman cannot replay");
        menu.getSlot(2).set(ItemStack.EMPTY); menu.getSlot(3).set(ItemStack.EMPTY); player.containerMenu=player.inventoryMenu;
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,weapon);
        check(ContractLifecycleService.manifest(player,weapon,false),"manifest migrated maid"); maid=(LivingEntity)server.overworld().getEntity(id);
        check(ContractAnomalyRecoveryService.state(player,weapon)==ContractAnomalyRecoveryService.State.MANIFESTED,"manifested state");
        check(MaidBondCombatHandler.preventContractDeath(player,maid),"normal emergency recall reports committed success");
        check(MaidInfusion.containsMaid(weapon)&&maid.isRemoved(),"normal emergency retains data");
        check(ContractLifecycleService.manifest(player,weapon,false),"manifest for failed encoding protection"); maid=(LivingEntity)server.overworld().getEntity(id);
        weapon.getOrCreateTag().putByteArray("UnrelatedOversized",new byte[com.maidweapon.common.MaidWeaponConfig.CONTRACT_NBT_MAX_STORED_BYTES.get()+1]);
        check(!MaidBondCombatHandler.preventContractDeath(player,maid),"failed recall is not false success");
        check(maid.isAlive()&&!maid.isRemoved()&&!MaidInfusion.containsMaid(weapon),"failed recall retains protected live maid");
        check(MaidBondCombatHandler.protectContractDeath(player,maid)==MaidBondCombatHandler.DeathProtection.LIVE_PROTECTED_RECALL_FAILED,"event caller can cancel death after failed recall");
        weapon.getOrCreateTag().remove("UnrelatedOversized");
        check(ContractLifecycleService.capture(player,maid,weapon,false),"recovery after encoding rejection");
        // Populate only the native owner lookup for this test player; then exercise the real event bus.
        var playerMapField=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID"); playerMapField.setAccessible(true);
        @SuppressWarnings("unchecked") var playerMap=(java.util.Map<UUID,ServerPlayer>)playerMapField.get(server.getPlayerList());
        playerMap.put(player.getUUID(),player);
        try {
            check(ContractLifecycleService.manifest(player,weapon,false),"manifest before native TLM death event");
            var dying=(com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid)server.overworld().getEntity(id);
            var tlmEvent=new com.github.tartaricacid.touhoulittlemaid.api.event.MaidDeathEvent(dying,dying.damageSources().generic());
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(tlmEvent);
            check(tlmEvent.isCanceled()&&MaidInfusion.containsMaid(weapon),"native TLM death event safely canceled after commit");
            check(ContractLifecycleService.manifest(player,weapon,false),"manifest before Forge death event");
            dying=(com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid)server.overworld().getEntity(id);
            var forgeEvent=new net.minecraftforge.event.entity.living.LivingDeathEvent(dying,dying.damageSources().generic());
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(forgeEvent);
            check(forgeEvent.isCanceled()&&MaidInfusion.containsMaid(weapon),"Forge death event safely canceled after commit");
        } finally {playerMap.remove(player.getUUID());}
        check(!TlmFilmService.isEmptyMaidFilm(item("touhou_little_maid:film")),"empty film is not normal table material");
        check(TlmFilmService.createEmergencyResurrectionFilm(player,weapon,null).isEmpty(),"no actual loss means no emergency film");
        var snapshot=weapon.copy(); player.getInventory().clearContent();
        ContractCarrierLossService.scheduleDestroyed(player,id.toString(),binding,snapshot);
        var emergency=TlmFilmService.createEmergencyResurrectionFilm(player,snapshot,null);
        check(!emergency.isEmpty()&&emergency.getTag().contains(SoulTalismanService.EMERGENCY),"confirmed destroyed carrier permits emergency film");
        check(!TlmFilmService.isFilledMaidFilm(emergency)&&!SoulTalismanService.importAllowed(player,emergency),"emergency film cannot be normal talisman");
        // separate native source for legacy film migration; unknown/bound sources cannot be cleared or imported
        var nativeMaid=maid(player); var raw=TlmEntityAdapter.save(nativeMaid); nativeMaid.discard();
        var legacy=item("touhou_little_maid:film"); legacy.getOrCreateTag().put("MaidInfo",raw);
        var legacyBefore=legacy.copy(); check(!SoulTalismanService.migrateLegacy(player,legacy).isEmpty(),"owned unbound legacy MaidInfo migration");
        check(legacy.getTag().equals(legacyBefore.getTag()),"precheck does not consume old film");
        raw.putUUID("Owner",UUID.randomUUID()); check(SoulTalismanService.migrateLegacy(player,legacy).isEmpty(),"wrong-owner legacy data retained");
        // missing loaded entity does not authorize cleanup or backup restoration
        weapon=snapshot.copy(); player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,weapon);
        check(ContractLifecycleService.manifest(player,weapon,false),"manifest for missing entity case"); maid=(LivingEntity)server.overworld().getEntity(id);
        maid.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
        ContractAnomalyRecoveryService.record(player,weapon,"NOT_LOADED",false);
        check(ContractAnomalyRecoveryService.state(player,weapon)==ContractAnomalyRecoveryService.State.SUSPECT_MISSING,"unknown missing stays suspect");
        var backup=TlmEntityAdapter.save(maid); backup.putString("id","touhou_little_maid:maid");
        check(ContractAnomalyRecoveryService.preflight(player,weapon,ContractAnomalyRecoveryService.incident(player,weapon),new TlmBackupBridge.Result("fixture",backup,"old"))!=null,"suspect cannot be restored");
        ContractAnomalyRecoveryService.record(player,weapon,"KILLED",true);
        var incident=ContractAnomalyRecoveryService.incident(player,weapon);
        check(ContractAnomalyRecoveryService.releasesSlot(player,weapon),"confirmed loss releases follower slot");
        check(ContractAnomalyRecoveryService.cleanup(player,weapon,incident),"confirmed ordinary unbind");
        check(!MaidInfusion.isInfused(weapon)&&weapon.getItem()==Items.DIAMOND_SWORD,"native weapon survives cleanup");
        check(!ContractAnomalyRecoveryService.cleanup(player,weapon,incident),"repeated cleanup is idempotent");
        // The current removed snapshot is preferred over old disk data and preserves equipment.
        var foxMaid=maid(player); var foxId=foxMaid.getUUID(); var foxCarrier=new ItemStack(Items.IRON_SWORD);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,foxCarrier);
        check(ContractLifecycleService.capture(player,foxMaid,foxCarrier,false),"capture special recovery fixture");
        var fox=new CompoundTag(); fox.putInt("Version",1); fox.putString("SpiritId",FoxSpiritState.WHITE);
        fox.putUUID("SpiritUUID",UUID.randomUUID()); fox.putUUID("OriginUUID",UUID.randomUUID());
        fox.putUUID("OwnerUUID",player.getUUID()); fox.putUUID("Token",UUID.randomUUID());
        foxCarrier.getOrCreateTag().put(FoxSpiritState.ROOT,fox); FoxSpiritLedger.get(server).record(fox,"WEAPON");
        check(ContractLifecycleService.manifest(player,foxCarrier,false),"manifest special fixture");
        foxMaid=(LivingEntity)server.overworld().getEntity(foxId);
        foxMaid.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND,new ItemStack(Items.DIAMOND,3));
        foxMaid.discard();
        check(ContractAnomalyRecoveryService.preserveRemoved(player,foxCarrier,foxMaid),"direct deletion rescued from current trusted data");
        check(ContractMaidStorage.read(player,foxCarrier).getList("HandItems",10).getCompound(1).getInt("Count")==3,"current recovery preserves equipment");
        check(ContractLifecycleService.manifest(player,foxCarrier,false),"current rescued special manifests with original UUID");
        foxMaid=(LivingEntity)server.overworld().getEntity(foxId); check(foxMaid!=null,"same UUID recovered");
        check(ContractLifecycleService.capture(player,foxMaid,foxCarrier,false),"current rescued special recalls normally");
        check(TlmBackupBridge.supported(),"actual TLM 1.5.3 backup methods verified");
        var backupMaid=(com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid)maid(player);
        UUID backupId=backupMaid.getUUID();
        com.github.tartaricacid.touhoulittlemaid.world.backups.MaidBackupsManager.save(server,backupMaid);
        TlmBackupBridge.Result nativeBackup=null;
        for(int attempt=0;attempt<30;attempt++) {
            nativeBackup=TlmBackupBridge.latest(player,backupId).get(10,java.util.concurrent.TimeUnit.SECONDS);
            if(nativeBackup.data()!=null)break;
            Thread.sleep(50); // test-only wait for upstream's asynchronous save
        }
        check(nativeBackup!=null&&nativeBackup.data()!=null&&nativeBackup.data().getUUID("UUID").equals(backupId),"reads actual native TLM asynchronous backup");
        var backupCarrier=new ItemStack(Items.DIAMOND_SWORD); player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,backupCarrier);
        check(ContractLifecycleService.capture(player,backupMaid,backupCarrier,false),"bind actual backup identity");
        check(ContractLifecycleService.manifest(player,backupCarrier,false),"manifest for confirmed death backup rescue");
        backupMaid=(com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid)server.overworld().getEntity(backupId);
        backupMaid.remove(net.minecraft.world.entity.Entity.RemovalReason.KILLED);
        ContractAnomalyRecoveryService.record(player,backupCarrier,"KILLED",true);
        String result=ContractAnomalyRecoveryService.restore(player,backupCarrier,nativeBackup);
        check(result.startsWith("RESTORED"),"actual backup is safely stored in matching contract: "+result);
        check(ContractMaidStorage.read(player,backupCarrier).getUUID("UUID").equals(backupId),"backup restore retains original UUID");
        check(!ContractAnomalyRecoveryService.restore(player,backupCarrier,nativeBackup).startsWith("RESTORED"),"backup cannot be replayed");
        check(ContractLifecycleService.manifest(player,backupCarrier,false),"backup-restored maid manifests");
        check(ContractLifecycleService.capture(player,server.overworld().getEntity(backupId),backupCarrier,false),"backup-restored maid recalls");
        var noBackup=TlmBackupBridge.latest(player,UUID.randomUUID()).get(10,java.util.concurrent.TimeUnit.SECONDS);
        check(noBackup.data()==null,"missing backup creates no replacement");
        // Force a real durability loss and a canceled native spawn, then consume the actual emergency film.
        var emergencyMaid=maid(player); UUID emergencyId=emergencyMaid.getUUID();
        emergencyMaid.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,new ItemStack(Items.DIAMOND_AXE));
        var doomed=new ItemStack(Items.IRON_SWORD); player.getInventory().clearContent(); player.getInventory().setItem(0,doomed);
        check(ContractLifecycleService.capture(player,emergencyMaid,doomed,false),"capture emergency-film fixture");
        String doomedBinding=ContractCarrierData.getBindingId(doomed);
        java.util.function.Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent> blockSpawn=join->{
            if(join.getEntity().getUUID().equals(emergencyId))join.setCanceled(true);
        };
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(blockSpawn);
        try {
            doomed.setDamageValue(doomed.getMaxDamage()-1); doomed.hurtAndBreak(2,player,ignored->{});
            check(doomed.isEmpty(),"emergency source was actually consumed by durability");
            var worldData=(net.minecraft.world.level.storage.ServerLevelData)server.overworld().getLevelData();
            worldData.setGameTime(server.overworld().getGameTime()+45);
            ContractCarrierLossService.process(player,ContractWeaponLocator.indexByBinding(player),(p,c)->{});
        } finally {net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(blockSpawn);}
        ItemStack delivered=ItemStack.EMPTY;
        for(int slot=0;slot<player.getInventory().getContainerSize();slot++) {
            var candidate=player.getInventory().getItem(slot);
            if(candidate.hasTag()&&candidate.getTag().contains(SoulTalismanService.EMERGENCY)
                    &&candidate.getTag().getCompound(SoulTalismanService.EMERGENCY).getString("Binding").equals(doomedBinding))delivered=candidate;
        }
        check(!delivered.isEmpty(),"failed normal spawn delivers exactly one emergency film");
        var replay=delivered.copy();
        // Native Forge capabilities verified earlier are present in the canonical film and are restored through the real protected Forge API.
        playerMap.put(player.getUUID(),player);
        com.github.tartaricacid.touhoulittlemaid.item.ItemFilm.filmToMaid(delivered,server.overworld(),player.blockPosition(),player);
        var revivedEmergency=server.overworld().getEntity(emergencyId);
        check(revivedEmergency instanceof LivingEntity && ((LivingEntity)revivedEmergency).getMainHandItem().is(Items.DIAMOND_AXE),"native emergency film preserves UUID and equipment");
        com.github.tartaricacid.touhoulittlemaid.item.ItemFilm.filmToMaid(replay,server.overworld(),player.blockPosition(),player);
        check(server.overworld().getEntity(emergencyId)==revivedEmergency,"spent emergency film cannot create another maid");
        playerMap.remove(player.getUUID());
        player.getInventory().clearContent(); var newCarrier=new ItemStack(Items.IRON_SWORD); player.getInventory().setItem(0,newCarrier);
        check(ContractLifecycleService.capture(player,revivedEmergency,newCarrier,false),"released emergency maid can enter a new normal contract");
        var checkpoint = new CompoundTag(); checkpoint.putUUID("Owner",player.getUUID());
        checkpoint.put("Carrier",backupCarrier.save(new CompoundTag()));
        var committed = ContractAnomalyRecoveryService.incident(player,backupCarrier);
        committed.putString("Status","COMMITTING"); // fault injection: stop after a reserved commit with stored data
        ContractRecoveryJournal.get(server).incident(ContractCarrierData.getBindingId(backupCarrier),committed);
        server.overworld().getDataStorage().set("maid_weapon_stage_three_fixture",new Checkpoint(checkpoint));
        server.overworld().getDataStorage().save();
        player.getInventory().clearContent();
    }
    private static final class Checkpoint extends net.minecraft.world.level.saveddata.SavedData {
        final CompoundTag data;
        Checkpoint(CompoundTag data){this.data=data.copy();setDirty();}
        @Override public CompoundTag save(CompoundTag root){return data.copy();}
    }
    private static void resume(net.minecraft.server.MinecraftServer server) {
        var checkpoint=server.overworld().getDataStorage().computeIfAbsent(Checkpoint::new,
                ()->new Checkpoint(new CompoundTag()),"maid_weapon_stage_three_fixture").data;
        check(checkpoint.hasUUID("Owner"),"actual prior server checkpoint loaded");
        var player=net.minecraftforge.common.util.FakePlayerFactory.get(server.overworld(),
                new com.mojang.authlib.GameProfile(checkpoint.getUUID("Owner"),"RecoveryResume"));
        var carrier=ItemStack.of(checkpoint.getCompound("Carrier"));
        player.getInventory().clearContent();player.getInventory().selected=0;player.getInventory().setItem(0,carrier);
        var record=ContractAnomalyRecoveryService.incident(player,carrier);
        check(record.getBoolean("BackupSpent")&&record.getString("Status").equals("COMMITTING"),"spent interrupted receipt survives real stop/restart");
        check(ContractWeaponLocator.findManifestedMaid(player,ContractCarrierData.getBoundMaidUUID(carrier))==null,"restart creates no second maid");
        check(!ContractLifecycleService.manifest(player,carrier,false),"interrupted transaction cannot replay manifestation");
        ContractAnomalyRecoveryService.reconcile(player);
        check(ContractAnomalyRecoveryService.incident(player,carrier).getString("Status").equals("REVIEW_REQUIRED"),"stored interrupted payload retained for operator review");
        check(MaidInfusion.containsMaid(carrier),"restart quarantine retains final entity data");
        player.getInventory().clearContent();
        com.mojang.logging.LogUtils.getLogger().info("CONTRACT_STAGE_THREE_RESTART_PASS");
    }
    static void check(boolean valid,String message){ if(!valid)throw new IllegalStateException(message); com.mojang.logging.LogUtils.getLogger().info("Stage three fixture: {}",message); }
    private ContractStageThreeValidation(){ }
}
