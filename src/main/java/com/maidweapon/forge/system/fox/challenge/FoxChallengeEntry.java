package com.maidweapon.forge.system.fox.challenge;

import com.maidweapon.forge.compat.BladeTetraStoryCompat;
import com.maidweapon.forge.compat.SlashBladeCompat;
import com.maidweapon.forge.system.fox.FoxSpiritState;
import com.maidweapon.forge.system.fox.FoxSpiritTransferService;
import com.maidweapon.forge.system.fox.ShrineFoxStory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Two deliberate story entrances; ordinary beds and unrelated falls remain vanilla/provider-owned. */
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
        if (BladeTetraStoryCompat.isLoaded() || !SlashBladeCompat.isLoaded() || bed == null
                || whiteFox(player).isEmpty() || !ShrineRitualSites.prepared(player.serverLevel(), bed)) return;
        var point = FoxChallengeService.position(player);
        point.putLong("Bed", bed.asLong());
        player.getPersistentData().put(DREAM, point);
    }

    public static void tick(ServerPlayer player) {
        if (!SlashBladeCompat.isLoaded() || FoxChallengeService.inside(player)) return;
        var data = player.getPersistentData();
        if (data.contains(DREAM)) {
            var point = data.getCompound(DREAM);
            if (!player.isSleeping()) data.remove(DREAM);
            else if (player.getSleepTimer() >= 20) {
                var bed = player.getSleepingPos().orElse(null);
                ItemStack white = whiteFox(player);
                if (!BladeTetraStoryCompat.isLoaded() && bed != null && !white.isEmpty()
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
        if (player.tickCount % 5 != 0 || !BladeTetraStoryCompat.isLoaded()
                || !player.level().dimension().location().toString().equals("blade_tetra:divine_domain")) return;
        var route = BladeTetraStoryCompat.clearedRoute(player);
        ItemStack white = whiteFox(player);
        if (route == null || white.isEmpty()) { data.remove(GUIDE); return; }
        // Let the location-specific echo precede the edge guidance, including veteran first meetings.
        if (!ShrineFoxStory.hasHeardDivineEcho(player)) return;
        double x = player.getX() - route.originX();
        double z = player.getZ() - route.originZ();
        long now = player.level().getGameTime();
        var guide = data.getCompound(GUIDE);
        boolean guided = guide.getLong("Until") > now && guide.getInt("X") == route.originX()
                && guide.getInt("Z") == route.originZ()
                && guide.hasUUID("Spirit") && guide.getUUID("Spirit")
                .equals(FoxSpiritState.resident(white).getUUID("SpiritUUID"));
        if (!guided && x >= 17 && x <= 25 && Math.abs(z - 0.5) <= 6 && player.getY() >= 63) {
            player.sendSystemMessage(Component.translatable("maid_weapon.fox.challenge.white.jump"));
            guide.putLong("Until", now + 1200);
            guide.putInt("X", route.originX()); guide.putInt("Z", route.originZ());
            guide.putUUID("Spirit", FoxSpiritState.resident(white).getUUID("SpiritUUID"));
            data.put(GUIDE, guide);
        }
        if (now % 20 == 0) player.serverLevel().sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                route.originX() + 24.5, 64.1, route.originZ() + 0.5, 4, 0.7, 0.15, 1, 0);
        // Intercept well above BladeTetra's y=53 fall recovery, only at the indicated east edge.
        if (guided && x >= 21 && x <= 29 && Math.abs(z - 0.5) <= 5
                && player.getY() < 62 && player.getDeltaMovement().y < 0) {
            if (FoxChallengeService.enter(player, white, route.returnPoint())) data.remove(GUIDE);
        }
    }

    public static void clearPending(ServerPlayer player) {
        player.getPersistentData().remove(GUIDE);
        player.getPersistentData().remove(DREAM);
    }

    private FoxChallengeEntry() { }
}
