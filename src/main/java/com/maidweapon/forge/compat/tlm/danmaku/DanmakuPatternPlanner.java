package com.maidweapon.forge.compat.tlm.danmaku;

/** Deterministic, allocation-light polar choreography; native TLM collision owns damage. */
public final class DanmakuPatternPlanner {
    public record FlowerShot(double yaw, double rise, double speed) { }

    /** A five-petal phase modulation: first wave blooms, second converges. */
    public static FlowerShot flower(int bullet, int count, int wave, int mastery) {
        if (count < 1 || bullet < 0 || bullet >= count || wave < 0 || wave > 1)
            throw new IllegalArgumentException("Invalid floral wave");
        double theta = 2 * Math.PI * bullet / count + wave * Math.PI / 5;
        double petal = .55 + .45 * Math.cos(5 * theta);
        double yaw = Math.sin(theta) * (wave == 0 ? .70 : -.40) * petal;
        double rise = Math.cos(theta) * (wave == 0 ? .15 : .09) * petal;
        double speed = (wave == 0 ? .68 : .90)
                * (mastery >= 7 && bullet % 2 == 0 ? .80 : 1.0);
        return new FlowerShot(yaw, rise, speed);
    }

    public static double fantasyAngle(int wave, int bullet) {
        if (wave < 0 || wave > 3 || bullet < 0 || bullet >= 8)
            throw new IllegalArgumentException("Invalid fantasy wave");
        return bullet * Math.PI / 4 + wave * .34;
    }

    public static double fantasyRadius(int wave, int bullet) {
        double base = switch (wave) {
            case 0 -> 4.2;
            case 1 -> 3.3;
            case 2 -> 2.6;
            case 3 -> 1.9;
            default -> throw new IllegalArgumentException("Invalid fantasy wave");
        };
        return base * (.93 + .07 * Math.cos(5 * fantasyAngle(wave, bullet)));
    }
    private DanmakuPatternPlanner() { }
}
