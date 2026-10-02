package com.maidweapon.forge.event;

import com.maidweapon.forge.compat.TripleMagicCompat;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Projection copies are never loot. Real equipment keeps its own original drop chance. */
@Mod.EventBusSubscriber
public final class ContractProjectionDropGuard {
    @SubscribeEvent
    public static void drops(LivingDropsEvent event) {
        event.getDrops().removeIf(drop -> TripleMagicCompat.isPhantom(drop.getItem()));
    }

    private ContractProjectionDropGuard() { }
}
