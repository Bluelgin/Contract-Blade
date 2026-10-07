package com.maidweapon.forge.system.fox.challenge;

/** Visual-only head-to-tail erasure. Never extends the server's damage window. */
public final class BlackFoxSlashTimeline {
    public static final int HOLD = 2, DURATION = 12;
    public enum Layer {
        CORE(2, .5f, 4), BAND(3, 1.5f, 9), HALO(4, 2, 12), TRAIL(5, 1, 14);
        public final int shaderKind;
        public final float hold, duration;
        Layer(int shaderKind, float hold, float duration) {
            this.shaderKind = shaderKind; this.hold = hold; this.duration = duration;
        }
    }
    public static float visibility(float age, float along, Layer layer) {
        return visibility(age, along, layer.hold, layer.duration);
    }
    public static float visibility(float age, float along) {
        return visibility(age, along, HOLD, DURATION);
    }
    private static float visibility(float age, float along, float hold, float duration) {
        float elapsed = age - BlackFoxDimensionTimeline.HIT;
        if (elapsed < 0 || elapsed >= duration) return 0;
        if (elapsed <= hold) return 1;
        float edge = (elapsed - hold) / (duration - hold) * 1.16f - .08f;
        float value = Math.max(0, Math.min(1, (along - edge) / .08f));
        return value * value * (3 - 2 * value);
    }
    public static float brightness(float age, Layer layer) {
        float elapsed = age - BlackFoxDimensionTimeline.HIT;
        if (elapsed < 0 || elapsed >= layer.duration) return 0;
        float t = Math.max(0, (elapsed-layer.hold)/(layer.duration-layer.hold));
        return (1-t)*(1-t);
    }
    private BlackFoxSlashTimeline() { }
}
