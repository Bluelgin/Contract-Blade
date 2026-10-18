package com.maidweapon.forge.system.danmaku;

import com.maidweapon.forge.system.resonance.ResonanceService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Percent-of-pool cost policy; never silently grants free active spells. */
public final class DanmakuResonanceCostPolicy {
    public static int cost(DanmakuForm form) {
        return scaled(ResonanceService.maximum(), switch (form) {
            case NEEDLE -> 8;
            case FLOWER -> 14;
            case GUARD -> 22;
            case FANTASY -> 45;
        });
    }

    /** 100 means all of the shared maid pool, rounded up to forbid free casts. */
    public static int scaled(int capacity, int percent) {
        if (capacity <= 0 || percent <= 0 || percent > 100) return Integer.MAX_VALUE;
        return (int) Math.max(1L, ((long) capacity * percent + 99L) / 100L);
    }

    public static boolean charge(ServerPlayer owner, ItemStack carrier,
                                 String binding, DanmakuForm form) {
        int cost = cost(form);
        return cost != Integer.MAX_VALUE &&
                ResonanceService.succeeded(ResonanceService.pay(
                        owner, carrier, binding, cost, ResonanceService.newAttempt()));
    }
    private DanmakuResonanceCostPolicy() { }
}
