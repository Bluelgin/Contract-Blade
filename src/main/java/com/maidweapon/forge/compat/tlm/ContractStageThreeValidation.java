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
        check(SoulTalismanService.importAllowed(player,staleSeal),"copied talisman is allowed when no real UUID exists");
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
        check(!ContractWeaponLocator.mayManifest(player,id.toString()),"unloaded original blocks another real UUID");
        check(!ContractLifecycleService.manifest(player,snapshot.copy(),false),"a stored ItemStack copy cannot resurrect an unloaded maid");
        ContractAnomalyRecoveryService.record(player,weapon,"KILLED",true);
        var incident=ContractAnomalyRecoveryService.incident(player,weapon);
        check(ContractAnomalyRecoveryService.releasesSlot(player,weapon),"confirmed loss releases follower slot");
        check(ContractAnomalyRecoveryService.cleanup(player,weapon,incident),"confirmed ordinary unbind");
        check(MaidInfusion.isInfused(weapon)&&weapon.getItem()==Items.DIAMOND_SWORD,"cleanup retains identity and native weapon");
        check(ContractAnomalyRecoveryService.cleanup(player,weapon,incident),"repeated runtime cleanup is idempotent");
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
        // Native TLM APIs create the backup photo; Contract Blade never opens a backup file itself.
        var backupMaid=(com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid)maid(player);
        UUID backupId=backupMaid.getUUID();
        backupMaid.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND,new ItemStack(Items.EMERALD,4));
        var backupCarrier=new ItemStack(Items.DIAMOND_SWORD); player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,backupCarrier);
        check(ContractLifecycleService.capture(player,backupMaid,backupCarrier,false),"bind native backup identity");
        check(ContractLifecycleService.manifest(player,backupCarrier,false),"manifest native backup fixture");
        backupMaid=(com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid)server.overworld().getEntity(backupId);
        com.github.tartaricacid.touhoulittlemaid.world.backups.MaidBackupsManager.save(server,backupMaid);
        CompoundTag nativeBackup=new CompoundTag();
        for(int attempt=0;attempt<60;attempt++) {
            var files=com.github.tartaricacid.touhoulittlemaid.world.backups.MaidBackupsManager.getMaidBackupFiles(player,backupId);
            if(!files.isEmpty()) nativeBackup=com.github.tartaricacid.touhoulittlemaid.world.backups.MaidBackupsManager.getMaidBackFile(player,backupId,files.get(0));
            if(!nativeBackup.isEmpty()) break;
            Thread.sleep(50); // fixture-only wait for the upstream asynchronous writer
        }
        check(nativeBackup.hasUUID("UUID")&&nativeBackup.getUUID("UUID").equals(backupId),"actual TLM backup API returns original identity");
        com.github.tartaricacid.touhoulittlemaid.item.ItemCamera.spawnMaidPhoto(server.overworld(),nativeBackup,player);
        check(!ContractWeaponLocator.mayManifest(player,backupId.toString()),"backup photo generation never clears a live UUID guard");
        // Native custom death is offline here, so no capture can preserve a newer snapshot.
        backupMaid.remove(net.minecraft.world.entity.Entity.RemovalReason.KILLED);
        ContractAnomalyRecoveryService.record(player,backupCarrier,"KILLED",true);
        check(ContractAnomalyRecoveryService.releasesSlot(player,backupCarrier),"missing maid does not lock follower capacity");
        var photo=ItemStack.EMPTY;
        for(int slot=0;slot<player.getInventory().getContainerSize();slot++) {
            var candidate=player.getInventory().getItem(slot);
            if(candidate.is(item("touhou_little_maid:photo").getItem())&&candidate.getTag().getCompound("MaidInfo").getUUID("UUID").equals(backupId)) photo=candidate;
        }
        check(!photo.isEmpty(),"native camera API delivers an actual restoration photo");
        playerMap.put(player.getUUID(),player);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,photo);
        var pos=player.blockPosition().below(); server.overworld().setBlockAndUpdate(pos,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        var context=new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),net.minecraft.core.Direction.UP,pos,false));
        photo.useOn(context); playerMap.remove(player.getUUID());
        var restored=server.overworld().getEntity(backupId);
        check(restored instanceof LivingEntity&&((LivingEntity)restored).getOffhandItem().getCount()==4,"native ItemPhoto preserves original UUID and historical equipment");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,backupCarrier);
        check(ContractLifecycleService.capture(player,restored,backupCarrier,false),"native-restored maid can reenter original contract");
        // Legacy incident flags and creative provenance do not override a current complete snapshot.
        var review=new CompoundTag(); review.putBoolean("Confirmed",true); review.putString("Status","REVIEW_REQUIRED");
        review.putUUID("Owner",player.getUUID()); review.putString("Maid",backupId.toString());review.putString("Binding",ContractCarrierData.getBindingId(backupCarrier));
        ContractRecoveryJournal.get(server).incident(ContractCarrierData.getBindingId(backupCarrier),review);
        backupCarrier.getOrCreateTag().putBoolean("MaidWeaponClientSuppliedContract",true);
        var staleDestination=ContractRecoveryJournal.get(server).transfer(backupId.toString());staleDestination.putString("Kind","EMERGENCY");
        ContractRecoveryJournal.get(server).transfer(backupId.toString(),staleDestination);
        var copy=backupCarrier.copy();player.getInventory().setItem(1,copy);
        check(ContractLifecycleService.manifest(player,backupCarrier,false),"current snapshot manifests despite old review/emergency receipt/client provenance and inventory copy");
        check(!ContractLifecycleService.manifest(player,copy,false),"copy cannot create second loaded UUID");
        check(ContractLifecycleService.capture(player,server.overworld().getEntity(backupId),backupCarrier,false),"real entity recalls with a duplicate item present");
        player.getInventory().setItem(1,ItemStack.EMPTY);
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
        // An active ordinary contract and dormant intrinsic spirit share one physical carrier.
        var guest=maid(player);String guestId=guest.getStringUUID();var multi=new ItemStack(Items.IRON_SWORD);
        player.getInventory().clearContent();player.getInventory().setItem(0,multi);
        check(ContractLifecycleService.capture(player,guest,multi,false),"capture ordinary guest of a multichannel carrier");
        check(com.maidweapon.forge.api.IntrinsicSpiritApi.ensureIntrinsicSpirit(player,multi,"fixture:resident","Resident"),"create independent dormant intrinsic channel");
        String residentId=com.maidweapon.forge.api.IntrinsicSpiritApi.readOnlyContract(multi,"fixture:resident").getString("MaidUUID");
        check(!residentId.isEmpty()&&!residentId.equals(guestId),"multichannel fixture owns two existing original UUIDs");
        multi.setDamageValue(multi.getMaxDamage()-1);multi.hurtAndBreak(2,player,ignored->{});
        check(multi.isEmpty(),"multichannel carrier actually consumed");
        for(int pass=0;pass<8;pass++) {
            ((net.minecraft.world.level.storage.ServerLevelData)server.overworld().getLevelData()).setGameTime(server.overworld().getGameTime()+45);
            ContractCarrierLossService.process(player,ContractWeaponLocator.indexByBinding(player),(p,c)->{});
            if(server.overworld().getEntity(UUID.fromString(guestId))!=null&&server.overworld().getEntity(UUID.fromString(residentId))!=null) break;
        }
        var freeGuest=server.overworld().getEntity(UUID.fromString(guestId));var freeResident=server.overworld().getEntity(UUID.fromString(residentId));
        check(freeGuest!=null&&freeResident!=null,"durability rescue preserves active and dormant original maids");
        check(freeGuest.getPersistentData().getString(ContractMaidKeys.ENTITY_BINDING_ID).isEmpty()
            &&freeResident.getPersistentData().getString(ContractMaidKeys.ENTITY_BINDING_ID).isEmpty(),"both recovered channels become free without lost carrier bindings");
        // Existing native interior safety uses the same capture/manifest boundary after this refactor.
        String oldHomeValidation=System.getProperty("contractblade.home.nativeValidation");
        System.setProperty("contractblade.home.nativeValidation","true");
        try { ContractHomeSafetyValidation.run(server.getLevel(com.maidweapon.forge.system.interior.ContractInteriorService.INTERIOR_LEVEL)); }
        finally {
            if(oldHomeValidation==null)System.clearProperty("contractblade.home.nativeValidation");
            else System.setProperty("contractblade.home.nativeValidation",oldHomeValidation);
        }
        // Save an actually manifested original plus a stale stored copy for restart duplicate protection.
        var restartMaid=maid(player);var restartCarrier=new ItemStack(Items.IRON_SWORD);
        player.getInventory().setItem(0,restartCarrier);
        check(ContractLifecycleService.capture(player,restartMaid,restartCarrier,false),"capture live-restart fixture");
        var storedCopy=restartCarrier.copy();String restartId=ContractCarrierData.getBoundMaidUUID(restartCarrier);
        check(ContractLifecycleService.manifest(player,restartCarrier,false),"manifest live original before restart");
        var checkpoint = new CompoundTag(); checkpoint.putUUID("Owner",player.getUUID());
        checkpoint.put("Carrier",backupCarrier.save(new CompoundTag()));
        checkpoint.put("StoredCopy",storedCopy.save(new CompoundTag()));checkpoint.putString("LiveMaid",restartId);
        var committed = ContractAnomalyRecoveryService.incident(player,backupCarrier);
        committed.putBoolean("BackupSpent",true); committed.putString("Status","COMMITTING"); // fault injection: stop after a reserved commit with stored data
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
        var liveCopy=ItemStack.of(checkpoint.getCompound("StoredCopy"));
        check(ContractRecoveryJournal.get(server).transfer(checkpoint.getString("LiveMaid")).getBoolean("WorldPresent"),"real live original world presence survives server restart");
        check(!ContractLifecycleService.manifest(player,liveCopy,false),"persisted live or unloaded original prevents copied snapshot from spawning after restart");
        var carrier=ItemStack.of(checkpoint.getCompound("Carrier"));
        player.getInventory().clearContent();player.getInventory().selected=0;player.getInventory().setItem(0,carrier);
        var record=ContractAnomalyRecoveryService.incident(player,carrier);
        check(record.getBoolean("BackupSpent")&&record.getString("Status").equals("COMMITTING"),"spent interrupted receipt survives real stop/restart");
        check(ContractWeaponLocator.findManifestedMaid(player,ContractCarrierData.getBoundMaidUUID(carrier))==null,"restart creates no second maid");
        ContractAnomalyRecoveryService.reconcile(player);
        check(ContractAnomalyRecoveryService.incident(player,carrier).isEmpty(),"old interrupted diagnostics clear when a current complete snapshot exists");
        check(ContractLifecycleService.manifest(player,carrier,false),"stored original works after a real restart despite old commit marker");
        check(ContractLifecycleService.capture(player,ContractWeaponLocator.findManifestedMaid(player,ContractCarrierData.getBoundMaidUUID(carrier)),carrier,false),"restart original recalls safely");
        player.getInventory().clearContent();
        com.mojang.logging.LogUtils.getLogger().info("CONTRACT_STAGE_THREE_RESTART_PASS");
    }
    static void check(boolean valid,String message){ if(!valid)throw new IllegalStateException(message); com.mojang.logging.LogUtils.getLogger().info("Stage three fixture: {}",message); }
    private ContractStageThreeValidation(){ }
}
