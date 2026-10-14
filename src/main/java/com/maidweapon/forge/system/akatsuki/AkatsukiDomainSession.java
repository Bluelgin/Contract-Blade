package com.maidweapon.forge.system.akatsuki;

import java.util.*;

/** Tick state has no saved NBT. One per player; changing hands cannot reset its timers. */
public final class AkatsukiDomainSession {
    public long lastContact = Long.MIN_VALUE / 2, expires, readyAt, openedAt, cloakUntil;
    public int contacts;
    public UUID target;
    public String binding = "";
    private final Map<String, Long> next = new HashMap<>();
    public boolean active(long now) { return expires > now; }
    public boolean contact(long now, UUID victim, int required, int gap, int duration, int cooldown) {
        if (now - lastContact < 8) return false;
        contacts = now - lastContact > gap ? 1 : contacts + 1;
        lastContact = now;
        target = victim;
        if (active(now)) return false; // Fixed episode prevents infinite renewal and budget refill.
        if (now < readyAt || contacts < required) return false;
        openedAt = now; expires = now + duration; readyAt = now + Math.max(duration, cooldown);
        next.clear(); contacts = 0;
        return true;
    }
    public boolean due(String ability, long now, long interval) {
        if (now < next.getOrDefault(ability, Long.MIN_VALUE)) return false;
        next.put(ability, now + interval); return true;
    }
    public void suspend() { expires = 0; cloakUntil = 0; contacts = 0; target = null; }
}
