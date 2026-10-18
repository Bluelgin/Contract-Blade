package com.maidweapon.forge.system.danmaku;

import com.maidweapon.forge.system.mastery.ContractMasteryService;
import com.maidweapon.forge.compat.tlm.danmaku.ContractDanmakuProjectile;
import com.maidweapon.forge.compat.tlm.danmaku.DanmakuProjectilePatterns;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Independent ordinary-maid collaboration; no intrinsic spirit or resonance payment. */
@Mod.EventBusSubscriber
public final class DanmakuCombatService {
    private static final Map<String, Long> COOLDOWNS = new HashMap<>();
    private record RecentHit(UUID target, long tick) { }
    private record PendingFantasy(String maid, ResourceKey<Level> dimension,
                                  int mastery, float basis, int nextWave, long due) { }
    private static final Map<String, RecentHit> RECENT_HITS = new HashMap<>();
    private static final Map<String, PendingFantasy> PENDING = new HashMap<>();

    @SubscribeEvent public static void afterMelee(LivingDamageEvent event) {
        if (!(event.getSource().getDirectEntity() instanceof ServerPlayer player)
                || event.getAmount() <= 0 || player.level().isClientSide) return;
        LivingEntity victim = event.getEntity();
        var contract = DanmakuEligibility.context(player);
        if (contract == null || !ContractDanmakuProjectile.safeTarget(player, victim)) return;
        int mastery = ContractMasteryService.getLevel(player);
        String key = key(player, contract.maid(), "auto");
        long now = player.level().getGameTime();
        String recentKey = key(player, contract.maid(), "recent_hit");
        RecentHit previous = RECENT_HITS.put(recentKey, new RecentHit(victim.getUUID(), now));
        boolean consecutive = previous != null && previous.target().equals(victim.getUUID())
                && now >= previous.tick() && now - previous.tick() <= 100;
        if (!ready(key, now)) return;
        COOLDOWNS.put(key, now + DanmakuGrowth.auto(mastery).cooldownTicks());
        DanmakuProjectilePatterns.auto(player, victim, mastery, event.getAmount(), consecutive);
    }

    /** Returns false for invalid packet, missing bauble, level gate, or active cooldown. */
    public static boolean cast(ServerPlayer player, DanmakuForm form) {
        if (form == null) return false;
        var contract = DanmakuEligibility.context(player);
        if (contract == null) return false;
        int mastery = ContractMasteryService.getLevel(player);
        if (!form.unlocked(mastery)) return false;
        long now = player.level().getGameTime();
        String key = key(player, contract.maid(), "spell_" + form.name());
        if (!ready(key, now)) return false;
        // Deliberately NO ResonanceService.pay in this PR. A future costing policy
        // can be applied here without touching projectile/selection code.
        LivingEntity target = form == DanmakuForm.GUARD ? null : acquireTarget(player);
        float basis = (float) Math.max(1, player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        COOLDOWNS.put(key, now + form.cooldownTicks());
        DanmakuProjectilePatterns.cast(player, form, target, mastery, basis);
        if (form == DanmakuForm.FANTASY)
            PENDING.put(key(player, contract.maid(), "fantasy_waves"),
                    new PendingFantasy(contract.maid(), player.level().dimension(), mastery, basis, 1, now + 8));
        return true;
    }

    /** Three discrete waves, cancelled immediately if the weapon, bauble or maid changes. */
    @SubscribeEvent public static void pendingWave(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        long now = player.level().getGameTime();
        String prefix = player.getUUID() + ":";
        var iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!entry.getKey().startsWith(prefix)) continue;
            PendingFantasy pending = entry.getValue();
            if (now < pending.due()) continue;
            var context = DanmakuEligibility.context(player);
            if (context == null || !pending.maid().equals(context.maid())
                    || !pending.dimension().equals(player.level().dimension())) {
                iterator.remove();
                continue;
            }
            DanmakuProjectilePatterns.fantasyWave(player, pending.nextWave(), pending.mastery(), pending.basis());
            if (pending.nextWave() >= 2) iterator.remove();
            else entry.setValue(new PendingFantasy(pending.maid(), pending.dimension(), pending.mastery(), pending.basis(),
                    pending.nextWave() + 1, now + 8));
        }
    }

    private static LivingEntity acquireTarget(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        var world = player.serverLevel();
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        AABB bounds = player.getBoundingBox().inflate(24);
        for (LivingEntity candidate : world.getEntitiesOfClass(LivingEntity.class, bounds,
                victim -> ContractDanmakuProjectile.safeTarget(player, victim))) {
            Vec3 diff = candidate.getEyePosition().subtract(eye);
            double dist = diff.length();
            if (dist < .001 || dist > 24) continue;
            double dot = look.dot(diff.scale(1 / dist));
            if (dot < .72) continue;
            double score = (1 - dot) * 30 + dist * .15;
            if (score < bestScore) { bestScore = score; best = candidate; }
        }
        return best;
    }
    private static boolean ready(String key, long time) {
        Long deadline = COOLDOWNS.get(key);
        return deadline == null || time >= deadline;
    }
    private static String key(ServerPlayer player, String maid, String ability) {
        return player.getUUID() + ":" + maid + ":" + ability;
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        String prefix = event.getEntity().getUUID() + ":";
        COOLDOWNS.keySet().removeIf(key -> key.startsWith(prefix));
        RECENT_HITS.keySet().removeIf(key -> key.startsWith(prefix));
        PENDING.keySet().removeIf(key -> key.startsWith(prefix));
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        COOLDOWNS.clear(); RECENT_HITS.clear(); PENDING.clear();
    }
    private DanmakuCombatService() { }
}
