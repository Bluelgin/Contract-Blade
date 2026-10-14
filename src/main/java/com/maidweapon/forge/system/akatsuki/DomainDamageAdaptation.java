package com.maidweapon.forge.system.akatsuki;

import java.util.*;

/** One candidate per pulse; at most four switches per 200 ticks. */
public final class DomainDamageAdaptation {
  public enum Channel {
    SLASH,
    RAPID,
    PIERCING,
    ARCANE,
    ABSOLUTE
  }

  private final EnumMap<Channel, Double> scores = new EnumMap<>(Channel.class);
  private Channel selected = Channel.SLASH;
  private int failures, switches;
  private long switchAt = Long.MIN_VALUE / 2, windowAt, lastUsed, stalledUntil;
  private String phase = "";

  public Channel choose(long now, String currentPhase, List<Channel> enabled) {
    if (enabled.isEmpty()) return null;
    if (now < stalledUntil) return null;
    if (!phase.equals(currentPhase) || now - lastUsed > 200) {
      scores.clear();
      selected = enabled.get(0);
      failures = 0;
      phase = currentPhase;
    }
    if (now - windowAt >= 200) {
      switches = 0;
      windowAt = now;
    }
    lastUsed = now;
    if (!enabled.contains(selected)) selected = enabled.get(0);
    if (failures >= 2
        && enabled.stream().allMatch(scores::containsKey)
        && enabled.stream().noneMatch(c -> scores.get(c) > 0)) {
      stalledUntil = now + 100;
      scores.clear();
      failures = 0;
      selected = enabled.get(0);
      return null;
    }
    if (failures >= 2 && now - switchAt >= 20 && switches < 4) {
      Channel next =
          enabled.stream()
              .filter(c -> !scores.containsKey(c))
              .findFirst()
              .orElseGet(
                  () ->
                      enabled.stream()
                          .max(Comparator.comparingDouble(c -> scores.getOrDefault(c, 0d)))
                          .orElse(selected));
      if (next == selected && enabled.size() > 1)
        next = enabled.get((enabled.indexOf(selected) + 1) % enabled.size());
      selected = next;
      failures = 0;
      switchAt = now;
      switches++;
    }
    return selected;
  }

  public void feedback(
      Channel channel, double before, double after, double requested, boolean stablePhase) {
    if (channel == null) return;
    double actual = before - after;
    boolean reliable =
        stablePhase
            && Double.isFinite(actual)
            && requested > 0
            && actual >= 0
            && actual <= requested * 1.05 + .001;
    double score = reliable ? Math.min(1, actual / requested) : 0;
    scores.put(channel, score);
    failures = score >= .65 ? 0 : failures + 1;
  }

  public long lastUsed() {
    return lastUsed;
  }

  public double score(Channel channel) {
    return scores.getOrDefault(channel, 0d);
  }
}
