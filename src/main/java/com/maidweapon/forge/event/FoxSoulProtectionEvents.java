package com.maidweapon.forge.event;

import com.maidweapon.forge.system.fox.FoxSpiritState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.GrindstoneEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** A spirit-bearing soul seal is table-only, never a native maid spawn/capture item. */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class FoxSoulProtectionEvents {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void use(PlayerInteractEvent.RightClickItem event) { blockSeal(event); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void useOn(PlayerInteractEvent.RightClickBlock event) {
        if (FoxSpiritState.isSeal(event.getItemStack()) && event.getLevel().getBlockState(event.getPos())
                .is(com.maidweapon.forge.init.ModBlocks.MAID_INJECTOR.get())) {
            event.setUseItem(net.minecraftforge.eventbus.api.Event.Result.DENY);
            return;
        }
        blockSeal(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interact(PlayerInteractEvent.EntityInteract event) { blockSeal(event); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interactAt(PlayerInteractEvent.EntityInteractSpecific event) { blockSeal(event); }

    private static void blockSeal(PlayerInteractEvent event) {
        if (!FoxSpiritState.isSeal(event.getItemStack())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        if (!event.getLevel().isClientSide()) event.getEntity().displayClientMessage(
                Component.translatable("maid_weapon.fox.seal.table_only"), true);
    }

    @SubscribeEvent
    public static void anvil(AnvilUpdateEvent event) {
        // Rename/book/material repair keep the left stack; never consume a resident on the right.
        if (FoxSpiritState.isProtected(event.getRight()) || FoxSpiritState.isSeal(event.getLeft()))
            event.setCanceled(true);
    }

    @SubscribeEvent
    public static void grindstone(GrindstoneEvent.OnPlaceItem event) {
        if (FoxSpiritState.isProtected(event.getTopItem()) || FoxSpiritState.isProtected(event.getBottomItem()))
            event.setCanceled(true);
    }

    private FoxSoulProtectionEvents() { }
}
