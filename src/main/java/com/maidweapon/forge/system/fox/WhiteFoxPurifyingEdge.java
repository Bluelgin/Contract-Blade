package com.maidweapon.forge.system.fox;

import com.maidweapon.forge.api.BlackFoxEncounterApi;
import com.maidweapon.forge.compat.WhiteFoxSpecialEffectCompat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/** Target-scoped, temporary strength borrowing. Never rewrites a blade or copies another effect. */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class WhiteFoxPurifyingEdge {
    public static final double MAX_HEALTH_FRACTION = 0.01;
    public static final int INTERVAL_TICKS = 20;
    private record StrengthSnapshot(long tick, double strength) { }
    private static final Map<ServerPlayer, StrengthSnapshot> STRENGTH = new WeakHashMap<>();
    private static final Map<LivingEntity, Long> LAST_PURIFICATION = new WeakHashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void hurt(LivingHurtEvent event) {
        var context = eligible(event.getSource(), event.getEntity(), event.getAmount());
        if (context == null) return;
        double own = WhiteFoxSpecialEffectCompat.strength(context.originalBlade());
        double supplement = Math.max(0, strongest(context.owner()) - own);
        // A flat base/refine supplement, before armor: never multiplies enchantments or SA/SE damage.
        event.setAmount((float) Math.min(Float.MAX_VALUE, event.getAmount() + supplement));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damage(LivingDamageEvent event) {
        var context = eligible(event.getSource(), event.getEntity(), event.getAmount());
        if (context == null) return;
        float bonus = purification(event.getEntity());
        // Add to this successful hit; no recursive hurt(), invulnerability reset or second damage source.
        event.setAmount(event.getAmount() + bonus);
    }

    private static BlackFoxEncounterApi.HitContext eligible(
            net.minecraft.world.damagesource.DamageSource source, LivingEntity target, float damage) {
        if (target.level().isClientSide || !target.isAlive() || !(damage > 0) || !Float.isFinite(damage)) return null;
        var context = BlackFoxEncounterApi.resolve(source, target);
        if (context == null || context.owner() == null || context.originalBlade() == null
                || !context.owner().isAlive() || context.owner().level() != target.level()
                || !WhiteFoxSpecialEffectCompat.hasEffect(context.originalBlade())
                || WhiteFoxSpecialEffectCompat.strength(context.originalBlade()) <= 0) return null;
        return context;
    }

    public static double strongest(ServerPlayer player) {
        long now = player.level().getGameTime();
        var cached = STRENGTH.get(player);
        if (cached != null && now >= cached.tick() && now - cached.tick() < INTERVAL_TICKS)
            return cached.strength();
        double best = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack candidate = player.getInventory().getItem(slot);
            if (WhiteFoxSpecialEffectCompat.isShrineBlade(candidate)) continue;
            best = Math.max(best, WhiteFoxSpecialEffectCompat.strength(candidate));
        }
        STRENGTH.put(player, new StrengthSnapshot(now, best));
        return best;
    }

    private static float purification(LivingEntity target) {
        long now = target.level().getGameTime();
        Long last = LAST_PURIFICATION.get(target);
        if (last != null && now >= last && now - last < INTERVAL_TICKS) return 0;
        LAST_PURIFICATION.put(target, now);
        return (float) (target.getMaxHealth() * MAX_HEALTH_FRACTION);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        STRENGTH.clear();
        LAST_PURIFICATION.clear();
    }

    private WhiteFoxPurifyingEdge() { }
}
