package com.maidweapon.common;

/** Pure music gain policy; no world, damage or playback ownership. */
public final class BlackFoxMusicEnvelope {
    private float gain;
    private int duck;
    private int entryTicks;
    public static final int ENTRY_TICKS = 60;
    private boolean ending;
    public void parry() { duck = 8; }
    public void end() { ending = true; }
    public boolean ended() { return ending && gain <= 0; }
    public float gain() { return gain; }
    public void tick(boolean enabled, boolean finalStand) {
        float target = ending || !enabled ? 0 : finalStand ? .55f : 1;
        if (!ending && enabled) {
            float progress = Math.min(1, ++entryTicks / (float) ENTRY_TICKS);
            target *= progress * progress * (3 - 2 * progress);
        } else if (!enabled) entryTicks = 0;
        if (duck > 0) { target *= .45f; duck--; }
        float step = ending ? .025f : target < gain ? .2f : .08f;
        gain += Math.max(-step, Math.min(step, target - gain));
        gain = Math.max(0, Math.min(1, gain));
    }
}
