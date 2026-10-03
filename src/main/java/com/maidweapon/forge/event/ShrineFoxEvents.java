package com.maidweapon.forge.event;

import com.maidweapon.forge.system.fox.ShrineFoxStory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Observe actual acquisition, not a cancellable click on a stand. */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class ShrineFoxEvents {
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player
                && player.tickCount % 20 == 0) {
            ShrineFoxStory.observe(player);
            com.maidweapon.forge.system.fox.FoxSpiritCompanionService.synchronizeInventory(player);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        ShrineFoxStory.copyProgress(event.getOriginal(), event.getEntity());
    }

    private ShrineFoxEvents() { }
}
