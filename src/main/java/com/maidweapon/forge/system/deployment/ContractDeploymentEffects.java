package com.maidweapon.forge.system.deployment;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Visual/audio presentation for contract deployment transitions. */
public final class ContractDeploymentEffects {
    public static void deployBuildup(Player player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        level.sendParticles(ParticleTypes.SNOWFLAKE,
                player.getX(), player.getY() + 0.15, player.getZ(),
                6, 0.45, 0.08, 0.45, 0.015);
    }

    public static void deployFinish(Entity maid) {
        if (!(maid.level() instanceof ServerLevel level)) return;
        level.sendParticles(ParticleTypes.SNOWFLAKE,
                maid.getX(), maid.getY() + maid.getBbHeight() * 0.55, maid.getZ(),
                24, 0.55, maid.getBbHeight() * 0.45, 0.55, 0.035);
        level.sendParticles(ParticleTypes.END_ROD,
                maid.getX(), maid.getY() + 0.2, maid.getZ(),
                8, 0.35, 0.08, 0.35, 0.015);
        level.playSound(null, maid.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.7f, 1.35f);
    }

    public static void recallBuildup(Entity maid) {
        if (!(maid != null && maid.level() instanceof ServerLevel level)) return;
        level.sendParticles(ParticleTypes.SNOWFLAKE,
                maid.getX(), maid.getY() + maid.getBbHeight() * 0.5, maid.getZ(),
                5, 0.5, maid.getBbHeight() * 0.4, 0.5, 0.01);
    }

    public static void recallFinish(Entity maid) {
        if (!(maid.level() instanceof ServerLevel level)) return;
        level.sendParticles(ParticleTypes.POOF,
                maid.getX(), maid.getY() + maid.getBbHeight() * 0.5, maid.getZ(),
                16, 0.5, maid.getBbHeight() * 0.4, 0.5, 0.025);
        level.sendParticles(ParticleTypes.SNOWFLAKE,
                maid.getX(), maid.getY() + maid.getBbHeight() * 0.5, maid.getZ(),
                14, 0.45, maid.getBbHeight() * 0.35, 0.45, 0.02);
        level.playSound(null, maid.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS, 0.65f, 0.8f);
    }

    private ContractDeploymentEffects() {}
}
