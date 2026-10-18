package com.maidweapon.forge.compat.tlm.danmaku;

import com.github.tartaricacid.touhoulittlemaid.entity.projectile.DanmakuColor;
import com.github.tartaricacid.touhoulittlemaid.entity.projectile.DanmakuType;
import com.maidweapon.forge.system.danmaku.DanmakuEligibility;
import com.maidweapon.forge.system.danmaku.DanmakuPerformanceBudget;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/**
 * Server-only staggered TLM shots. Two-tick spacing makes independent damage
 * meaningful without global iframe changes or frame-time burst spam.
 * All deferred shots re-check the *original* maid authorization before spawning.
 */
@Mod.EventBusSubscriber
public final class DanmakuShotQueue {
    private record Shot(String maid, ResourceKey<Level> dimension, long tick,
                        Vec3 position, Vec3 direction, float speed,
                        DanmakuColor color, DanmakuType shape, UUID volley,
                        UUID target, int homingTicks) {}
    private static final Map<UUID, List<Shot>> QUEUED = new HashMap<>();
    private static int pendingGlobal;

    public static int pending(ServerPlayer player) {
        return QUEUED.getOrDefault(player.getUUID(), List.of()).size();
    }

    public static boolean canCast(ServerPlayer player, int count) {
        if (pendingGlobal + count > DanmakuPerformanceBudget.MAX_QUEUED_GLOBAL) return false;
        int active = countActive(player);
        return DanmakuPerformanceBudget.capacity(active, pending(player), count);
    }

    public static void schedule(ServerPlayer owner, Vec3 position, Vec3 direction,
                                float speed, DanmakuColor color, DanmakuType type,
                                UUID volley, LivingEntity target, int homingTicks,
                                int delay) {
        if (owner.level().isClientSide || volley == null || !Float.isFinite(speed)
                || speed <= 0 || direction.lengthSqr() < .00001 || delay < 0 || delay > 80) return;
        var context = DanmakuEligibility.context(owner);
        if (context == null || pending(owner) >= DanmakuPerformanceBudget.MAX_QUEUED_PER_OWNER
                || pendingGlobal >= DanmakuPerformanceBudget.MAX_QUEUED_GLOBAL) return;
        var shot = new Shot(context.maid(), owner.level().dimension(),
                owner.level().getGameTime() + delay, position, direction.normalize(),
                speed, color, type, volley, target == null ? null : target.getUUID(),
                Math.max(0, homingTicks));
        if (delay == 0) { fire(owner, shot, context.maid()); return; }
        QUEUED.computeIfAbsent(owner.getUUID(), k -> new ArrayList<>()).add(shot);
        pendingGlobal++;
    }

    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer owner)) return;
        List<Shot> waiting = QUEUED.get(owner.getUUID());
        if (waiting == null) return;
        var authorization = DanmakuEligibility.context(owner);
        String maid = authorization == null ? null : authorization.maid();
        long now = owner.level().getGameTime();
        int fired = 0;
        Iterator<Shot> iterator = waiting.iterator();
        while (iterator.hasNext()) {
            Shot shot = iterator.next();
            if (maid == null || !maid.equals(shot.maid)
                    || !owner.level().dimension().equals(shot.dimension)) {
                iterator.remove();
                pendingGlobal--; continue;
            }
            if (now < shot.tick) continue;
            if (fired >= 8) break;
            iterator.remove();
            pendingGlobal--;
            fire(owner, shot, maid);
            fired++;
        }
        if (waiting.isEmpty()) QUEUED.remove(owner.getUUID());
    }

    private static void fire(ServerPlayer owner, Shot shot, String maid) {
        if (!owner.isAlive() || !owner.level().dimension().equals(shot.dimension)
                || countActive(owner) >= DanmakuPerformanceBudget.MAX_ACTIVE_PER_OWNER) return;
        var context = DanmakuEligibility.context(owner);
        if (context == null || !maid.equals(context.maid())) return;
        var bullet = new ContractDanmakuProjectile(owner.serverLevel(), owner);
        bullet.setPos(shot.position.x, shot.position.y, shot.position.z);
        bullet.setColor(shot.color);
        bullet.setDanmakuType(shot.shape);
        bullet.setDamage(0);
        bullet.setVolley(shot.volley);
        bullet.setGravityVelocity(0);
        bullet.shoot(shot.direction.x, shot.direction.y, shot.direction.z, shot.speed, 0);
        if (shot.target != null && shot.homingTicks > 0)
            bullet.seek(shot.target, shot.homingTicks);
        owner.serverLevel().addFreshEntity(bullet);
    }

    private static int countActive(ServerPlayer owner) {
        return owner.serverLevel().getEntitiesOfClass(ContractDanmakuProjectile.class,
                owner.getBoundingBox().inflate(48),
                p -> owner.equals(p.getOwner()) && !p.isRemoved()).size();
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        remove(event.getEntity().getUUID());
    }
    public static void remove(UUID owner) {
        List<Shot> pending = QUEUED.remove(owner);
        if (pending != null) pendingGlobal -= pending.size();
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        QUEUED.clear();
        pendingGlobal = 0;
    }
    private DanmakuShotQueue() {}
}
