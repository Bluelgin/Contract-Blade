package com.maidweapon.forge.system.fox.challenge;

import java.util.LinkedHashSet;

/** Pure policy: ordinary cuts never build stagger; each committed skill round counts once. */
public final class BlackFoxParryCredit {
    private record Window(BlackFoxFight.Skill skill, long sequence, int round) { }
    private final LinkedHashSet<Window> credited = new LinkedHashSet<>();
    public boolean accept(BlackFoxFight.Skill skill, long sequence, int round) {
        boolean eligible = switch (skill) {
            case RETURN_BLADE, TRIPLE_COMBO, DIMENSION_STRIKE, STANDING_IAIDO, SKY, SEAL, UNSEAL,
                    RIFT_CROSS, ARC_BARRAGE, SWORD_WHEEL, DOMAIN_CROSS, DOMAIN_RAIN, DOMAIN_SEAL, SEAL_ARRAY -> true;
            default -> false;
        };
        if (!eligible || !credited.add(new Window(skill, sequence, round))) return false;
        if (credited.size() > 64) credited.remove(credited.iterator().next());
        return true;
    }
}
