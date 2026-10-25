package com.maidweapon.forge.system.danmaku;

import com.maidweapon.forge.network.DanmakuNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Lifecycle invalidation only; DanmakuCombatService owns the single entitlement publisher. */
@Mod.EventBusSubscriber
public final class DanmakuEligibilitySync {
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        DanmakuNetwork.forget(event.getEntity().getUUID());
    }
    /** Respawning changes the client Player instance even when the UUID is unchanged. */
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DanmakuCombatService.invalidateEligibility(player.getUUID());
            DanmakuNetwork.forget(player.getUUID());
        }
    }
    @SubscribeEvent public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DanmakuCombatService.invalidateEligibility(player.getUUID());
            DanmakuNetwork.forget(player.getUUID());
        }
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { DanmakuNetwork.clearAll(); }
    private DanmakuEligibilitySync() { }
}
