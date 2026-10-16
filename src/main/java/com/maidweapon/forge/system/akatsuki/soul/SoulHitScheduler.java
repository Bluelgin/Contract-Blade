package com.maidweapon.forge.system.akatsuki.soul;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Frequency/concurrency protection, not an HP-budget ledger. Only the separate
 * verified recovery ledger can create or consume a damage-debt entitlement.
 * Target-wide clocks survive domain reopening and weapon changes.
 */
public final class SoulHitScheduler {
  private static final int MAX_TARGETS = 4096;
  private static final int RESONANCE_GAP = 10;
  private static final int PASSIVE_YIELD = 6;
  private static final int BURST_DURATION = 32;
  private static final int RETENTION = 400;

  private static final class Window {
    long lastCut = Long.MIN_VALUE / 2;
    long lastResonance = Long.MIN_VALUE / 2;
    long passiveHoldUntil, burstReady, burstUntil, touched;
  }
  private final Map<UUID, Window> targets = new HashMap<>();

  /** Minecraft's float API is a representation boundary, never a gameplay HP cap. */
  private static boolean valid(UUID owner, UUID target, long now, double request) {
    return owner != null && target != null && now >= 0
        && Double.isFinite(request) && request >= .01 && request <= Float.MAX_VALUE;
  }

  private Window window(UUID target, long now) {
    var w = targets.get(target);
    if (w != null) { w.touched = now; return w; }
    if (targets.size() >= MAX_TARGETS) return null;
    w = new Window();
    w.touched = now;
    targets.put(target, w);
    return w;
  }

  public double reserveCut(UUID owner, UUID target, long now, double request, int gapTicks) {
    if (!valid(owner, target, now, request) || gapTicks < 1) return 0;
    var w = window(target, now);
    if (w == null || now < w.burstUntil || now < w.passiveHoldUntil
        || now - w.lastCut < gapTicks) return 0;
    w.lastCut = now;
    return request;
  }

  /** Active native hits outrank ambient pulses, without competing for HP quota. */
  public double reserveResonance(UUID owner, UUID target, long now, double request) {
    if (!valid(owner, target, now, request)) return 0;
    var w = window(target, now);
    if (w == null || now < w.burstUntil || now - w.lastResonance < RESONANCE_GAP) return 0;
    w.lastResonance = now;
    w.passiveHoldUntil = Math.max(w.passiveHoldUntil, now + PASSIVE_YIELD);
    return request;
  }

  /** A precise core hit outranks both other attacks but cannot restart its own burst. */
  public double reserveBurst(UUID owner, UUID target, long now, double request, int cooldownTicks) {
    if (!valid(owner, target, now, request) || cooldownTicks < 1) return 0;
    var w = window(target, now);
    if (w == null || now < w.burstReady || now < w.burstUntil) return 0;
    w.burstReady = now + cooldownTicks;
    w.burstUntil = now + BURST_DURATION;
    return request;
  }

  public boolean burstReady(UUID target, long now) {
    var w = targets.get(target);
    return w == null || now >= w.burstReady && now >= w.burstUntil;
  }

  public void finishBurst(UUID target, long now) {
    var w = targets.get(target);
    if (w != null) w.burstUntil = Math.min(w.burstUntil, now);
  }

  public void prune(long now) {
    targets.values().removeIf(w -> now - w.touched >= RETENTION
        && now >= w.burstReady && now >= w.burstUntil);
  }
  public void clear() { targets.clear(); }
}
