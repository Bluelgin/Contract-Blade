package com.maidweapon.forge.client;

import java.util.*;

/** Independent cosmetic arrivals. No entities, target tracking or damage scheduling. */
public final class AkatsukiRainDirector {
  public record Drop(
      double born,
      double x,
      double z,
      double height,
      double leanX,
      double leanZ,
      double reveal,
      double flight) {}

  private final Random random;
  private double[] arrivals = new double[0];
  private int cursor, perTick;
  private long deadline, lastPoll = Long.MIN_VALUE;

  public AkatsukiRainDirector(long seed) {
    random = new Random(seed);
  }

  /** A new cue replaces an unfinished shower instead of banking another burst. */
  public void begin(long now, int blades, boolean rampage, int quality, double density) {
    cancel();
    int count =
        Math.max(
            1,
            Math.min(
                192,
                (int)
                    (Math.max(8, Math.min(96, blades))
                        * (rampage ? 2 : 1)
                        * Math.max(.1, Math.min(3, density))
                        * (quality == 0 ? .25 : quality == 1 ? .7 : 1))));
    int duration = rampage ? 36 : 44;
    perTick = quality == 0 ? 2 : quality == 1 ? 4 : 6;
    arrivals = new double[count];
    arrivals[0] = now + random.nextDouble();
    for (int i = 1; i < count; i++) arrivals[i] = now + random.nextDouble() * duration;
    Arrays.sort(arrivals);
    deadline = now + duration + 4;
  }

  public void cancel() {
    arrivals = new double[0];
    cursor = 0;
    // Do not reset lastPoll: repeated cues cannot bypass the same-tick emission limit.
  }

  public List<Drop> poll(long now, double velocityX, double velocityZ) {
    if (now > deadline) cancel(); // A stalled client never replays a queued sheet of swords.
    if (lastPoll == now || cursor == arrivals.length) return List.of();
    lastPoll = now;
    var result = new ArrayList<Drop>();
    while (cursor < arrivals.length && arrivals[cursor] < now + 1 && result.size() < perTick) {
      double born = Math.max(now, arrivals[cursor++]);
      // Area-uniform scatter, biased toward the victim; a minority lead its current movement.
      double choice = random.nextDouble();
      double radius = Math.sqrt(random.nextDouble()) * (choice < .55 ? 1.1 : 3.6);
      double angle = random.nextDouble() * Math.PI * 2;
      double leadX = 0, leadZ = 0;
      if (choice >= .8) {
        double speed = Math.hypot(velocityX, velocityZ);
        double factor = speed > 0 ? Math.min(4, 1.4 / speed) : 0;
        leadX = velocityX * factor;
        leadZ = velocityZ * factor;
      }
      result.add(
          new Drop(
              born,
              Math.cos(angle) * radius + leadX,
              Math.sin(angle) * radius + leadZ,
              8 + random.nextDouble() * 4,
              (random.nextDouble() - .5) * .7,
              (random.nextDouble() - .5) * .7,
              .35 + random.nextDouble() * .5,
              1.8 + random.nextDouble() * 1.2));
    }
    return result;
  }

  public static double progress(double age, double reveal, double flight) {
    return Math.max(0, Math.min(1, (age - reveal) / flight));
  }
}
