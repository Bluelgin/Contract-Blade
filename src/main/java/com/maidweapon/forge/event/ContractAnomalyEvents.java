package com.maidweapon.forge.event;

import com.maidweapon.forge.compat.tlm.TlmEntityAdapter;
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
                || maid.getPersistentData().getBoolean("MaidWeaponCoreStorageTransfer")) return;
        var owner = maid.getServer().getPlayerList().getPlayer(tamed.getOwnerUUID());
        if (owner == null) {
            ContractAnomalyRecoveryService.observeOffline(maid, tamed.getOwnerUUID()); return;
        }
        var carrier = ContractEmergencyCarrier.resolve(owner, maid).stack();
        if (carrier.isEmpty() || ContractCarrierData.hasMaidEntityData(carrier)
                || maid.getPersistentData().getBoolean("MaidWeaponNativeStorageTransfer")
                || ContractAnomalyRecoveryService.state(owner, carrier) == ContractAnomalyRecoveryService.State.TRANSITIONING) return;
        var reason = maid.getRemovalReason();
        if (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED) {
            if (maid instanceof net.minecraft.world.entity.LivingEntity living
                    && ContractAnomalyRecoveryService.preserveRemoved(owner, carrier, living)) {
                ContractEmergencyCarrier.resolve(owner, maid).publish(owner);
            } else ContractAnomalyRecoveryService.record(owner, carrier, reason.name(), true);
        }
        // UNLOADED_TO_CHUNK, UNLOADED_WITH_PLAYER and CHANGED_DIMENSION are not death evidence.
    }
    @SubscribeEvent public static void joined(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity().getPersistentData().getBoolean("MaidWeaponRecoveryRejected")) event.setCanceled(true);
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player && player.tickCount % 100 == 0)
            ContractAnomalyRecoveryService.reconcile(player);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { ContractAnomalyRecoveryService.clearSession(); }
    private ContractAnomalyEvents() { }
}
