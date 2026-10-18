package com.maidweapon.forge.system.danmaku;

import com.maidweapon.forge.compat.tlm.danmaku.ContractDanmakuProjectile;
import com.maidweapon.forge.compat.tlm.danmaku.DanmakuProjectilePatterns;
import com.maidweapon.forge.system.mastery.ContractMasteryService;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Ordinary-maid support coordinator; all actual damage still requires projectile collision. */
@Mod.EventBusSubscriber
public final class DanmakuCombatService {
    private static final Map<String, Long> COOLDOWNS = new HashMap<>();
    private record Chain(UUID target, int count, long time) { }
    private static final Map<String, Chain> CHAINS = new HashMap<>();
    // Delaying actual follow-up bullets until ordinary melee immunity subsides avoids
    // a close-range salvo being swallowed in the same hurt frame.
    private record PendingAuto(String maid, ResourceKey<Level> dimension,
                               UUID target, int mastery, float observedDamage,
                               boolean climax, long due) { }
    private static final Map<String, PendingAuto> AUTOS = new HashMap<>();

    private record PendingSpell(String maid, ResourceKey<Level> dimension, DanmakuForm form,
                                UUID target, int mastery, float basis, int nextWave,
                                long due, boolean synchronizedStrike) { }
    private static final Map<String, PendingSpell> PENDING = new HashMap<>();

    private static final class GuardSession {
        final String maid;
        final ResourceKey<Level> dimension;
        final int mastery;
        final float attack;
        final long expires;
        int remaining;
        UUID aggressor;
        GuardSession(String maid, ResourceKey<Level> dimension, int mastery, float attack,
                     long expires, int remaining) {
            this.maid = maid;
            this.dimension = dimension;
            this.mastery = mastery;
            this.attack = attack;
            this.expires = expires;
            this.remaining = remaining;
        }
    }
    private static final Map<UUID, GuardSession> GUARDS = new HashMap<>();

    @SubscribeEvent public static void afterMelee(LivingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)
                || event.getAmount() <= 0 || player.level().isClientSide) return;
        Entity direct = event.getSource().getDirectEntity();
        // Never loop a supplementary bullet back into another salvo. Native melee and
        // SlashBlade-created sword attacks qualify; unrelated projectiles do not.
        if (direct != player && (direct == null ||
                !direct.getClass().getName().toLowerCase(Locale.ROOT).contains("slashblade"))) return;
        var victim = event.getEntity();
        var contract = DanmakuEligibility.context(player);
        if (contract == null || !ContractDanmakuProjectile.safeTarget(player, victim)) return;

        int mastery = ContractMasteryService.getLevel(player);
        long now = player.level().getGameTime();
        String chainKey = key(player, contract.maid(), "chain");
        Chain previous = CHAINS.get(chainKey);
        int count = previous != null && previous.target.equals(victim.getUUID())
                && now >= previous.time && now - previous.time <= 100
                ? Math.min(3, previous.count + 1) : 1;
        CHAINS.put(chainKey, new Chain(victim.getUUID(), count, now));

        // A player landing a real sword hit during the ritual empowers only its final wave.
        String prefix = player.getUUID() + ":" + contract.maid() + ":";
        for (var entry : PENDING.entrySet()) {
            if (!entry.getKey().startsWith(prefix)) continue;
            PendingSpell pending = entry.getValue();
            if (pending.form == DanmakuForm.FANTASY && Objects.equals(pending.target, victim.getUUID())
                    && !pending.synchronizedStrike) {
                entry.setValue(new PendingSpell(pending.maid, pending.dimension, pending.form,
                        pending.target, pending.mastery, pending.basis, pending.nextWave,
                        pending.due, true));
            }
        }

        String autoKey = key(player, contract.maid(), "auto");
        if (!ready(autoKey, now)) return;
        boolean climax = count >= 3;
        var profile = DanmakuGrowth.auto(mastery, climax);
        COOLDOWNS.put(autoKey, now + profile.cooldownTicks());
        AUTOS.put(key(player, contract.maid(), "queued_auto"),
                new PendingAuto(contract.maid(), player.level().dimension(),
                        victim.getUUID(), mastery, event.getAmount(), climax, now + 11));
        if (climax) CHAINS.put(chainKey, new Chain(victim.getUUID(), 0, now));
    }

    /** Cast packets only indicate a form. Every right is re-established on the server. */
    public static boolean cast(ServerPlayer player, DanmakuForm form) {
        if (form == null) return false;
        var contract = DanmakuEligibility.context(player);
        if (contract == null) return false;
        int level = ContractMasteryService.getLevel(player);
        if (!form.unlocked(level)) return false;
        long now = player.level().getGameTime();
        String cooldownKey = key(player, contract.maid(), "spell_" + form.name());
        if (!ready(cooldownKey, now)) return false;
        float basis = (float) Math.max(1, player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        LivingEntity target = acquireTarget(player);
        COOLDOWNS.put(cooldownKey, now + form.cooldownTicks());

        if (form == DanmakuForm.GUARD) {
            GUARDS.put(player.getUUID(), new GuardSession(contract.maid(), player.level().dimension(),
                    level, basis, now + 60, DanmakuGrowth.count(form, level)));
        } else {
            DanmakuProjectilePatterns.wave(player, form, target, level, basis, 0, false);
            if (form == DanmakuForm.FANTASY || form == DanmakuForm.NEEDLE || form == DanmakuForm.FLOWER) {
                PENDING.put(key(player, contract.maid(), "wave_" + form.name()),
                        new PendingSpell(contract.maid(), player.level().dimension(), form,
                                target == null ? null : target.getUUID(),
                                level, basis, 1, now + DanmakuGrowth.waveSpacing(form), false));
            }
        }
        return true;
    }

    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        long now = player.level().getGameTime();
        if (player.tickCount % 40 == 0) DanmakuHitBudget.cleanup(now);
        var context = DanmakuEligibility.context(player);
        String prefix = player.getUUID() + ":";

        var scheduledAuto = AUTOS.entrySet().iterator();
        while (scheduledAuto.hasNext()) {
            var entry = scheduledAuto.next();
            if (!entry.getKey().startsWith(prefix)) continue;
            PendingAuto queued = entry.getValue();
            if (context == null || !queued.maid.equals(context.maid())
                    || !queued.dimension.equals(player.level().dimension())) {
                scheduledAuto.remove();
                continue;
            }
            if (now < queued.due) continue;
            scheduledAuto.remove();
            LivingEntity victim = targetById(player, queued.target);
            if (victim != null)
                DanmakuProjectilePatterns.auto(player, victim, queued.mastery,
                        queued.observedDamage, queued.climax);
        }

        var spells = PENDING.entrySet().iterator();
        while (spells.hasNext()) {
            var entry = spells.next();
            if (!entry.getKey().startsWith(prefix)) continue;
            PendingSpell wave = entry.getValue();
            if (context == null || !wave.maid.equals(context.maid())
                    || !wave.dimension.equals(player.level().dimension())
                    || ContractMasteryService.getLevel(player) < wave.form.requiredLevel()) {
                spells.remove();
                continue;
            }
            if (now < wave.due) continue;
            LivingEntity target = targetById(player, wave.target);
            DanmakuProjectilePatterns.wave(player, wave.form, target, wave.mastery,
                    wave.basis, wave.nextWave, wave.synchronizedStrike);
            if (wave.nextWave >= (wave.form == DanmakuForm.FANTASY ? 2 : 1)) {
                spells.remove();
            } else {
                entry.setValue(new PendingSpell(wave.maid, wave.dimension, wave.form, wave.target,
                        wave.mastery, wave.basis, wave.nextWave + 1,
                        now + DanmakuGrowth.waveSpacing(wave.form), wave.synchronizedStrike));
            }
        }

        GuardSession guard = GUARDS.get(player.getUUID());
        if (guard == null) return;
        if (context == null || !guard.maid.equals(context.maid())
                || !guard.dimension.equals(player.level().dimension())
                || ContractMasteryService.getLevel(player) < DanmakuForm.GUARD.requiredLevel()) {
            GUARDS.remove(player.getUUID());
            return;
        }
        if (now >= guard.expires || guard.remaining <= 0) {
            GUARDS.remove(player.getUUID());
            LivingEntity retaliation = targetById(player, guard.aggressor);
            if (retaliation == null) retaliation = acquireTarget(player);
            DanmakuProjectilePatterns.guardCounter(player, retaliation, guard.mastery, guard.attack);
            return;
        }
        if (player.tickCount % 2 != 0) return;
        // Prevent indiscriminate projectile deletion: intercept only living hostile
        // shooters, never the player's projectiles, maid shots or ownerless effects.
        AABB zone = player.getBoundingBox().inflate(3.25);
        List<Projectile> threats = player.level().getEntitiesOfClass(Projectile.class, zone,
                projectile -> !projectile.isRemoved()
                        && projectile.getDeltaMovement().lengthSqr() > .0025
                        && projectile.getOwner() instanceof LivingEntity shooter
                        && ContractDanmakuProjectile.safeTarget(player, shooter));
        for (Projectile shot : threats) {
            if (guard.remaining <= 0) break;
            Entity origin = shot.getOwner();
            guard.aggressor = origin.getUUID();
            shot.discard();
            guard.remaining--;
            ((ServerLevel) player.level()).sendParticles(ParticleTypes.ENCHANT,
                    shot.getX(), shot.getY(), shot.getZ(), 5, .15, .15, .15, .08);
        }
        if (player.tickCount % 6 == 0) {
            double theta = now * .13;
            ((ServerLevel) player.level()).sendParticles(ParticleTypes.ENCHANT,
                    player.getX() + Math.cos(theta) * 2,
                    player.getY() + 1.2, player.getZ() + Math.sin(theta) * 2,
                    2, .18, .30, .18, .015);
        }
    }

    private static LivingEntity targetById(ServerPlayer player, UUID id) {
        if (id == null) return null;
        Entity entity = player.serverLevel().getEntity(id);
        return entity instanceof LivingEntity living
                && ContractDanmakuProjectile.safeTarget(player, living) ? living : null;
    }
    private static LivingEntity acquireTarget(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (var candidate : player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(24),
                victim -> ContractDanmakuProjectile.safeTarget(player, victim))) {
            Vec3 difference = candidate.getEyePosition().subtract(eye);
            double distance = difference.length();
            if (distance < .001 || distance > 24 || !player.hasLineOfSight(candidate)) continue;
            double dot = look.dot(difference.scale(1 / distance));
            if (dot < .72) continue;
            double score = (1 - dot) * 30 + distance * .15;
            if (score < bestScore) { best = candidate; bestScore = score; }
        }
        return best;
    }
    private static boolean ready(String key, long now) {
        Long end = COOLDOWNS.get(key);
        return end == null || now >= end;
    }
    private static String key(ServerPlayer player, String maid, String suffix) {
        return player.getUUID() + ":" + maid + ":" + suffix;
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        String prefix = id + ":";
        COOLDOWNS.keySet().removeIf(k -> k.startsWith(prefix));
        CHAINS.keySet().removeIf(k -> k.startsWith(prefix));
        AUTOS.keySet().removeIf(k -> k.startsWith(prefix));
        PENDING.keySet().removeIf(k -> k.startsWith(prefix));
        GUARDS.remove(id);
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) {
        COOLDOWNS.clear(); CHAINS.clear(); AUTOS.clear(); PENDING.clear(); GUARDS.clear();
        DanmakuHitBudget.clear();
    }
    private DanmakuCombatService() { }
}
