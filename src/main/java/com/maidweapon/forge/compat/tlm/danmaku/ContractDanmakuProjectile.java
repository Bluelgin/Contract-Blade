package com.maidweapon.forge.compat.tlm.danmaku;

import com.maidweapon.forge.api.danmaku.DanmakuRuntimeApi;
import com.github.tartaricacid.touhoulittlemaid.entity.projectile.EntityDanmaku;
import com.maidweapon.forge.compat.tlm.TlmEntityAdapter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Native TLM visuals and ordinary damage, but with hit-based volley caps and gentle homing. */
public final class ContractDanmakuProjectile extends EntityDanmaku {
    private UUID volleyId;
    private UUID homingTarget;
    private int homingTicks;

    public ContractDanmakuProjectile(Level world, LivingEntity owner) {
        super(world, owner);
    }

    public void setVolley(UUID id) { volleyId = id; }
    public void seek(UUID target, int ticks) {
        homingTarget = target;
        homingTicks = Math.max(0, Math.min(ticks, 24));
    }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide || isRemoved() || homingTicks-- <= 0
                || homingTarget == null || !(level() instanceof ServerLevel server)) return;
        var found = server.getEntity(homingTarget);
        if (!(found instanceof LivingEntity target) || !target.isAlive()) return;
        var previous = getDeltaMovement();
        double speed = previous.length();
        Vec3 delta = target.getEyePosition().subtract(position());
        if (speed < .08 || delta.lengthSqr() < .0001) return;
        var desired = delta.normalize().scale(speed);
        setDeltaMovement(previous.scale(.83).add(desired.scale(.17)));
        hasImpulse = true;
    }

    @Override protected void onHitEntity(EntityHitResult hit) {
        if (level().isClientSide) return;
        var shooter = getOwner();
        if (!(shooter instanceof net.minecraft.server.level.ServerPlayer player)
                || !(hit.getEntity() instanceof LivingEntity victim)
                || !safeTarget(player, victim)
                || DanmakuRuntimeApi.authorizedMaid(player) == null) {
            discard();
            return;
        }

        long tick = level().getGameTime();
        if (volleyId == null || !DanmakuRuntimeApi.mayHit(victim.getUUID(), tick)) {
            discard();
            return;
        }
        float damage = DanmakuRuntimeApi.claim(volleyId, player.getUUID(), victim.getUUID(), tick);
        if (damage <= 0) { discard(); return; }
        setDamage(damage);
        // This affects only the short duration of our own native projectile hurt()
        // call, never the mob's global hurt state or other mods' incoming attacks.
        DanmakuRuntimeApi.record(victim.getUUID(), tick);
        int existingFrames = DanmakuRuntimeApi.relaxFrames(victim);
        try {
            super.onHitEntity(hit);
        } finally {
            DanmakuRuntimeApi.restoreFrames(victim, existingFrames);
        }
    }

    public static boolean safeTarget(Player owner, LivingEntity target) {
        if (target == owner || target instanceof Player || TlmEntityAdapter.isMaidEntity(target)
                || owner.isAlliedTo(target) || target.isAlliedTo(owner)) return false;
        if (target instanceof TamableAnimal tame && owner.getUUID().equals(tame.getOwnerUUID())) return false;
        return target.isAlive() && !target.isInvulnerable();
    }
}
