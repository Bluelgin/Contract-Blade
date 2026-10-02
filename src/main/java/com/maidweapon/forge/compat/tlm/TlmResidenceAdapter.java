package com.maidweapon.forge.compat.tlm;

import net.minecraft.world.entity.Entity;

/** Native home/stay choices remain owned by TLM, not a parallel residence menu. */
public final class TlmResidenceAdapter {
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
