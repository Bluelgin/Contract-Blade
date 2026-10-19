package com.maidweapon.forge.system.resonance;

import java.util.function.BooleanSupplier;

/** A paid half-open combat window. Failed payments never authorize later attacks. */
public final class ResonancePaymentWindow {
    private long paidAt = Long.MIN_VALUE;
    private long until = Long.MIN_VALUE;

    public boolean covers(long now) { return now >= paidAt && now < until; }

    public boolean pay(long now, int ticks, BooleanSupplier payment) {
        if (ticks < 1) throw new IllegalArgumentException("Invalid combat window");
        if (covers(now)) return true;
        if (!payment.getAsBoolean()) return false;
        paidAt = now;
        until = now + ticks;
        return true;
    }
}
