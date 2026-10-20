package com.maidweapon.forge.system.danmaku;

import com.maidweapon.forge.compat.tlm.danmaku.DanmakuPatternPlanner;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Bounded vanilla particle cues for all nearby players, independent of Astra artwork.
 * Purely cosmetic: TLM real projectile collision owns every damage decision.
 * Eight/small groups per wave, never one visual entity per decoration.
 */
public final class DanmakuSpellEffects {
    public static void cast(ServerPlayer owner, DanmakuForm form, LivingEntity target) {
        ServerLevel level = owner.serverLevel();
        level.playSound(null, owner.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE,
                SoundSource.PLAYERS, .32f, form == DanmakuForm.FANTASY ? 1.18f : 1.65f);
        if (form == DanmakuForm.GUARD) {
            DanmakuWorldEvents.send(owner, com.maidweapon.forge.api.danmaku.DanmakuVisualEvent.Kind.GUARD_START,
                    2, 0, owner, owner.position());
            ring(level, owner.position().add(0, 1.15, 0), 2.0, 8, 0);
        } else {
            wave(owner, form, target, 0);
        }
    }

    public static void wave(ServerPlayer owner, DanmakuForm form,
                            LivingEntity target, int phase) {
        ServerLevel level = owner.serverLevel();
        Vec3 origin = owner.getEyePosition();
        Vec3 anchor = form == DanmakuForm.FANTASY
                ? (target == null ? origin.add(owner.getLookAngle().scale(7)) : target.getEyePosition())
                : owner.position().add(0, 1.1, 0);
        DanmakuWorldEvents.send(owner, com.maidweapon.forge.api.danmaku.DanmakuVisualEvent.Kind.WAVE,
                form.ordinal(), phase, owner, anchor);
        switch (form) {
            case NEEDLE -> {
                if (phase > 1) return;
                Vec3 center = origin.add(owner.getLookAngle().scale(1.5));
                ring(level, center, .52, 4, phase);
            }
            case FLOWER -> {
                if (phase > 1) return;
                Vec3 center = origin.add(owner.getLookAngle().scale(2.4));
                ring(level, center, phase == 0 ? 1.8 : 1.1, 5, phase);
            }
            case GUARD -> { }
            case FANTASY -> {
                if (phase < 0 || phase > 3) return;
                Vec3 center = target == null
                        ? origin.add(owner.getLookAngle().scale(7))
                        : target.getEyePosition();
                for (int i = 0; i < 8; i++) {
                    double angle = DanmakuPatternPlanner.fantasyAngle(phase, i);
                    double r = DanmakuPatternPlanner.fantasyRadius(phase, i);
                    Vec3 p = center.add(Math.cos(angle) * r, .25 + phase * .2,
                            Math.sin(angle) * r);
                    level.sendParticles(phase == 3 ? ParticleTypes.END_ROD
                                    : ParticleTypes.CHERRY_LEAVES,
                            p.x, p.y, p.z, 1, 0, 0, 0, 0);
                }
                if (phase == 3)
                    level.sendParticles(ParticleTypes.ENCHANT, center.x, center.y,
                            center.z, 8, .45, .35, .45, .04);
            }
        }
    }

    private static void ring(ServerLevel level, Vec3 center, double radius, int count,
                             int phase) {
        for (int i = 0; i < count; i++) {
            double a = i * Math.PI * 2 / count + phase * .4;
            double x = center.x + Math.cos(a) * radius;
            double z = center.z + Math.sin(a) * radius;
            level.sendParticles(phase == 0 ? ParticleTypes.END_ROD
                            : ParticleTypes.ENCHANT,
                    x, center.y, z, 1, 0, 0, 0, 0);
        }
    }
    private DanmakuSpellEffects() { }
}
