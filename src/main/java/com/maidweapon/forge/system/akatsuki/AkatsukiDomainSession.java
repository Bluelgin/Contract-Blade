package com.maidweapon.forge.system.akatsuki;

import java.util.*;

/** Ephemeral targeting/scheduling only. No damage balance or rescue cooldown belongs to a blade. */
public final class AkatsukiDomainSession {
  public final AkatsukiDomainState lifecycle = new AkatsukiDomainState();
  public long lastContact = Long.MIN_VALUE / 2, executionPulseUntil, protectionUntil;
  public UUID target;
  public String binding = "";
  private long visualContact = Long.MIN_VALUE;
  private UUID visualVictim;
  private final Map<String, Long> next = new HashMap<>();

  public boolean active() {
    return lifecycle.valid();
  }

  public boolean attacking() {
    return lifecycle.attacking();
  }

  public boolean contact(long now, UUID victim) {
    lastContact = now;
    target = victim;
    if (visualContact == now && victim.equals(visualVictim)) return false;
    visualContact = now;
    visualVictim = victim;
    return true;
  }

  public boolean due(String ability, long now, long interval) {
    if (now < next.getOrDefault(ability, Long.MIN_VALUE)) return false;
    next.put(ability, now + interval);
    return true;
  }

  public void suspend() {
    lifecycle.suspend();
    target = null;
    lastContact = Long.MIN_VALUE / 2;
    // Do not reset attack timers or the finite protection window on a hand/state change.
  }
}
