package com.maidweapon.forge.system.oath;

import com.maidweapon.common.OathConfig;
import com.maidweapon.forge.event.MaidBondCombatHandler;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Changes the existing event amount once. No secondary hurt call, armor rewrite or cap bypass. */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class OathCombat {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void invulnerable(LivingAttackEvent e) {
        if (!e.getEntity().level().isClientSide && OathService.empowered(e.getEntity())) e.setCanceled(true);
    }
    /** The actual causing entity is authoritative for player-wide buffs, independent of today's hand.
     * Native projectile ownership must agree; unknown/environmental causes fail closed.
     */
    public static Entity actor(DamageSource source) {
        Entity causing = source.getEntity(), direct = source.getDirectEntity();
        if (direct instanceof Projectile shot && shot.getOwner() != causing) return null;
        if (causing instanceof Player || causing instanceof TamableAnimal) return causing;
        var proof = com.maidweapon.forge.system.mastery.ContractCombatCredits.resolveHit(source);
        return proof == null ? null : proof.player();
    }
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void hurt(LivingHurtEvent e) {
        if (e.getEntity().level().isClientSide || e.getAmount() <= 0) return;
        if (OathService.empowered(e.getEntity())) { e.setCanceled(true); return; }
        // Soul debt/critical settlements already derive from original damage. Never compound them.
        boolean settlement = com.maidweapon.forge.system.akatsuki.soul.SoulDamageEngine.applying();
        double multiplier = 1;
        Entity attacker = actor(e.getSource());
        if (!settlement && attacker instanceof Player player) {
            if (OathService.active(player)) {
                multiplier *= 1 + OathConfig.PLAYER_DAMAGE.get();
                if (MaidBondCombatHandler.oathCooperation(player, e.getEntity(), false)) multiplier *= 1 + OathConfig.COOP_DAMAGE.get();
            }
            var backlash = player.getEffect(OathEffects.BACKLASH.get());
            if (backlash != null) multiplier *= Math.max(0, 1 - Math.min(.5, OathBurn.penaltyLevels(backlash.getAmplifier()) * OathConfig.BACKLASH_OUT.get()));
        } else if (!settlement && attacker instanceof TamableAnimal maid && OathService.empowered(maid)) {
            multiplier *= 1 + OathConfig.MAID_DAMAGE.get();
            if (maid.getOwner() instanceof Player owner && MaidBondCombatHandler.oathCooperation(owner, e.getEntity(), true))
                multiplier *= 1 + OathConfig.COOP_DAMAGE.get();
        }
        if (e.getEntity() instanceof Player target) {
            if (OathService.active(target)) multiplier *= 1 - OathConfig.PLAYER_DEFENSE.get();
            var backlash = target.getEffect(OathEffects.BACKLASH.get());
            if (backlash != null) multiplier *= 1 + Math.min(.5, OathBurn.penaltyLevels(backlash.getAmplifier()) * OathConfig.BACKLASH_IN.get());
        }
        double amount = e.getAmount() * multiplier;
        if (amount > e.getAmount()) {
            var limits = com.maidweapon.forge.compat.DomainTargetCompatibility.inspect(e.getEntity());
            // A rolling native budget may already have been spent before Forge's event.
            // Do not append bonus damage after that verified budget or protected phase.
            if (limits.protectedPhase() || limits.dpsCap() > 0) amount = e.getAmount();
            else if (limits.hitCap() > 0) amount = Math.min(amount, limits.hitCap());
        }
        e.setAmount((float) Math.min(Float.MAX_VALUE, amount));
    }
    private OathCombat() { }
}
