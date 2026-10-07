package com.maidweapon.common;

/** Client presentation only. Never schedules attacks or changes an equipped stack. */
public final class AkatsukiSwordTimeline {
    public enum Motion { IDLE, DRAW, READY, CUT_1, CUT_2, CUT_3, SHEATHE }
    public static final int DRAW_TICKS = 8, CUT_TICKS = 8, SHEATHE_TICKS = 14, IDLE_DELAY = 40;
    private Motion motion = Motion.IDLE;
    private long started, lastActivity, lastTick = Long.MIN_VALUE;
    private int nextCut, previousSwingTime;
    private boolean previouslySwinging;

    public void update(long tick, boolean enabled, boolean engaged, boolean mainSwing, int swingTime) {
        if (!enabled) { reset(tick); return; }
        if (tick == lastTick) return;
        if (tick < lastTick || (lastTick != Long.MIN_VALUE && tick - lastTick > 100)) reset(tick);
        lastTick = tick;
        boolean newAttack = mainSwing && (!previouslySwinging || swingTime < previousSwingTime);
        previouslySwinging = mainSwing;
        previousSwingTime = swingTime;
        if (newAttack) {
            if (motion == Motion.IDLE || motion == Motion.SHEATHE) nextCut = 0;
            begin(Motion.values()[Motion.CUT_1.ordinal() + nextCut], tick);
            nextCut = (nextCut + 1) % 3;
            lastActivity = tick;
            return;
        }
        if (engaged || mainSwing) lastActivity = tick;
        if (motion == Motion.IDLE && engaged) begin(Motion.DRAW, tick);
        else if (motion == Motion.SHEATHE && engaged) begin(Motion.DRAW, tick);
        else if (motion == Motion.DRAW && tick - started >= DRAW_TICKS) begin(Motion.READY, tick);
        else if (isCut() && tick - started >= CUT_TICKS) begin(Motion.READY, tick);
        else if (motion == Motion.READY && tick - lastActivity >= IDLE_DELAY) begin(Motion.SHEATHE, tick);
        else if (motion == Motion.SHEATHE && tick - started >= SHEATHE_TICKS) {
            begin(Motion.IDLE, tick); nextCut = 0;
        }
    }

    public void reset(long tick) {
        begin(Motion.IDLE, tick); lastActivity = tick; lastTick = Long.MIN_VALUE;
        nextCut = previousSwingTime = 0; previouslySwinging = false;
    }
    private void begin(Motion value, long tick) { motion = value; started = tick; }
    public Motion motion() { return motion; }
    public boolean isCut() { return motion.ordinal() >= Motion.CUT_1.ordinal() && motion.ordinal() <= Motion.CUT_3.ordinal(); }
    public float age(long tick, float partial) { return Math.max(0, tick - started + partial); }
    public float duration() { return motion == Motion.DRAW ? DRAW_TICKS : motion == Motion.SHEATHE ? SHEATHE_TICKS : CUT_TICKS; }
    public String clip() { return motion.name().toLowerCase(java.util.Locale.ROOT); }
}
