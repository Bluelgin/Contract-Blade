package com.maidweapon.forge.system.akatsuki.soul;

/**
 * Deterministic, immutable geometry for Akatsuki's verified-cap soul-core constellation.
 * Presentation only: stars are not server entities, hitboxes, damage sources or debts.
 */
public final class SoulEclipseGeometry {
  public static final int STARS = 3;
  public static final double GAP_START = 5.15, GAP_END = 5.76;
  private static final double[] STARS_AT = {0.40, 2.58, 4.37};
  private static final double TWO_PI = Math.PI * 2;

  public record Point(double x, double y, double z) {
    public Point lerp(Point to, double fraction) {
      return new Point(x + (to.x - x) * fraction,
          y + (to.y - y) * fraction, z + (to.z - z) * fraction);
    }
  }

  /** A tilted, asymmetric ellipse instead of a generic flat halo. */
  public static Point orbit(double angle, double tick) {
    double phase = angle + tick * .0035;
    double x = Math.cos(phase) * 1.40;
    double y = Math.sin(phase) * .73;
    return new Point(x * .94 - y * .33, x * .33 + y * .94,
        Math.sin(phase + .7) * .16);
  }

  public static Point star(int index, double tick) {
    if (index < 0 || index >= STARS) throw new IllegalArgumentException("star index");
    double tremor = Math.sin(tick * .065 + index * 2.1) * .025;
    return orbit(STARS_AT[index] + tremor, tick);
  }

  public static boolean onTrack(double angle) {
    double a = ((angle % TWO_PI) + TWO_PI) % TWO_PI;
    return a < GAP_START || a > GAP_END;
  }

  /** Stage number mapped onto the three fixed satellites, regardless of 3-5 damage stages. */
  public static int brokenStar(int stage, int stages) {
    if (stage < 0 || stage >= stages - 1 || stages < 3 || stages > 5)
      return -1;
    return Math.min(STARS - 1, (stage * STARS) / (stages - 1));
  }

  public static float fade(double age, double lifetime) {
    if (!Double.isFinite(age) || !Double.isFinite(lifetime) || lifetime <= 0
        || age < 0 || age >= lifetime) return 0;
    return (float) Math.min(1, Math.max(0, (lifetime - age) / Math.min(2, lifetime)));
  }

  private SoulEclipseGeometry() {}
}
