package com.maidweapon.forge.system.danmaku;

import com.maidweapon.forge.system.mastery.ContractMasteryService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Independent ordinary-maid collaboration; no intrinsic spirit or resonance payment. */
@Mod.EventBusSubscriber
public final class DanmakuCombatService {
    private static final Map<String, Long> COOLDOWNS = new HashMap<>();

    @SubscribeEvent public static void afterMelee(LivingDamageEvent event) {
        if (!(event.getSource().getDirectEntity() instanceof ServerPlayer player)
                || event.getAmount() <= 0 || player.level().isClientSide) return;
        LivingEntity victim = event.getEntity();
        if (!ContractDanmakuProjectile.safeTarget(player, victim)) return;
        var contract = DanmakuEligibility.context(player);
        if (contract == null) return;
        int mastery = ContractMasteryService.getLevel(player);
        String key = key(player, contract.maid(), "auto");
        long now = player.level().getGameTime();
        if (!ready(key, now)) return;
        COOLDOWNS.put(key, now + DanmakuGrowth.auto(mastery).cooldownTicks());
        DanmakuProjectilePatterns.auto(player, victim, mastery, event.getAmount());
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
        return true;
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
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { COOLDOWNS.clear(); }
    private DanmakuCombatService() { }
}
