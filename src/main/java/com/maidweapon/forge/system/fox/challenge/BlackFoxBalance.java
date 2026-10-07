package com.maidweapon.forge.system.fox.challenge;

/** Five confirmed parries, not projectile contacts. Independent of ordinary short staggers. */
public final class BlackFoxBalance {
    public static final int REQUIRED = 5, RECOVERY_TICKS = 100, HIT_RECOVERY = 10;
    private int count;
    private long last = Long.MIN_VALUE;
    private boolean pending;
    public void parried(long now) {
        if(pending || last!=Long.MIN_VALUE && now-last<4) return;
        last=now;
        if(++count>=REQUIRED) { count=0; pending=true; }
    }
    public boolean take() { boolean result=pending; pending=false; return result; }
    public void clear() { count=0; pending=false; last=Long.MIN_VALUE; }
    public int progress() { return pending ? REQUIRED : count; }
    public BlackFoxBalance() { }
}
