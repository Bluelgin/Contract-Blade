package com.maidweapon.forge.system.interior.home;

import java.util.EnumSet;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;

/** Pure, bounded offline inference. No world ticks, resource costs or entity copies. */
public final class ContractHomeArrivalPlanner {
    public static final long SHORT_ABSENCE_MILLIS = 120000;
    private static final ContractHomeActivity[] PREFERENCES = {
            ContractHomeActivity.READ, ContractHomeActivity.PLAY,
            ContractHomeActivity.SIT, ContractHomeActivity.WANDER};
    public static ContractHomeActivity preference(UUID maid) {
        return PREFERENCES[new SplittableRandom(ContractHomeActivityResolver.seed("personality", maid, 0))
                .nextInt(PREFERENCES.length)];
    }
    public static boolean continuePrevious(boolean sameMaid, long departedAt, long now,
            ContractHomeActivity previous, ContractHomeClock.Phase phase) {
        long elapsed = now - departedAt;
        return sameMaid && departedAt > 0 && elapsed >= 0 && elapsed <= SHORT_ABSENCE_MILLIS
                && (previous != ContractHomeActivity.SLEEP || phase == ContractHomeClock.Phase.NIGHT);
    }
    public static ContractHomeActivity choose(String binding, UUID maid, ContractHomeClock.Reading clock,
            Set<ContractHomeActivity> available, ContractHomeActivity previous, boolean sameMaid,
            long departedAt, long now, int favorability) {
        var pool = EnumSet.of(ContractHomeActivity.IDLE, ContractHomeActivity.WANDER);
        for (var activity : PREFERENCES) if (available.contains(activity)) pool.add(activity);
        if (available.contains(ContractHomeActivity.SLEEP)) pool.add(ContractHomeActivity.SLEEP);
        if (continuePrevious(sameMaid, departedAt, now, previous, clock.phase()) && pool.contains(previous)) return previous;
        int total = 0;
        for (var activity : pool) total += weight(activity, preference(maid), previous, sameMaid, clock.phase(), favorability);
        int roll = new SplittableRandom(ContractHomeActivityResolver.seed(binding, maid, clock.slot())).nextInt(total);
        for (var activity : ContractHomeActivity.values()) {
            if (!pool.contains(activity)) continue;
            roll -= weight(activity, preference(maid), previous, sameMaid, clock.phase(), favorability);
            if (roll < 0) return activity;
        }
        return ContractHomeActivity.IDLE;
    }
    public static int weight(ContractHomeActivity activity, ContractHomeActivity favorite,
            ContractHomeActivity previous, boolean sameMaid, ContractHomeClock.Phase phase, int favorability) {
        int weight = ContractHomeActivityResolver.weight(activity, phase, favorability, false);
        if (activity == favorite) weight *= 2;
        // A nearby chair can be a quiet welcome, not a permanent door-guard task.
        if (activity == ContractHomeActivity.SIT) weight += Math.max(0, Math.min(384, favorability)) / 48;
        if (sameMaid && activity == previous && activity != ContractHomeActivity.SLEEP) weight = Math.max(1, weight / 3);
        return weight;
    }
    private ContractHomeArrivalPlanner() {}
}
