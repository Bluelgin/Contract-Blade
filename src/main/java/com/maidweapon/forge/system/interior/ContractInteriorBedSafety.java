package com.maidweapon.forge.system.interior;

import com.maidweapon.forge.system.interior.home.ContractHomeClock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerSetSpawnEvent;
import net.minecraftforge.event.entity.player.SleepingTimeCheckEvent;
import net.minecraftforge.event.level.SleepFinishedTimeEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.time.Instant;

/** Beds are safe furniture/rest places, not a new respawn authority or a shared clock setter. */
@Mod.EventBusSubscriber
public final class ContractInteriorBedSafety {
    @SubscribeEvent
    public static void onSetSpawn(PlayerSetSpawnEvent event) {
        if (!event.isForced() && event.getSpawnLevel().equals(ContractInteriorService.INTERIOR_LEVEL))
            event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onSleepTime(SleepingTimeCheckEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !ContractInteriorService.isInside(player)) return;
        String binding = ContractInteriorService.activeOwnedBinding(player);
        var plot = ContractInteriorSavedData.get(player.getServer()).find(binding);
        long overworldTime = player.getServer().overworld().getDayTime();
        long skyTime = plot == null ? Math.floorMod(overworldTime, 24000L)
                : ContractHomeClock.skyTime(plot.home().mode, plot.home().zone, Instant.now(), overworldTime);
        // Player beds retain the normal dusk-to-dawn window; maid bedtime can be later.
        event.setResult(skyTime >= 12542 && skyTime <= 23460 ? Event.Result.ALLOW : Event.Result.DENY);
    }

    @SubscribeEvent
    public static void onSleepFinished(SleepFinishedTimeEvent event) {
        if (event.getLevel() instanceof ServerLevel level
                && level.dimension().equals(ContractInteriorService.INTERIOR_LEVEL))
            event.setTimeAddition(level.getDayTime());
    }

    private ContractInteriorBedSafety() {}
}
