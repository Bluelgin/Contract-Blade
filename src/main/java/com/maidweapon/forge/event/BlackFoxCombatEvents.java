package com.maidweapon.forge.event;

import com.maidweapon.forge.compat.BlackFoxSlashCompat;
import com.maidweapon.forge.system.fox.challenge.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class BlackFoxCombatEvents {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void phantomImpact(net.minecraftforge.event.entity.ProjectileImpactEvent event) {
        if (event.getProjectile().getOwner() instanceof BlackFoxCombatant owner
                && owner.combat().ranged().held(event.getProjectile())) {
            event.setImpactResult(net.minecraftforge.event.entity.ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
            return;
        }
        if (!(event.getProjectile().getOwner() instanceof BlackFoxCombatant boss)
                || !boss.combat().ranged().owns(event.getProjectile())
                || !(event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hit)) return;
        // Native swords normally reject all players with PvP disabled. Bypass only for this private target.
        if (hit.getEntity() instanceof ServerPlayer player && boss.challenger().equals(player.getUUID())
                && BlackFoxEncounters.registered(boss) && !boss.combat().protects(player)) {
            if (boss.combat().parryNative(player, event.getProjectile())) event.getProjectile().discard();
            else com.maidweapon.forge.compat.BlackFoxPhantomCompat.impact(event.getProjectile(), player);
        }
        event.setImpactResult(net.minecraftforge.event.entity.ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void parryProtection(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getSource().getEntity() instanceof BlackFoxCombatant boss && boss.combat().protects(player))
            event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void attack(AttackEntityEvent event) {
        if (event.getTarget() instanceof BlackFoxCombatant && event.getEntity() instanceof ServerPlayer player) {
            BlackFoxEncounters.swing(player, player.getMainHandItem(), false);
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damage(LivingDamageEvent event) {
        if (event.getEntity() instanceof BlackFoxCombatant boss && event.getAmount() > 0)
            boss.combat().landedHit(event.getAmount());
    }
    @SubscribeEvent
    public static void projectile(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && BlackFoxSlashCompat.nativeSlash(event.getEntity())
                && event.getEntity() instanceof Projectile projectile && projectile.getOwner() != null) {
            if (projectile.getOwner() instanceof BlackFoxCombatant boss) boss.combat().nativeCombo().capture(projectile);
            BlackFoxAttackTrace.capture(projectile, projectile.getOwner());
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof BlackFoxCombatant) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interactAt(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getTarget() instanceof BlackFoxCombatant) event.setCanceled(true);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) BlackFoxEncounters.stop(player);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getFrom().equals(FoxChallengeService.LEVEL))
            BlackFoxEncounters.stop(player);
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) {
        BlackFoxEncounters.copyWin(event.getOriginal(), event.getEntity());
        if (event.getOriginal() instanceof ServerPlayer player) BlackFoxEncounters.stop(player);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { BlackFoxEncounters.clear(event.getServer()); }
    private BlackFoxCombatEvents() { }
}
