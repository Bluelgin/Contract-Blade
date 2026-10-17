package com.maidweapon.forge.system.akatsuki.soul;

import com.maidweapon.forge.entity.SoulAnchorEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server-only, fail-closed three-star chain. No damage is issued until three
 * distinct, ordered native sword hits AND a final deliberate central hit.
 * The client's progress is descriptive only; it cannot authorize stages.
 */
public final class SoulEclipseChallenge {
  public static final int FIRST_TICKS = 48, NEXT_TICKS = 18, FINAL_TICKS = 16;
  private static final int FAIL_FADE_TICKS = 10;

  public static void prepare(ServerPlayer owner, SoulAnchorEntity core, long now) {
    if (!core.eclipse() || core.chainDeadline != 0) return;
    core.eclipseYaw(owner.getYRot());
    core.chainProgress(0);
    core.chainDeadline = now + FIRST_TICKS;
    core.expires = Math.max(core.expires, now + 112);
  }

  public static boolean hit(ServerPlayer owner, SoulAnchorEntity core, int star) {
    if (!core.eclipse() || !owner.getUUID().equals(core.owner)
        || !SoulAnchorBinding.valid(core)
        || core.chainDeadline == 0 || core.chainProgress() < 0) return false;
    long now = SoulDamageEngine.now(owner);
    if (now > core.chainDeadline) {
      fail(owner, core, "deadline", now);
      return false;
    }
    var entity = owner.serverLevel().getEntity(core.original);
    if (!(entity instanceof LivingEntity target)
        || SoulDamageDebtService.available(owner, target, true) <= .01) {
      fail(owner, core, "no_recoverable_receipt", now);
      return false;
    }
    int current = core.chainProgress();
    // The fourth strike is the moon; early moon or incorrect/repeated star fails.
    int expected = current == SoulEclipseGeometry.STARS ? -1 : current;
    if (star != expected) {
      fail(owner, core, "wrong_node_" + star + "_expected_" + expected, now);
      return false;
    }
    if (star == -1) {
      if (!SoulBurstService.strike(owner, core)) {
        fail(owner, core, "burst_reservation_rejected", now);
        return false;
      }
      core.shake();
      return true;
    }
    core.chainProgress(current + 1);
    core.chainDeadline = now + (current + 1 == SoulEclipseGeometry.STARS
        ? FINAL_TICKS : NEXT_TICKS);
    core.shake();
    if (com.maidweapon.common.SoulDamageConfig.DIAGNOSTICS.get())
      com.mojang.logging.LogUtils.getLogger().info(
          "SOUL_ECLIPSE_STEP owner={} core={} progress={} deadline={}",
          owner.getUUID(), core.getId(), core.chainProgress(), core.chainDeadline);
    return true;
  }

  public static boolean expired(ServerPlayer owner, SoulAnchorEntity core, long now) {
    if (!core.eclipse() || core.chainDeadline == 0 || core.chainProgress() < 0
        || core.burstUntil > 0) return false;
    if (now <= core.chainDeadline) return false;
    fail(owner, core, "timeout", now);
    return true;
  }

  public static void fail(ServerPlayer owner, SoulAnchorEntity core, String reason, long now) {
    if (core.chainProgress() < 0 || core.burstUntil > 0) return;
    var target = owner.serverLevel().getEntity(core.original);
    if (target instanceof LivingEntity living)
      SoulDamageDebtService.forfeitAerial(owner, living);
    core.chainProgress(-1);
    core.chainDeadline = now + FAIL_FADE_TICKS;
    core.expires = Math.max(core.expires, core.chainDeadline + 1);
    if (com.maidweapon.common.SoulDamageConfig.DIAGNOSTICS.get())
      com.mojang.logging.LogUtils.getLogger().info(
          "SOUL_ECLIPSE_FAILED owner={} core={} reason={}",
          owner.getUUID(), core.getId(), reason);
  }

  private SoulEclipseChallenge() {}
}
