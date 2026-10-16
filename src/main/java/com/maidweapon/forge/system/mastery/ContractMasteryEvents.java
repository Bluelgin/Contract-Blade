package com.maidweapon.forge.system.mastery;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class ContractMasteryEvents {
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) { ContractMasteryService.observeInventory(player); ContractMasteryService.sync(player); }
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ContractMasteryService.sync(player);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ContractMasteryService.sync(player);
    }
    @SubscribeEvent public static void close(PlayerContainerEvent.Close event) {
        if (event.getEntity() instanceof ServerPlayer player) ContractMasteryService.observeInventory(player);
    }
    @SubscribeEvent public static void pickup(PlayerEvent.ItemPickupEvent event) {
        ContractMasteryService.observe(event.getEntity(), event.getStack());
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END
                && event.player instanceof ServerPlayer player
                && player.tickCount % 40 == 0)
            com.maidweapon.forge.network.ContractMasteryNetwork.refreshIfChanged(player);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            com.maidweapon.forge.network.ContractMasteryNetwork.forget(player);
    }
    private ContractMasteryEvents() { }
}
