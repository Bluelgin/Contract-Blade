package com.maidweapon.forge.system.interior.home;

import java.util.UUID;

/** Seeds and weights for one-shot offline arrival inference, never online AI. */
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
    private ContractHomeActivityResolver() {}
}
