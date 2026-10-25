package com.maidweapon.forge.system.danmaku;

import com.maidweapon.forge.system.mastery.ContractMasteryService;

import com.maidweapon.forge.compat.tlm.danmaku.DanmakuShotQueue;
import com.maidweapon.forge.compat.tlm.danmaku.DanmakuProjectilePatterns;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import java.util.*;
import static com.maidweapon.forge.system.danmaku.DanmakuTargeting.targetById;

/** Owns staggered spells and auto-combo state, not guard or network input. */
final class DanmakuWaveSessions {
    private record Chain(UUID target, int count, long time) { }
    private static final Map<String, Chain> CHAINS = new HashMap<>();
    private record PendingAuto(String maid, ResourceKey<Level> dimension, UUID target,
                               int mastery, float weaponPanel, boolean climax, long due) { }
    private static final Map<String, PendingAuto> AUTOS = new HashMap<>();
    private record PendingSpell(String maid, ResourceKey<Level> dimension, DanmakuForm form,
                                UUID target, int mastery, float basis, int nextWave,
                                long due, boolean synchronizedStrike) { }
    private static final Map<String, PendingSpell> PENDING = new HashMap<>();
    /** Idle players can test for work without scanning global wave maps. */
    private static final Map<UUID, Integer> ACTIVE_OWNERS = new HashMap<>();
    private static void queued(UUID owner) { ACTIVE_OWNERS.merge(owner, 1, Integer::sum); }
    private static void completed(UUID owner) {
        ACTIVE_OWNERS.computeIfPresent(owner, (id, count) -> count <= 1 ? null : count - 1);
    }
    static boolean hasPending(ServerPlayer player) { return ACTIVE_OWNERS.containsKey(player.getUUID()); }
    private static String key(ServerPlayer player, String maid, String suffix) {
        return player.getUUID() + ":" + maid + ":" + suffix;
    }

    static void empowerFinale(ServerPlayer player, String maid, LivingEntity victim) {
        String prefix = player.getUUID() + ":" + maid + ":";
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
    }
    static void queueAuto(ServerPlayer player, String maid, LivingEntity victim,
                                  float weaponPanel, int mastery, long now) {
        if (!DanmakuInputGate.attackReady(player.getUUID(), now)
                || AUTOS.size() >= 128) return;
        String chainKey = key(player, maid, "chain");
        Chain previous = CHAINS.get(chainKey);
        int count = previous != null && previous.target.equals(victim.getUUID())
                && now >= previous.time && now - previous.time <= 100
                ? Math.min(3, previous.count + 1) : 1;
        boolean climax = mastery >= 8 && count >= 3;
        if (!DanmakuShotQueue.canCast(player, DanmakuGrowth.auto(mastery, climax).count())) return;
        DanmakuInputGate.markAttack(player.getUUID(), now);
        CHAINS.put(chainKey, new Chain(victim.getUUID(), climax ? 0 : count, now));
        AUTOS.put(key(player, maid, "auto_" + UUID.randomUUID()),
                new PendingAuto(maid, player.level().dimension(), victim.getUUID(),
                        mastery, weaponPanel, climax, now + 2));
        queued(player.getUUID());
    }


    static long pendingCount(ServerPlayer player) {
        String prefix = player.getUUID() + ":";
        return PENDING.keySet().stream().filter(k -> k.startsWith(prefix)).count();
    }
    static void scheduleWave(ServerPlayer player, String maid, DanmakuForm form,
                             LivingEntity target, int level, float basis, long now) {
        PENDING.put(key(player, maid, "wave_" + form.name() + "_" + UUID.randomUUID()),
                new PendingSpell(maid, player.level().dimension(), form,
                        target == null ? null : target.getUUID(), level, basis, 1,
                        now + DanmakuGrowth.waveSpacing(form), false));
        queued(player.getUUID());
    }
    static void tick(ServerPlayer player, String maid, long now) {
        String prefix = player.getUUID() + ":";
        if (maid == null) CHAINS.keySet().removeIf(k -> k.startsWith(prefix));
        var scheduledAuto = AUTOS.entrySet().iterator();
        while (scheduledAuto.hasNext()) {
            var entry = scheduledAuto.next();
            if (!entry.getKey().startsWith(prefix)) continue;
            PendingAuto queued = entry.getValue();
            if (maid == null || !queued.maid.equals(maid)
                    || !queued.dimension.equals(player.level().dimension())) {
                scheduledAuto.remove();
                completed(player.getUUID());
                continue;
            }
            if (now < queued.due) continue;
            scheduledAuto.remove();
            completed(player.getUUID());
            LivingEntity victim = targetById(player, queued.target);
            if (victim != null)
                DanmakuProjectilePatterns.auto(player, victim, queued.mastery,
                        queued.weaponPanel, queued.climax);
        }

        var spells = PENDING.entrySet().iterator();
        while (spells.hasNext()) {
            var entry = spells.next();
            if (!entry.getKey().startsWith(prefix)) continue;
            PendingSpell wave = entry.getValue();
            if (maid == null || !wave.maid.equals(maid)
                    || !wave.dimension.equals(player.level().dimension())
                    || ContractMasteryService.getLevel(player) < wave.form.requiredLevel()) {
                spells.remove();
                completed(player.getUUID());
                continue;
            }
            if (now < wave.due) continue;
            LivingEntity target = targetById(player, wave.target);
            DanmakuProjectilePatterns.wave(player, wave.form, target, wave.mastery,
                    wave.basis, wave.nextWave, wave.synchronizedStrike);
            DanmakuSpellEffects.wave(player, wave.form, target, wave.nextWave);
            if (wave.nextWave + 1 >= DanmakuGrowth.waveCount(wave.form)) {
                spells.remove();
                completed(player.getUUID());
            } else {
                entry.setValue(new PendingSpell(wave.maid, wave.dimension, wave.form, wave.target,
                        wave.mastery, wave.basis, wave.nextWave + 1,
                        now + DanmakuGrowth.waveSpacing(wave.form), wave.synchronizedStrike));
            }
        }


    }
    static void forget(UUID id) {
        ACTIVE_OWNERS.remove(id);
        String prefix = id + ":";
        CHAINS.keySet().removeIf(k -> k.startsWith(prefix));
        AUTOS.keySet().removeIf(k -> k.startsWith(prefix));
        PENDING.keySet().removeIf(k -> k.startsWith(prefix));
    }
    static void clear() { CHAINS.clear(); AUTOS.clear(); PENDING.clear(); ACTIVE_OWNERS.clear(); }
    private DanmakuWaveSessions() { }
}
