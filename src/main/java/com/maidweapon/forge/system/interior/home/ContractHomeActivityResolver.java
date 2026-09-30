package com.maidweapon.forge.system.interior.home;

import java.util.EnumMap;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;

/** Stable weighted choices in enum order; missing/unsupported targets never enter the pool. */
public final class ContractHomeActivityResolver {
    public static long seed(String binding, UUID maid, long slot) {
        long hash = 0xcbf29ce484222325L;
        for (char c : binding.toCharArray()) hash = (hash ^ c) * 0x100000001b3L;
        hash ^= maid.getMostSignificantBits();
        hash = Long.rotateLeft(hash, 23) ^ maid.getLeastSignificantBits() ^ slot;
        hash = (hash ^ (hash >>> 30)) * 0xbf58476d1ce4e5b9L;
        return (hash ^ (hash >>> 27)) * 0x94d049bb133111ebL;
    }
    public static int weight(ContractHomeActivity activity, ContractHomeClock.Phase phase,
                             int favorability, boolean playerPresent) {
        return switch (activity) {
            case SLEEP -> phase == ContractHomeClock.Phase.NIGHT ? 85 : 2;
            case SIT -> phase == ContractHomeClock.Phase.EVENING ? 25 : 15;
            case PLAY -> phase == ContractHomeClock.Phase.EVENING ? 25 : 12;
            case READ -> phase == ContractHomeClock.Phase.EVENING ? 22
                    : phase == ContractHomeClock.Phase.DAY ? 16 : 8;
            case MEAL -> switch (phase) {
                case MORNING -> 24;
                case DAY -> 18;
                case EVENING -> 22;
                case NIGHT -> 3;
            };
            case WANDER -> phase == ContractHomeClock.Phase.NIGHT ? 4 : 20;
            case STAY_NEAR_PLAYER -> playerPresent ? 3 + Math.max(0, Math.min(384, favorability)) / 12 : 0;
            case IDLE -> 5;
            default -> 0; // Future adapters must also define an intentional activity policy.
        };
    }
    public static ContractHomeActivity resolve(long seed, ContractHomeClock.Phase phase,
            Set<ContractHomeActivity> available, int favorability, boolean playerPresent) {
        return resolve(seed, phase, available, favorability, playerPresent, null);
    }
    public static ContractHomeActivity resolve(long seed, ContractHomeClock.Phase phase,
            Set<ContractHomeActivity> available, int favorability, boolean playerPresent, ContractHomeActivity favorite) {
        var weights = new EnumMap<ContractHomeActivity, Integer>(ContractHomeActivity.class);
        int total = 0;
        for (var activity : ContractHomeActivity.values()) {
            if (!available.contains(activity)) continue;
            int weight = weight(activity, phase, favorability, playerPresent);
            if (activity == favorite) weight *= 2;
            if (weight > 0) { weights.put(activity, weight); total += weight; }
        }
        if (total == 0) return ContractHomeActivity.IDLE;
        int roll = new SplittableRandom(seed).nextInt(total);
        for (var entry : weights.entrySet()) {
            roll -= entry.getValue();
            if (roll < 0) return entry.getKey();
        }
        return ContractHomeActivity.IDLE;
    }
    private ContractHomeActivityResolver() {}
}
