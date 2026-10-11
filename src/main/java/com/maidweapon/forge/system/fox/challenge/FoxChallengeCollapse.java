package com.maidweapon.forge.system.fox.challenge;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Player-scoped visual collapse. No block edits, explosions, damage or spawned hazard entities. */
public final class FoxChallengeCollapse {
    private static final String REMAINING = "MaidWeaponFoxVictoryCollapse";
    private static final String WAITING = "MaidWeaponFoxVictoryRecallWaiting";
    public static boolean active(ServerPlayer player) {
        return FoxChallengeService.inside(player) && player.getPersistentData().contains(REMAINING);
    }
    public static void begin(ServerPlayer player) {
        if (!FoxChallengeService.inside(player) || active(player)) return;
        player.getPersistentData().putInt(REMAINING, FoxChallengeCollapseTimeline.DURATION);
        player.clearFire(); player.fallDistance = 0;
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(10, 50, 20));
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.translatable("maid_weapon.fox.challenge.victory")));
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.translatable("maid_weapon.fox.challenge.collapse_start")));
        player.sendSystemMessage(Component.translatable("maid_weapon.fox.challenge.collapse_start"));
        countdown(player, 10);
    }
    public static boolean tick(ServerPlayer player) {
        if (!active(player)) { clear(player); return false; }
        int remaining = Math.max(0, player.getPersistentData().getInt(REMAINING) - 1);
        player.getPersistentData().putInt(REMAINING, remaining);
        player.clearFire(); player.fallDistance = 0;
        if (remaining == 0) {
            // Keep the shared companion-safe return and loose-item recovery path intact.
            if (FoxChallengeService.leave(player)) {
                player.invulnerableTime = 40;
                player.sendSystemMessage(Component.translatable("maid_weapon.fox.challenge.collapse_returned"));
            } else {
                player.getPersistentData().putBoolean(WAITING, true);
                player.getPersistentData().putInt(REMAINING, FoxChallengeCollapseTimeline.RETRY_INTERVAL);
                player.displayClientMessage(Component.translatable("maid_weapon.fox.challenge.collapse_recalling"), true);
            }
            return true;
        }
        if (player.getPersistentData().getBoolean(WAITING)) return true;
        if (remaining % 20 == 0) countdown(player, FoxChallengeCollapseTimeline.seconds(remaining));
        if (remaining % FoxChallengeCollapseTimeline.particleInterval(remaining) == 0) particles(player, remaining);
        return true;
    }
    private static void countdown(ServerPlayer player, int seconds) {
        player.displayClientMessage(Component.translatable("maid_weapon.fox.challenge.collapse_countdown", seconds), true);
        player.playNotifySound(SoundEvents.RESPAWN_ANCHOR_AMBIENT, SoundSource.AMBIENT, .45f, seconds <= 3 ? .7f : 1f);
    }
    private static void particles(ServerPlayer player, int remaining) {
        var level = player.serverLevel();
        var origin = FoxChallengeService.origin(player);
        float progress = FoxChallengeCollapseTimeline.progress(remaining);
        // Eight fixed fractures advance from the rim toward the centre; floor remains safe and unchanged.
        double radius = 21 - progress * 12;
        for (int crack = 0; crack < 8; crack++) {
            double angle = Math.PI * 2 * crack / 8;
            for (int point = 0; point < 5; point++) {
                double r = radius + point * .5;
                double x = origin.getX() + .5 + Math.cos(angle) * r;
                double z = origin.getZ() + .5 + Math.sin(angle) * r;
                double y = origin.getY() + 1.15 + point * (.3 + progress);
                level.sendParticles(player, ParticleTypes.REVERSE_PORTAL, true, x, y, z, 2, .12, .2, .12, .04);
                level.sendParticles(player, ParticleTypes.END_ROD, true, x, y, z, 1, .02, .08, .02, .005);
            }
        }
        if (remaining <= 60) level.sendParticles(player, ParticleTypes.PORTAL, true,
                player.getX(), player.getY() + 1, player.getZ(), 12, 1.5, 1, 1.5, .05);
    }
    public static void clear(ServerPlayer player) {
        player.getPersistentData().remove(REMAINING); player.getPersistentData().remove(WAITING);
    }
    private FoxChallengeCollapse() { }
}
