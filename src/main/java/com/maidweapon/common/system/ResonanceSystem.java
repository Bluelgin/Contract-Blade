package com.maidweapon.common.system;

import com.maidweapon.common.MaidWeaponConfig;
import com.maidweapon.common.data.MaidWeaponData;

/** Short-term contract resource, deliberately independent from TLM favorability. */
public final class ResonanceSystem {
    private ResonanceSystem() {}

    public static boolean spend(MaidWeaponData data, int amount) {
        int cost = Math.max(0, amount);
        if (data.getResonance() < cost) return false;
        data.reduceResonance(cost);
        return true;
    }

    public static void recover(MaidWeaponData data, int amount) {
        if (amount > 0) data.addResonance(amount);
    }

    public static void onEat(MaidWeaponData data, float foodRestored) {
        recover(data, scaled(foodRestored, MaidWeaponConfig.RESONANCE_FOOD_MULTIPLIER.get()));
    }

    public static void onHeal(MaidWeaponData data, float healthRestored) {
        recover(data, scaled(healthRestored, MaidWeaponConfig.RESONANCE_HEAL_MULTIPLIER.get()));
    }

    public static void onOwnerHurt(MaidWeaponData data, float healthLost) {
        recover(data, scaled(healthLost, MaidWeaponConfig.RESONANCE_OWNER_HURT_MULTIPLIER.get()));
    }

    private static int scaled(float value, double multiplier) {
        if (value <= 0 || multiplier <= 0) return 0;
        return Math.max(1, (int) Math.floor(value * multiplier));
    }
}
