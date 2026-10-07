package com.maidweapon.forge.system.fox.challenge;

/** Pure short-lived sword momentum. Three distinct melee windows break it; disengagement restores it slowly. */
public final class BlackFoxSwordplay {
    private int momentum = 3;
    private long lastSequence = -1, lastContact = Long.MIN_VALUE, recoveredAt;
    private int lastWindow = -1;
    public int momentum() { return momentum; }
    public boolean wasParried(long sequence) { return lastSequence == sequence; }
    public boolean parry(long now, long sequence, int window) {
        if (lastSequence == sequence && lastWindow == window) return false;
        lastSequence = sequence; lastWindow = window; lastContact = now; recoveredAt = now;
        if (--momentum > 0) return false;
        momentum = 3;
        return true;
    }
    public void advance(long now, boolean engaged) {
        if (!engaged && lastContact != Long.MIN_VALUE && now - lastContact >= 80 && now - recoveredAt >= 20) {
            momentum = Math.min(3, momentum + 1); recoveredAt = now;
        }
    }
    public void clash() { momentum = 3; lastSequence = -1; lastWindow = -1; lastContact = Long.MIN_VALUE; }
}
