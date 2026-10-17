package com.maidweapon.forge.system.akatsuki.soul;

import com.maidweapon.common.SoulDamageConfig;
import com.maidweapon.forge.entity.SoulAnchorEntity;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Verified recovery unlocks on five distinct native summoned-sword contacts
 * with the central core. Orbital stars are rendering only.
 */
public final class SoulEclipseChallenge {
  public static final int WINDOW_TICKS = SoulEclipseHitSequence.WINDOW_TICKS;
  private static final int FAIL_FADE_TICKS = 10;
  private static final Map<SoulAnchorEntity, SoulEclipseHitSequence> ACTIVE = new WeakHashMap<>();

  public static void prepare(ServerPlayer owner, SoulAnchorEntity core, long now) {
    if (!core.eclipse() || core.chainDeadline != 0) return;
    var sequence = new SoulEclipseHitSequence(now);
    ACTIVE.put(core, sequence);
    core.chainProgress(0);
    core.chainDeadline = sequence.deadline();
    core.expires = Math.max(core.expires, core.chainDeadline + 33);
  }

  public static boolean swordHit(ServerPlayer owner, SoulAnchorEntity core, UUID swordId) {
    if (!core.eclipse() || !owner.getUUID().equals(core.owner)
        || !SoulAnchorBinding.valid(core) || core.chainProgress() < 0
        || core.burstUntil > 0) return false;
    long now = SoulDamageEngine.now(owner);
    var seq = ACTIVE.get(core);
    if (seq == null) return false;
    var entity = owner.serverLevel().getEntity(core.original);
    if (!(entity instanceof LivingEntity target)
        || SoulDamageDebtService.available(owner, target, true) <= .01) {
      fail(owner, core, "no_recoverable_receipt", now);
      return false;
    }
    var result = seq.hit(swordId, now);
    switch (result) {
      case EXPIRED -> {
        fail(owner, core, "timeout", now);
        return false;
      }
      case DUPLICATE, TOO_FAST, CLOSED -> {
        return false;
      }
      case ADVANCED -> {
        core.chainProgress(seq.hits());
        core.shake();
        if (SoulDamageConfig.DIAGNOSTICS.get())
          com.mojang.logging.LogUtils.getLogger().info(
              "SOUL_ECLIPSE_HIT owner={} core={} hits={}/{}",
              owner.getUUID(), core.getId(), seq.hits(), SoulEclipseHitSequence.REQUIRED);
        return true;
      }
      case COMPLETE -> {
        core.chainProgress(seq.hits());
        ACTIVE.remove(core);
        if (!SoulBurstService.strike(owner, core)) {
          fail(owner, core, "burst_reservation_rejected", now);
          return false;
        }
        core.shake();
        return true;
      }
    }
    return false;
  }

  public static boolean expired(ServerPlayer owner, SoulAnchorEntity core, long now) {
    if (!core.eclipse() || core.chainDeadline == 0
        || core.chainProgress() < 0 || core.burstUntil > 0
        || now <= core.chainDeadline) return false;
    fail(owner, core, "timeout", now);
    return true;
  }

  public static void fail(ServerPlayer owner, SoulAnchorEntity core, String reason, long now) {
    if (core.chainProgress() < 0 || core.burstUntil > 0) return;
    ACTIVE.remove(core);
    var target = owner.serverLevel().getEntity(core.original);
    if (target instanceof LivingEntity living)
      SoulDamageDebtService.forfeitAerial(owner, living);
    core.chainProgress(-1);
    core.chainDeadline = now + FAIL_FADE_TICKS;
    core.expires = Math.max(core.expires, core.chainDeadline + 1);
    if (SoulDamageConfig.DIAGNOSTICS.get())
      com.mojang.logging.LogUtils.getLogger().info(
          "SOUL_ECLIPSE_FAILED owner={} core={} reason={}",
          owner.getUUID(), core.getId(), reason);
  }

  public static void clear() {
    ACTIVE.clear();
  }

  private SoulEclipseChallenge() {}
}
