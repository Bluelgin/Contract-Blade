package com.maidweapon.forge.system.danmaku;

/** Cosmetic refresh policy. Active combat paths never trust the cached state. */
final class DanmakuTickRefreshPolicy {
    static final int IDLE_INTERVAL = 10;

    static boolean shouldValidate(boolean changed, boolean active, long now, long lastValidated) {
        return changed || active || lastValidated == Long.MIN_VALUE
                || now < lastValidated || now - lastValidated >= IDLE_INTERVAL;
    }

    private DanmakuTickRefreshPolicy() { }
}
