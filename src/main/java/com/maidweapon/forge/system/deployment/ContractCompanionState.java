package com.maidweapon.forge.system.deployment;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/** Persistent intent and timing, never another serialized copy of the maid. */
public final class ContractCompanionState {
    private static final String KEY = "MaidWeaponCompanion";
    public enum Mode { MANUAL, GUARD, RESIDENT }

    public static Mode mode(ItemStack carrier) {
        return parseMode(data(carrier).getString("Mode"));
    }

    public static Mode mode(Entity maid) {
        return parseMode(maid.getPersistentData().getCompound(KEY).getString("Mode"));
    }

    /** Establish spawn intent before lifecycle callbacks, without mutating the carrier on failure. */
    public static void prepareEntity(Entity maid, Mode mode) {
        CompoundTag state = new CompoundTag();
        state.putString("Mode", mode.name());
        maid.getPersistentData().put(KEY, state);
    }

    private static Mode parseMode(String name) {
        try { return Mode.valueOf(name); }
        catch (IllegalArgumentException ignored) { return Mode.MANUAL; }
    }

    public static void mark(ItemStack carrier, Entity maid, Mode mode, long until) {
        CompoundTag state = data(carrier).copy();
        state.putString("Mode", mode.name());
        state.putLong("Until", until);
        carrier.getOrCreateTag().put(KEY, state);
        maid.getPersistentData().put(KEY, state.copy());
        ContractRecoveryService.rememberLocation(carrier, maid);
    }

    public static long until(ItemStack carrier) { return data(carrier).getLong("Until"); }

    public static void extend(ItemStack carrier, Entity maid, long until) {
        mark(carrier, maid, mode(carrier), Math.max(until(carrier), until));
    }

    public static void clear(ItemStack carrier) {
        if (carrier.getTag() != null) carrier.getTag().remove(KEY);
    }

    private static CompoundTag data(ItemStack carrier) {
        return carrier.getTag() == null ? new CompoundTag() : carrier.getTag().getCompound(KEY);
    }

    private ContractCompanionState() { }
}
