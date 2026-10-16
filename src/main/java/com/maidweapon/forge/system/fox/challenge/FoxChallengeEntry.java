package com.maidweapon.forge.system.fox.challenge;

import com.maidweapon.forge.compat.SlashBladeCompat;
import com.maidweapon.forge.system.fox.FoxSpiritTransferService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Shrine dream entrance; ordinary beds and all provider-owned falls remain untouched. */
public final class FoxChallengeEntry {
    private static final String GUIDE = "MaidWeaponFoxJumpGuide";
    private static final String DREAM = "MaidWeaponFoxDreamPending";

    public static ItemStack whiteFox(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (FoxSpiritTransferService.storyEligible(player, stack)) return stack;
        }
        return ItemStack.EMPTY;
    }

    /** Capture the pre-sleep standing position, not a sleeping pose inside a bed block. */
    public static void prepareSleep(ServerPlayer player, BlockPos bed) {
        if (!SlashBladeCompat.isLoaded() || bed == null
                || whiteFox(player).isEmpty() || !ShrineRitualSites.prepared(player.serverLevel(), bed)) return;
        var point = FoxChallengeService.position(player);
        point.putLong("Bed", bed.asLong());
        player.getPersistentData().put(DREAM, point);
    }

    public static void tick(ServerPlayer player) {
        // Discard legacy jump guidance without intercepting BladeTetra's fall recovery.
        player.getPersistentData().remove(GUIDE);
        if (!SlashBladeCompat.isLoaded() || FoxChallengeService.inside(player)) return;
        var data = player.getPersistentData();
        if (data.contains(DREAM)) {
            var point = data.getCompound(DREAM);
            if (!player.isSleeping()) data.remove(DREAM);
            else if (player.getSleepTimer() >= 20) {
                var bed = player.getSleepingPos().orElse(null);
                ItemStack white = whiteFox(player);
                if (bed != null && !white.isEmpty()
                        && bed.equals(BlockPos.of(point.getLong("Bed")))
                        && ShrineRitualSites.prepared(player.serverLevel(), bed)) {
                    var stand = ShrineRitualSites.preparedStand(player.serverLevel(), bed);
                    var returns = BlackFoxShrineReturn.get(player.getServer());
                    if (returns.canBegin(player, stand)) {
                        if (FoxChallengeService.enter(player, white, point)) {
                            if (BlackFoxEncounters.get(player) == null || !returns.begin(player, stand))
                                FoxChallengeService.restore(player);
                        }
                    } else player.displayClientMessage(Component.translatable("maid_weapon.fox.black.ritual_unavailable"), true);
                }
                data.remove(DREAM);
            }
        }
    }

    public static void clearPending(ServerPlayer player) {
        player.getPersistentData().remove(GUIDE);
        player.getPersistentData().remove(DREAM);
    }

    private FoxChallengeEntry() { }
}
