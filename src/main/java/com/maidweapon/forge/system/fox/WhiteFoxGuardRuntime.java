package com.maidweapon.forge.system.fox;

import java.util.EnumMap;

/** One player's ephemeral combat budget; hand changes never allocate another budget. */
public final class WhiteFoxGuardRuntime {
  public enum Action {
    GUARD,
    CLEANSE,
    HEAL,
    EDGE,
    HIT
  }

  private final EnumMap<Action, Long> until = new EnumMap<>(Action.class);
  private int hits;
  private long lastHit = Long.MIN_VALUE, moveUntil, protectionUntil;

  public boolean consume(Action action, long now, long cooldown) {
    if (now < until.getOrDefault(action, 0L)) return false;
    until.put(action, now + Math.max(1, cooldown));
    return true;
  }

  /** At most one charge per ten ticks; twenty rapid area hits are still one charge. */
  public boolean hit(long now, int required, long cooldown, boolean injured) {
    if (!consume(Action.HIT, now, 10)) return false;
    if (lastHit != Long.MIN_VALUE && (now < lastHit || now - lastHit > 100)) hits = 0;
    lastHit = now;
    hits = Math.min(required, hits + 1);
    if (!injured || hits < required || !consume(Action.HEAL, now, cooldown)) return false;
    hits = 0;
    return true;
  }

  public void move(long now, long ticks) {
    moveUntil = now + ticks;
  }

  public boolean moving(long now) {
    return now < moveUntil;
  }

  public void protect(long now, long ticks) {
    protectionUntil = now + ticks;
  }

  public boolean protectedNow(long now) {
    return now < protectionUntil;
  }

  /**
   * Lose accrued benefits on unequip/manifest, but retain cooldown receipts against hand cycling.
   */
  public void suspend() {
    hits = 0;
    lastHit = Long.MIN_VALUE;
    moveUntil = protectionUntil = 0;
  }

  public enum HitMode {
    NONE,
    WHITE,
    RESONANCE
  }

  public static HitMode hitMode(
      boolean mainWhite,
      boolean offWhite,
      boolean earnedWhiteHit,
      boolean earnedBlackHit,
      boolean resonance) {
    if (mainWhite && earnedWhiteHit) return HitMode.WHITE;
    if (offWhite && earnedBlackHit && resonance) return HitMode.RESONANCE;
    return HitMode.NONE;
  }
}
