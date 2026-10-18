package com.maidweapon.forge.system.akatsuki.soul;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Five distinct native sword contacts with a 90-tick shared deadline. */
public final class SoulEclipseHitSequence {
  public static final int REQUIRED = 5, WINDOW_TICKS = 90, MIN_GAP_TICKS = 4;
  public enum Result { ADVANCED, COMPLETE, DUPLICATE, TOO_FAST, EXPIRED, CLOSED }
  private final Set<UUID> consumedSwords = new HashSet<>(REQUIRED);
  private final long deadline;
  private long lastAccepted = Long.MIN_VALUE;
  private int hits;

  public SoulEclipseHitSequence(long now) { deadline = now + WINDOW_TICKS; }
  public long deadline() { return deadline; }
  public int hits() { return hits; }
  public Result hit(UUID sword, long now) {
    if (hits >= REQUIRED) return Result.CLOSED;
    if (now > deadline) return Result.EXPIRED;
    if (sword == null || !consumedSwords.add(sword)) return Result.DUPLICATE;
    if (lastAccepted != Long.MIN_VALUE && now - lastAccepted < MIN_GAP_TICKS)
      return Result.TOO_FAST;
    lastAccepted = now;
    hits++;
    return hits == REQUIRED ? Result.COMPLETE : Result.ADVANCED;
  }
}
