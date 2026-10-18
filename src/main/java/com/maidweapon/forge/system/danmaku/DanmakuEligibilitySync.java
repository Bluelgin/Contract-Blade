package com.maidweapon.forge.system.danmaku;

import com.maidweapon.forge.network.DanmakuNetwork;
import com.maidweapon.forge.system.mastery.ContractMasteryService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Owner-only entitlement synchronization. Not a permission token for the server. */
@Mod.EventBusSubscriber
public final class DanmakuEligibilitySync {
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer owner)
                || owner.tickCount % 10 != 0) return;
        var contract = DanmakuEligibility.context(owner);
        int level = ContractMasteryService.getLevel(owner);
        DanmakuNetwork.sync(owner, contract == null ? "" : contract.binding(),
                contract != null, level);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        DanmakuNetwork.forget(event.getEntity().getUUID());
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { DanmakuNetwork.clearAll(); }
    private DanmakuEligibilitySync() { }
}
