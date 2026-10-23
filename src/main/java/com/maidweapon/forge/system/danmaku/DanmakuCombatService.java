package com.maidweapon.forge.system.danmaku;

import com.maidweapon.forge.compat.tlm.danmaku.ContractDanmakuProjectile;
import com.maidweapon.forge.compat.tlm.danmaku.DanmakuProjectilePatterns;
import com.maidweapon.forge.compat.tlm.danmaku.DanmakuShotQueue;
import com.maidweapon.forge.system.mastery.ContractMasteryService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.Locale;
import java.util.UUID;
import static com.maidweapon.forge.system.danmaku.DanmakuTargeting.panel;
import static com.maidweapon.forge.system.danmaku.DanmakuTargeting.acquireTarget;

/** Ordinary-maid support coordinator; all actual damage still requires projectile collision. */
@Mod.EventBusSubscriber
public final class DanmakuCombatService {
    private static final java.util.Map<UUID, String> VISUAL_BINDINGS = new java.util.HashMap<>();
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

        DanmakuWaveSessions.empowerFinale(player, contract.maid(), victim);
        DanmakuWaveSessions.queueAuto(player, contract.maid(), victim, panel(player), mastery, now);
    }

    /** Actual vanilla left click is observed before hurt(), including blocked damage. */
    @SubscribeEvent public static void attackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof LivingEntity victim)
                || player.level().isClientSide
                || !ContractDanmakuProjectile.safeTarget(player, victim)) return;
        var context = DanmakuEligibility.context(player);
        if (context == null) return;
        long now = player.level().getGameTime();
        int mastery = ContractMasteryService.getLevel(player);
        DanmakuWaveSessions.queueAuto(player, context.maid(), victim,
                panel(player), mastery, now);
    }

    /** Optional empty-air normal attack from a client left-click packet. */
    public static void airSwing(ServerPlayer player) {
        if (player == null || !player.isAlive()) return;
        var context = DanmakuEligibility.context(player);
        if (context == null) return;
        long now = player.level().getGameTime();
        if (!DanmakuInputGate.attackReady(player.getUUID(), now)) return;
        // Air swings can fire forward, but still obey exactly the same entity budget.
        int mastery = ContractMasteryService.getLevel(player);
        if (!DanmakuShotQueue.canCast(player, DanmakuGrowth.auto(mastery).count())) return;
        DanmakuInputGate.markAttack(player.getUUID(), now);
        DanmakuProjectilePatterns.auto(player, null, mastery,
                panel(player), false);
    }

    /** Cast packets only indicate a form. Every right is re-established on the server. */
    public static boolean cast(ServerPlayer player, DanmakuForm form) {
        if (form == null) return false;
        var contract = DanmakuEligibility.context(player);
        if (contract == null) return false;
        int level = ContractMasteryService.getLevel(player);
        if (!form.unlocked(level)) return false;
        long now = player.level().getGameTime();
        if (!DanmakuInputGate.castReady(player, contract.maid(), form, now)) return false;
        if (form == DanmakuForm.GUARD && DanmakuGuardService.active(player, now)) return false;
        if (DanmakuWaveSessions.pendingCount(player) >= 6 || DanmakuGuardService.overBudget()) return false;
        if (form != DanmakuForm.GUARD && !DanmakuShotQueue.canCast(player,
                DanmakuGrowth.maxProjectilesPerCast(form, level))) return false;
        // Charge the verified player/maid contract exactly once BEFORE any effect.
        if (!DanmakuResonanceCostPolicy.charge(player, player.getMainHandItem(),
                contract.binding(), form)) return false;
        DanmakuInputGate.markCast(player, contract.maid(), form, now);
        float basis = panel(player);
        LivingEntity target = acquireTarget(player);

        if (form == DanmakuForm.GUARD) {
            DanmakuGuardService.start(player, contract.maid(), level, basis, now);
        } else {
            DanmakuProjectilePatterns.wave(player, form, target, level, basis, 0, false);
            if (form == DanmakuForm.FANTASY || form == DanmakuForm.NEEDLE || form == DanmakuForm.FLOWER) {
                DanmakuWaveSessions.scheduleWave(player, contract.maid(), form, target, level, basis, now);
            }
        }
        DanmakuSpellEffects.cast(player, form, target);
        com.maidweapon.forge.network.DanmakuNetwork.declare(player, form);
        return true;
    }

    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        long now = player.level().getGameTime();
        if (player.tickCount % 40 == 0) {
            DanmakuHitBudget.cleanup(now);
            DanmakuHitPolicy.cleanup(now);
        }
        var context = DanmakuEligibility.context(player);
        String binding = context == null ? "" : context.maid();
        // Push eligibility changes on the same server tick; sync deduplicates packets.
        com.maidweapon.forge.network.DanmakuNetwork.sync(player,
                context == null ? "" : context.binding(), context != null,
                ContractMasteryService.getLevel(player));
        String previous = VISUAL_BINDINGS.put(player.getUUID(), binding);
        if (previous != null && !previous.equals(binding))
            DanmakuWorldEvents.send(player, com.maidweapon.forge.api.danmaku.DanmakuVisualEvent.Kind.CANCEL,
                    0, 0, player, player.position());
        DanmakuWaveSessions.tick(player, context == null ? null : context.maid(), now);
        DanmakuGuardService.tick(player, context == null ? null : context.maid(), now);
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        VISUAL_BINDINGS.remove(id);
        DanmakuInputGate.forget(id);
        DanmakuWaveSessions.forget(id);
        DanmakuGuardService.forget(id);
        DanmakuShotQueue.remove(id);
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) {
        VISUAL_BINDINGS.clear();
        DanmakuInputGate.clear();
        DanmakuWaveSessions.clear();
        DanmakuGuardService.clear();
        DanmakuHitBudget.clear();
        DanmakuHitPolicy.clear();
    }
    private DanmakuCombatService() { }
}
