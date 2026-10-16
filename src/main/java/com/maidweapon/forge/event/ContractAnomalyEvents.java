package com.maidweapon.forge.event;

import com.maidweapon.forge.compat.tlm.TlmEntityAdapter;
import com.maidweapon.forge.compat.tlm.ContractMaidKeys;
import com.maidweapon.forge.system.contract.*;
import com.maidweapon.forge.system.deployment.ContractEmergencyCarrier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class ContractAnomalyEvents {
    @SubscribeEvent public static void left(EntityLeaveLevelEvent event) {
        var maid = event.getEntity();
        if (event.getLevel().isClientSide || !TlmEntityAdapter.isMaidEntity(maid) || !(maid instanceof TamableAnimal tamed)
                || tamed.getOwnerUUID() == null || maid.getServer() == null
                || maid.getPersistentData().getBoolean(ContractMaidKeys.CORE_STORAGE_TRANSFER)) return;
        var reason = maid.getRemovalReason();
        if (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED)
            ContractRecoveryJournal.get(maid.getServer()).markMaidAbsent(maid.getStringUUID());
        var owner = maid.getServer().getPlayerList().getPlayer(tamed.getOwnerUUID());
        if (owner == null) return;
        var carrier = ContractEmergencyCarrier.resolve(owner, maid).stack();
        if (carrier.isEmpty() || ContractCarrierData.hasMaidEntityData(carrier)
                || maid.getPersistentData().getBoolean(ContractMaidKeys.NATIVE_STORAGE_TRANSFER)
                || ContractAnomalyRecoveryService.state(owner, carrier) == ContractAnomalyRecoveryService.State.TRANSITIONING) return;
        if (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED) {
            if (maid instanceof net.minecraft.world.entity.LivingEntity living
                    && ContractAnomalyRecoveryService.preserveRemoved(owner, carrier, living)) {
                ContractEmergencyCarrier.resolve(owner, maid).publish(owner);
            } else ContractAnomalyRecoveryService.confirmedRemoved(owner, carrier);
        }
        // UNLOADED_TO_CHUNK, UNLOADED_WITH_PLAYER and CHANGED_DIMENSION are not death evidence.
    }
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void joined(EntityJoinLevelEvent event) {
        var entity = event.getEntity();
        if (event.getLevel().isClientSide || !TlmEntityAdapter.isMaidEntity(entity)) return;
        if (com.maidweapon.forge.compat.tlm.ContractMaidRebirthBridge.rejected(entity)) { event.setCanceled(true); return; }
        entity.getPersistentData().remove("MaidWeaponRecoveryRejected"); // legacy diagnostic is not permanent authority
        var journal = ContractRecoveryJournal.get(entity.getServer());
        String binding = entity.getPersistentData().getString(com.maidweapon.forge.compat.tlm.ContractMaidKeys.ENTITY_BINDING_ID);
        var destination = journal.transfer(entity.getStringUUID());
        if (destination.getString("Kind").equals("FREE_MAID") && binding.equals(destination.getString("Binding"))) {
            entity.getPersistentData().remove(com.maidweapon.forge.compat.tlm.ContractMaidKeys.ENTITY_BINDING_ID);
            if (destination.contains("Fox")) entity.getPersistentData().put(com.maidweapon.forge.system.fox.FoxSpiritState.ROOT, destination.getCompound("Fox").copy());
        }
        if (event.loadedFromDisk()) journal.markMaidPresent(entity.getStringUUID());
        // Fresh spawns are recorded after successful addEntity, not during a cancellable join event.
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player && player.tickCount % 100 == 0)
            ContractAnomalyRecoveryService.reconcile(player);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { ContractAnomalyRecoveryService.clearSession(); com.maidweapon.forge.compat.tlm.ContractMaidRebirthBridge.clearSession(); }
    private ContractAnomalyEvents() { }
}
