package com.maidweapon.forge.system.interior;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import com.maidweapon.forge.compat.tlm.TlmHomeBehaviorController;
import com.maidweapon.forge.system.interior.home.ContractHomeRuntime;
import com.maidweapon.forge.system.interior.home.ContractInteriorGuideService;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerDestroyItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Safety hooks around the contract-interior lifecycle. */
@Mod.EventBusSubscriber
public final class ContractInteriorEvents {
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        ContractInteriorService.recoverFromVoid(player);
        if (player.tickCount % 20 != 0) return;
        if (player.tickCount % 600 == 0) {
            ContractInteriorService.resumeHome(player);
            String binding = ContractInteriorService.activeOwnedBinding(player);
            if (!binding.isEmpty()) {
                var saved = ContractInteriorSavedData.get(player.getServer());
                ContractInteriorGuideService.give(player, saved, saved.getOrCreate(binding));
            }
        }
        ContractHomeRuntime.tick(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ContractInteriorService.prepareForLogout(player);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ContractInteriorService.prepareForDeath(player);
        }
    }

    @SubscribeEvent
    public static void onActiveContractDestroyed(PlayerDestroyItemEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !ContractInteriorService.isInside(player)
                || ContractInteriorService.isGallerySession(player)) return;
        ContractInteriorService.recoverDestroyedActiveContract(player, event.getOriginal());
    }

    @SubscribeEvent
    public static void onToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (ContractInteriorService.isActiveContract(player, event.getEntity().getItem())) {
            net.minecraft.world.item.ItemStack protectedStack = event.getEntity().getItem().copy();
            event.setCanceled(true);
            player.getInventory().placeItemBackInInventory(protectedStack);
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable(
                            "maid_weapon.message.interior.contract_drop_blocked"),
                    true
            );
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ContractInteriorService.resumeHome(player);
    }

    @SubscribeEvent
    public static void onDimensionChanged(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getFrom().equals(ContractInteriorService.INTERIOR_LEVEL)
                && event.getEntity() instanceof ServerPlayer player) ContractInteriorService.leaveUnexpectedly(player);
    }

    @SubscribeEvent
    public static void onStop(ServerStoppingEvent event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers())
            ContractInteriorService.prepareForLogout(player);
        ContractHomeRuntime.clear();
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) { invalidate(event); }
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) { invalidate(event); }
    private static void invalidate(BlockEvent event) {
        if (event.getLevel() instanceof ServerLevel level
                && level.dimension().equals(ContractInteriorService.INTERIOR_LEVEL))
            ContractHomeRuntime.invalidate(level, event.getPos());
    }
    @SubscribeEvent
    public static void onMaidTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
        if (!event.getEntity().level().isClientSide && event.getEntity().tickCount % 20 == 0
                && event.getEntity() instanceof net.minecraft.world.entity.Mob maid)
            ContractHomeRuntime.guardResident(maid);
    }
    @SubscribeEvent
    public static void onManagedSeatDismount(EntityMountEvent event) {
        if (!event.isDismounting()
                || !(event.getEntityMounting() instanceof net.minecraft.world.entity.Mob maid)) return;
        if (TlmHomeBehaviorController.shouldKeepManagedSeat(maid, event.getEntityBeingMounted())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level
                && level.dimension().equals(ContractInteriorService.INTERIOR_LEVEL))
            ContractHomeRuntime.invalidate(level, event.getEntity().blockPosition());
    }
    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level
                && level.dimension().equals(ContractInteriorService.INTERIOR_LEVEL))
            ContractHomeRuntime.invalidate(level, event.getEntity().blockPosition());
    }

    private ContractInteriorEvents() {
    }
}
