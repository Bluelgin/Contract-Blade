package com.maidweapon.forge.system.akatsuki;

/**
 * Pure health-driven lifecycle. Combat scheduling and persistent rescue receipts live elsewhere.
 */
public final class AkatsukiDomainState {
  public enum Phase {
    INACTIVE,
    NORMAL,
    RAMPAGE,
    RESCUE_COLLAPSE,
    RESCUE_REIGNITE,
    SUSPENDED
  }

  private Phase phase = Phase.INACTIVE;
  private long reigniteAt, resumeAt;

  public Phase phase() {
    return phase;
  }

  public boolean valid() {
    return phase != Phase.INACTIVE && phase != Phase.SUSPENDED;
  }

  public boolean attacking() {
    return phase == Phase.NORMAL || phase == Phase.RAMPAGE;
  }

  public boolean rampage() {
    return phase == Phase.RAMPAGE || phase == Phase.RESCUE_REIGNITE;
  }

  public boolean update(
      boolean authorized, boolean alive, double fraction, long now, double enter, double exit) {
    Phase before = phase;
    if (!alive) phase = Phase.INACTIVE;
    else if (!authorized) phase = Phase.SUSPENDED;
    else if (phase == Phase.RESCUE_COLLAPSE && now < reigniteAt) return false;
    else if ((phase == Phase.RESCUE_COLLAPSE || phase == Phase.RESCUE_REIGNITE) && now < resumeAt)
      phase = Phase.RESCUE_REIGNITE;
    else {
      boolean wasRampage = phase == Phase.RAMPAGE || phase == Phase.RESCUE_REIGNITE;
      double threshold = wasRampage ? Math.max(enter, exit) : enter;
      phase = fraction < threshold ? Phase.RAMPAGE : Phase.NORMAL;
    }
    return before != phase;
  }

  public boolean rescue(long now, int collapseTicks, int reigniteTicks) {
    if (!attacking()) return false;
    phase = Phase.RESCUE_COLLAPSE;
    reigniteAt = now + Math.max(1, collapseTicks);
    resumeAt = reigniteAt + Math.max(1, reigniteTicks);
    return true;
  }

  public void suspend() {
    phase = Phase.SUSPENDED;
  }
}
