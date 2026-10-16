package com.maidweapon.forge.system.akatsuki.soul;

/**
 * Verified debt is never converted into new entitlement. When the recovery channel is
 * locked, a separately scheduled ambient cut may still occur without consuming or
 * resetting the receipt. Neither channel manufactures additional debt.
 */
public record SoulCutDecision(State state, double recoveryAmount) {
  public enum State {
    NORMAL,
    RECOVERABLE,
    RECOVERY_LOCKED
  }

  public static SoulCutDecision decide(
      boolean hasValidDebt, boolean recoveryCooldown, double availableRecovery) {
    if (!hasValidDebt) return new SoulCutDecision(State.NORMAL, 0);
    if (recoveryCooldown
        || !Double.isFinite(availableRecovery)
        || availableRecovery < .01)
      return new SoulCutDecision(State.RECOVERY_LOCKED, 0);
    return new SoulCutDecision(State.RECOVERABLE, availableRecovery);
  }
}
