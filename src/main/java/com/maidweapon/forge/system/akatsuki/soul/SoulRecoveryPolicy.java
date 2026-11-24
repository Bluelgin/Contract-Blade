package com.maidweapon.forge.system.akatsuki.soul;

/** Stable Forge API delegates to identical cross-version default rule. */
public final class SoulRecoveryPolicy {
    public static double cut(int level){return com.maidweapon.port.domain.ability.SoulRecoveryPolicy.cut(level);}
    public static double aerial(int level){return com.maidweapon.port.domain.ability.SoulRecoveryPolicy.aerial(level);}
    private SoulRecoveryPolicy(){}
}
