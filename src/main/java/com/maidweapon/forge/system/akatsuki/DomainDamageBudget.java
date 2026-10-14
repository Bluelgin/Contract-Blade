package com.maidweapon.forge.system.akatsuki;

import java.util.*;

/** Sliding 20-tick windows. Reservations are charged before hurt(), including failed probes. */
public final class DomainDamageBudget {
  public record Limits(double hit, double targetSecond, double playerSecond, double domain) {
    public Limits {
      if (!Double.isFinite(hit + targetSecond + playerSecond + domain)
          || hit < 0
          || targetSecond < 0
          || playerSecond < 0
          || domain < 0) throw new IllegalArgumentException("Finite nonnegative limits required");
    }
  }

  private record Charge(long tick, double amount) {}

  private static final class Window {
    final Deque<Charge> charges = new ArrayDeque<>();
    long lastAttempt = Long.MIN_VALUE / 2, touched;

    double used(long now) {
      while (!charges.isEmpty() && now - charges.peekFirst().tick() >= 20) charges.removeFirst();
      return charges.stream().mapToDouble(Charge::amount).sum();
    }

    void add(long now, double value) {
      charges.addLast(new Charge(now, value));
      touched = now;
    }
  }

  private final Map<UUID, Window> targets = new HashMap<>(), players = new HashMap<>();

  public static Limits policy(
      double nativeHit,
      double nativeDps,
      double ratio,
      double fallbackHit,
      double fallbackDps,
      Limits ceilings) {
    double fraction = Double.isFinite(ratio) ? Math.max(0, Math.min(.2, ratio)) : 0;
    boolean knownHit = fraction > 0 && Double.isFinite(nativeHit) && nativeHit > 0;
    boolean knownDps = fraction > 0 && Double.isFinite(nativeDps) && nativeDps > 0;
    double hit = knownHit ? nativeHit * fraction : Math.min(.5, fallbackHit);
    // When DPS is unknown, one hit allowance per second is OUR policy, not native DPS data.
    double rate = knownDps ? nativeDps * fraction : knownHit ? hit : Math.min(1, fallbackDps);
    return new Limits(
        Math.min(hit, Math.min(4, ceilings.hit())),
        Math.min(rate, Math.min(4, ceilings.targetSecond())),
        Math.min(6, ceilings.playerSecond()),
        Math.min(24, ceilings.domain()));
  }

  public double reserve(UUID player, UUID target, long now, double spent, Limits limits) {
    if (!Double.isFinite(spent) || spent < 0) return 0;
    // Saturation fails closed; never evict recent charges to make room for an extra entity.
    if (targets.size() >= 4096 && !targets.containsKey(target)
        || players.size() >= 1024 && !players.containsKey(player)) return 0;
    var t = targets.computeIfAbsent(target, id -> new Window());
    var p = players.computeIfAbsent(player, id -> new Window());
    if (now - t.lastAttempt < 10) return 0;
    double amount =
        Math.max(
            0,
            Math.min(
                Math.min(4, limits.hit()),
                Math.min(
                    Math.min(4, limits.targetSecond()) - t.used(now),
                    Math.min(
                        Math.min(6, limits.playerSecond()) - p.used(now),
                        Math.min(24, limits.domain()) - spent))));
    if (amount < .001) return 0;
    t.lastAttempt = now;
    t.add(now, amount);
    p.add(now, amount);
    return amount;
  }

  public void prune(long now) {
    targets.values().removeIf(w -> now - w.touched > 200);
    players.values().removeIf(w -> now - w.touched > 200);
  }

  public void clear() {
    targets.clear();
    players.clear();
  }
}
