package com.maidweapon.forge.client;

/** Tick samples plus click edges preserve even a tap shorter than a client tick. */
public final class DanmakuPressGesture {
    public enum Action { NONE, CAST, OPEN_WHEEL }
    private boolean pressed;
    private int ticks;
    public void reset() { pressed = false; ticks = 0; }
    public Action sample(boolean down, boolean clicked) {
        if (!down && !pressed && clicked) return Action.CAST;
        if (down && !pressed) { pressed = true; ticks = 0; }
        else if (down && ++ticks >= 6) { reset(); return Action.OPEN_WHEEL; }
        else if (!down && pressed) { reset(); return Action.CAST; }
        return Action.NONE;
    }
}
