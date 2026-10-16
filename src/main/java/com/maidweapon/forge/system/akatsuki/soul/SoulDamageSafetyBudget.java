package com.maidweapon.forge.system.akatsuki.soul;

import java.util.*;

/** UUID-based rolling safety ledger shared across blades, modes, players and domain reopenings. */
public final class SoulDamageSafetyBudget {
  public record Limits(
      double hit,
      double targetSecond,
      double playerSecond,
      double targetWindow,
      double playerWindow,
      int cutGap,
      int burstCooldown) {
    public Limits {
      if (!Double.isFinite(hit + targetSecond + playerSecond + targetWindow + playerWindow)
          || Math.min(
                  Math.min(hit, targetSecond),
                  Math.min(playerSecond, Math.min(targetWindow, playerWindow)))
              <= 0
          || cutGap < 1
          || burstCooldown < 1)
        throw new IllegalArgumentException("Finite positive safety limits required");
    }
  }

  private record Charge(long tick, double amount) {}

  private static final class Window {
    final Deque<Charge> charges = new ArrayDeque<>();
    long lastCut = Long.MIN_VALUE / 2, lastResonance = Long.MIN_VALUE / 2,
        passiveHoldUntil, burstReady, burstUntil, touched;

    double used(long now, int ticks) {
      while (!charges.isEmpty() && now - charges.peekFirst().tick() >= 200) charges.removeFirst();
      return charges.stream().filter(c -> now - c.tick() < ticks).mapToDouble(Charge::amount).sum();
    }

    void add(long now, double amount) {
      charges.addLast(new Charge(now, amount));
      touched = now;
    }
  }

  private final Map<UUID, Window> targets = new HashMap<>(), players = new HashMap<>();

  /** Failed/partially absorbed attempts stay charged. Burst totals are reserved once up front. */
  public double reserve(
      UUID owner,
      UUID target,
      long now,
      SoulDamagePolicy.Mode mode,
      double request,
      Limits limits) {
    if (!Double.isFinite(request)
        || request <= 0
        || now < 0
        || targets.size() >= 4096 && !targets.containsKey(target)
        || players.size() >= 1024 && !players.containsKey(owner)) return 0;
    var t = targets.computeIfAbsent(target, id -> new Window());
    var p = players.computeIfAbsent(owner, id -> new Window());
    boolean burst = mode == SoulDamagePolicy.Mode.SOUL_BURST;
    if (burst ? now < t.burstReady : now < t.burstUntil || now - t.lastCut < limits.cutGap()
        || now < t.passiveHoldUntil)
      return 0;
    double amount =
        Math.max(
            0,
            Math.min(
                request,
                Math.min(
                    limits.targetSecond() - t.used(now, 20),
                    Math.min(
                        limits.playerSecond() - p.used(now, 20),
                        Math.min(
                            limits.targetWindow() - t.used(now, 200),
                            limits.playerWindow() - p.used(now, 200))))));
    if (!burst) amount = Math.min(amount, limits.hit());
    if (amount < .01) return 0;
    t.add(now, amount);
    p.add(now, amount);
    if (burst) {
      t.burstReady = now + limits.burstCooldown();
      t.burstUntil = now + 32;
    } else t.lastCut = now;
    return amount;
  }

  /**
   * Uncapped NORMAL space cuts keep target cooldown and burst locking. Every attempt
   * charges the existing rolling ledger; verified recovery cannot launder that budget.
   */
  public double reserveUncappedCut(
      UUID owner, UUID target, long now, double request, Limits limits) {
    if (!Double.isFinite(request)
        || request < .01
        || now < 0
        || targets.size() >= 4096 && !targets.containsKey(target)
        || players.size() >= 1024 && !players.containsKey(owner)) return 0;
    var t = targets.computeIfAbsent(target, id -> new Window());
    var p = players.computeIfAbsent(owner, id -> new Window());
    if (now < t.burstUntil || now - t.lastCut < limits.cutGap()
        || now < t.passiveHoldUntil) return 0;
    t.add(now, request);
    p.add(now, request);
    t.lastCut = now;
    return request;
  }

  /**
   * One owner-authenticated echo per target every 10 ticks. Echoes use the SAME
   * global target/player safety windows as automatic cuts and soul bursts, but not
   * the ambient cut cooldown. The native hit gets priority over the ambient domain.
   */
  public double reserveResonance(
      UUID owner, UUID target, long now, double request, Limits limits) {
    if (!Double.isFinite(request) || request < .01 || now < 0
        || targets.size() >= 4096 && !targets.containsKey(target)
        || players.size() >= 1024 && !players.containsKey(owner)) return 0;
    var t = targets.computeIfAbsent(target, id -> new Window());
    var p = players.computeIfAbsent(owner, id -> new Window());
    if (now < t.burstUntil || now - t.lastResonance < 10) return 0;
    double amount = Math.max(0, Math.min(request,
        Math.min(limits.hit(), Math.min(
            limits.targetSecond() - t.used(now, 20),
            Math.min(limits.playerSecond() - p.used(now, 20),
                Math.min(limits.targetWindow() - t.used(now, 200),
                    limits.playerWindow() - p.used(now, 200)))))));
    if (amount < .01) return 0;
    t.add(now, amount);
    p.add(now, amount);
    t.lastResonance = now;
    // The very next ambient tick cannot crowd out a verified native sword hit.
    t.passiveHoldUntil = Math.max(t.passiveHoldUntil, now + 6);
    return amount;
  }

  public boolean burstReady(UUID target, long now) {
    var w = targets.get(target);
    return w == null || now >= w.burstReady;
  }

  public void finishBurst(UUID target, long now) {
    var w = targets.get(target);
    if (w != null) w.burstUntil = Math.min(w.burstUntil, now);
  }

  public void prune(long now) {
    targets.values().removeIf(w -> now - w.touched >= 400 && now >= w.burstReady);
    players.values().removeIf(w -> now - w.touched >= 400);
  }

  public void clear() {
    targets.clear();
    players.clear();
  }
}
