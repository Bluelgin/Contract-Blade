package com.maidweapon.common.system;

/** Compatibility facade; one shared registry keeps vanilla and addon tier lookups consistent. */
public final class MonsterTierRegistry {
    public static void registerTier(String id,int tier){com.maidweapon.port.domain.mastery.MonsterTierRegistry.registerTier(id,tier);}
    public static void registerBoss(String id,boolean boss){com.maidweapon.port.domain.mastery.MonsterTierRegistry.registerBoss(id,boss);}
    public static int getTier(String id){return com.maidweapon.port.domain.mastery.MonsterTierRegistry.getTier(id);}
    public static int estimateTierFromHealth(float health){return com.maidweapon.port.domain.mastery.MonsterTierRegistry.estimateTierFromHealth(health);}
    public static boolean isBoss(String id){return com.maidweapon.port.domain.mastery.MonsterTierRegistry.isBoss(id);}
    public static boolean isBossFromHealth(float health){return com.maidweapon.port.domain.mastery.MonsterTierRegistry.isBossFromHealth(health);}
    public static String getTierName(int tier){return com.maidweapon.port.domain.mastery.MonsterTierRegistry.getTierName(tier);}
    private MonsterTierRegistry(){}
}
