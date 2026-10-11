package com.maidweapon.forge.system.fox.challenge;

/** The only normal-combat stagger gate: five confirmed defensive inputs, not projectile contacts. */
public final class BlackFoxBalance {
    public static final int REQUIRED = 5, RECOVERY_TICKS = 30, HIT_RECOVERY = 0;
    private int count;
    private long last = Long.MIN_VALUE;
    private boolean pending;
    public void parried(long now) {
        if(pending || last!=Long.MIN_VALUE && now-last<8) return;
        last=now;
        if(++count>=REQUIRED) { count=0; pending=true; }
    }
    public boolean take() { boolean result=pending; pending=false; return result; }
    public void clear() { count=0; pending=false; last=Long.MIN_VALUE; }
    public int progress() { return pending ? REQUIRED : count; }
    public BlackFoxBalance() { }
}
