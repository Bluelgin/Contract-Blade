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

    @SubscribeEvent
    public static void itemSpawn(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity item
                && TripleMagicCompat.isPhantom(item.getItem())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void toss(net.minecraftforge.event.entity.item.ItemTossEvent event) {
        if (TripleMagicCompat.isPhantom(event.getEntity().getItem())) event.setCanceled(true);
    }

    private ContractProjectionDropGuard() { }
}
