package com.maidweapon.forge.system.fox.challenge;

/** Pure selection and short-lived observations. No entities, movement, damage or resource ownership. */
public final class BlackFoxTactics {
    private int hits;
    private long lastHit = Long.MIN_VALUE;
    public void pressured(long now) {
        if (now == lastHit) return;
        hits = now - lastHit > 60 || lastHit == Long.MIN_VALUE ? 1 : Math.min(3, hits + 1);
        lastHit = now;
    }
    public boolean reposition(long now) {
        boolean result = hits >= 3 && now - lastHit <= 60;
        if (result) hits = 0;
        return result;
    }
}
