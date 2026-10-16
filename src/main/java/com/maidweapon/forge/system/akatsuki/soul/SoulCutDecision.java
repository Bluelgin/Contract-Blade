package com.maidweapon.forge.system.akatsuki.soul;

/**
 * Immutable ordinary space-cut choice. Only positively verified, currently usable debt can
 * authorize recovery. A valid but exhausted/cooling debt locks the normal fallback until
 * that receipt expires or becomes invalid, so attempts cannot re-mint ordinary cut damage.
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
