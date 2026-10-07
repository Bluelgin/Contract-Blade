package com.maidweapon.forge.event;

import com.maidweapon.forge.system.fox.challenge.FoxChallengeArena;
import com.maidweapon.forge.system.fox.challenge.FoxChallengeEntry;
import com.maidweapon.forge.system.fox.challenge.FoxChallengeService;
import com.maidweapon.forge.system.fox.challenge.ShrineRitualSites;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.block.BedBlock;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Scoped entrances, arena protection and return recovery. No modifications to normal worlds. */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class FoxChallengeEvents {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void drops(net.minecraftforge.event.entity.living.LivingDropsEvent event) {
        // Canceled/captured grave-mod drops are not ours to recreate or move.
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player)
            com.maidweapon.forge.system.fox.challenge.FoxChallengeDrops.returnDeathDrops(player, event.getDrops());
    }
    @SubscribeEvent
    public static void started(net.minecraftforge.event.server.ServerStartedEvent event) {
        com.maidweapon.forge.system.fox.challenge.BlackFoxShrineReturn.get(event.getServer()).recoverInterrupted();
    }
    @SubscribeEvent
    public static void joined(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof ItemFrame frame)
            ShrineRitualSites.observe(frame);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void sleep(PlayerSleepInBedEvent event) {
        if (event.getResultStatus() == null && event.getEntity() instanceof ServerPlayer player)
            FoxChallengeEntry.prepareSleep(player, event.getPos());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        com.maidweapon.forge.system.fox.challenge.FoxChallengeDrops.tickRecovery(player);
        if (player.tickCount % 20 == 0)
            com.maidweapon.forge.system.fox.challenge.BlackFoxShrineReturn.get(player.getServer())
                    .retry(player.getServer(), player.getUUID());
        if (!FoxChallengeService.inside(player)) { FoxChallengeEntry.tick(player); return; }
        if (!player.isAlive()) return;
        var origin = FoxChallengeService.origin(player);
        if (!FoxChallengeArena.contains(origin, player.getX(), player.getY(), player.getZ())) {
            var entry = FoxChallengeArena.entry(origin);
            player.teleportTo(player.serverLevel(), entry.getX() + 0.5, entry.getY() + 0.1, entry.getZ() + 0.5,
                    player.getYRot(), player.getXRot());
            player.fallDistance = 0;
            player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        }
        if (player.tickCount % 20 == 0) FoxChallengeArena.atmosphere(player.serverLevel(), origin);
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && FoxChallengeService.inside(player))
            FoxChallengeService.leave(player);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FoxChallengeEntry.clearPending(player);
            if (FoxChallengeService.inside(player)) FoxChallengeService.leave(player);
        }
    }

    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        FoxChallengeService.copyReturn(event.getOriginal(), event.getEntity());
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && FoxChallengeService.hasReturn(player))
            FoxChallengeService.restore(player);
    }

    @SubscribeEvent
    public static void use(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !FoxChallengeService.inside(player)) return;
        if (event.getItemStack().isEmpty() && event.getPos().equals(
                FoxChallengeArena.returnLight(FoxChallengeService.origin(player)))) {
            event.setCanceled(true);
            FoxChallengeService.leave(player);
        } else if (player.level().getBlockState(event.getPos()).getBlock() instanceof BedBlock
                || event.getItemStack().getItem() instanceof net.minecraft.world.item.BucketItem) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void useItem(PlayerInteractEvent.RightClickItem event) {
        // Buckets use a ray trace from an air-click too, bypassing RightClickBlock.
        if (event.getEntity() instanceof ServerPlayer player && FoxChallengeService.inside(player)
                && event.getItemStack().getItem() instanceof net.minecraft.world.item.BucketItem)
            event.setCanceled(true);
    }

    @SubscribeEvent
    public static void breakBlock(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level
                && level.dimension().equals(FoxChallengeService.LEVEL)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void placeBlock(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level
                && level.dimension().equals(FoxChallengeService.LEVEL)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getFrom().equals(FoxChallengeService.LEVEL) && !event.getTo().equals(FoxChallengeService.LEVEL)
                && event.getEntity() instanceof ServerPlayer player) FoxChallengeService.forgetReturn(player);
        if (event.getEntity() instanceof ServerPlayer player) FoxChallengeEntry.clearPending(player);
    }

    @SubscribeEvent
    public static void explosion(ExplosionEvent.Detonate event) {
        if (event.getLevel().dimension().equals(FoxChallengeService.LEVEL)) event.getAffectedBlocks().clear();
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("foxchallenge")
                .then(Commands.literal("recover").requires(source -> source.hasPermission(2)).executes(ctx ->
                        com.maidweapon.forge.system.fox.challenge.FoxChallengeDrops.startRecovery(ctx.getSource().getPlayerOrException()) ? 1 : 0))
                .then(Commands.literal("leave").executes(ctx -> FoxChallengeService.leave(ctx.getSource().getPlayerOrException()) ? 1 : 0))
                .then(Commands.literal("fight").requires(source -> source.hasPermission(2)).executes(ctx -> {
                    var player = ctx.getSource().getPlayerOrException();
                    if (!FoxChallengeService.inside(player) && !FoxChallengeService.enter(player,
                            net.minecraft.world.item.ItemStack.EMPTY, FoxChallengeService.position(player))) return 0;
                    return com.maidweapon.forge.system.fox.challenge.BlackFoxEncounters.start(player) ? 1 : 0;
                }))
                .then(Commands.literal("preview").requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            var player = ctx.getSource().getPlayerOrException();
                            return FoxChallengeService.enter(player, net.minecraft.world.item.ItemStack.EMPTY,
                                    FoxChallengeService.position(player)) ? 1 : 0;
                        })));
    }

    private FoxChallengeEvents() { }
}
