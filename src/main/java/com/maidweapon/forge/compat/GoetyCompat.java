package com.maidweapon.forge.compat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraftforge.fml.ModList;

/**
 * Narrow Goety compatibility boundary.
 *
 * <p>Contract Core currently needs presence detection and generic undead
 * classification only. Monster progression falls back to the central health/
 * boss estimator, so this class deliberately does not maintain a second list
 * of Goety entity IDs.</p>
 */
public final class GoetyCompat {
    private static final String MOD_ID = "goety";

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /**
     * Retained for beta addon source compatibility. Explicit monster tables are
     * no longer required by Core's dynamic tier estimator.
     */
    @Deprecated
    public static void registerMonsters() {
        // Intentionally empty.
    }

    public static boolean isUndeadEntity(Object entity) {
        return isLoaded() && entity instanceof LivingEntity living
                && living.getMobType() == MobType.UNDEAD;
    }

    /**
     * Legacy extension point. Core does not reinterpret Goety spell scaling;
     * the owning mod remains authoritative.
     */
    @Deprecated
    public static float getDarkSpellPower(Object spellStack) {
        return 1.0f;
    }

    private GoetyCompat() {}
}
