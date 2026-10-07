package com.maidweapon.forge.compat.tlm;

import net.minecraft.world.entity.Entity;

/** Native home/stay choices remain owned by TLM, not a parallel residence menu. */
public final class TlmResidenceAdapter {
    private static final String SNAPSHOT = "MaidWeaponResidenceChoice";
    public static void rememberResidence(Entity maid) {
        var choice = new net.minecraft.nbt.CompoundTag();
        choice.putBoolean("Home", flag(maid, "isHomeModeEnable"));
        choice.putBoolean("Sit", flag(maid, "isOrderedToSit"));
        maid.getPersistentData().put(SNAPSHOT, choice);
    }
    public static void restoreResidence(Entity maid) {
        var choice = maid.getPersistentData().getCompound(SNAPSHOT);
        boolean home = choice.getBoolean("Home"), sit = choice.getBoolean("Sit");
        try {
            maid.getClass().getMethod("setHomeModeEnable", boolean.class).invoke(maid, home);
            // Without a previous home choice, safely wait in place instead.
            maid.getClass().getMethod("setOrderedToSit", boolean.class).invoke(maid, sit || !home);
        } catch (ReflectiveOperationException | LinkageError ignored) { }
    }
    public static boolean isResident(Entity maid) {
        return flag(maid, "isHomeModeEnable") || flag(maid, "isOrderedToSit");
    }

    public static void startFollowing(Entity maid) {
        try {
            maid.getClass().getMethod("setHomeModeEnable", boolean.class).invoke(maid, false);
            maid.getClass().getMethod("setOrderedToSit", boolean.class).invoke(maid, false);
        } catch (ReflectiveOperationException | LinkageError ignored) { }
    }

    private static boolean flag(Entity maid, String method) {
        if (!TlmEntityAdapter.isMaidEntity(maid)) return false;
        try {
            return Boolean.TRUE.equals(maid.getClass().getMethod(method).invoke(maid));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private TlmResidenceAdapter() { }
}
