package com.maidweapon.forge.compat.tacz;

import com.maidweapon.forge.compat.TaczCompat;
import com.tacz.guns.api.item.IGun;
import net.minecraft.world.item.ItemStack;

/** Only physically debited rounds may be materialized on projection teardown. */
public final class TaczAmmoAccounting {
    static final String PAID = "MaidWeaponTaczPaidAmmo";
    static final String TRACKED = "MaidWeaponTaczAmmoTracked";
    public static void purchased(ItemStack gun, int amount) {
        if (!TaczCompat.isProjection(gun) || amount <= 0) return;
        var tag = gun.getOrCreateTag();
        tag.putInt(PAID, (int) Math.min(Integer.MAX_VALUE, (long) Math.max(0, tag.getInt(PAID)) + amount));
    }
    public static int loaded(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        return gun == null ? 0 : gun.getCurrentAmmoCount(stack) + (gun.hasBulletInBarrel(stack) ? 1 : 0);
    }
    public static void spent(ItemStack stack, int before) {
        if (!TaczCompat.isProjection(stack)) return;
        int spent = Math.max(0, before - loaded(stack));
        var tag = stack.getOrCreateTag();
        tag.putInt(PAID, Math.max(0, tag.getInt(PAID) - spent));
    }
    private TaczAmmoAccounting() { }
}
