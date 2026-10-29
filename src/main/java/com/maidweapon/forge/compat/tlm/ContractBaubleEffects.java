package com.maidweapon.forge.compat.tlm;

import com.maidweapon.forge.item.ContractProjectionBaubleItem;
import com.maidweapon.forge.system.resonance.ResonanceContract;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import java.util.EnumSet;

/** Capability composition shared by old projection items and the ultimate upgrade. */
public final class ContractBaubleEffects {
    public enum Effect { WEAPON_PROJECTION, ARMOR_PROJECTION, RESONANCE_WAIVER, OATH_MANIFESTATION, DANMAKU_ART }
    public static EnumSet<Effect> effects(ItemStack stack) {
        var result = EnumSet.noneOf(Effect.class);
        if (stack.isEmpty()) return result;
        if (stack.getItem() instanceof com.maidweapon.forge.item.DanmakuTalismanItem) {
            result.add(Effect.DANMAKU_ART);
            return result;
        }
        if (!(stack.getItem() instanceof ContractProjectionBaubleItem item)) return result;
        if (item.mode().weapon()) result.add(Effect.WEAPON_PROJECTION);
        if (item.mode().armor()) result.add(Effect.ARMOR_PROJECTION);
        if (item.oathManifestation()) result.add(Effect.OATH_MANIFESTATION);
        if (item.resonanceWaiver()) result.add(Effect.RESONANCE_WAIVER);
        return result;
    }
    /** Native slot order is shared by stored/live capability and projection consumers.
     * First projection keeps its existing policy; first ultimate alone grants its special power.
     */
    public static EnumSet<Effect> resolveSlots(IItemHandler slots) {
        var result = EnumSet.noneOf(Effect.class);
        boolean projection = false, ultimate = false;
        for (int slot = 0; slot < slots.getSlots(); slot++) {
            var next = effects(slots.getStackInSlot(slot));
            if (!projection && (next.contains(Effect.WEAPON_PROJECTION) || next.contains(Effect.ARMOR_PROJECTION))) {
                if (next.contains(Effect.WEAPON_PROJECTION)) result.add(Effect.WEAPON_PROJECTION);
                if (next.contains(Effect.ARMOR_PROJECTION)) result.add(Effect.ARMOR_PROJECTION);
                projection = true;
            }
            if (!ultimate && (next.contains(Effect.RESONANCE_WAIVER) || next.contains(Effect.OATH_MANIFESTATION))) {
                if (next.contains(Effect.RESONANCE_WAIVER)) result.add(Effect.RESONANCE_WAIVER);
                else result.add(Effect.OATH_MANIFESTATION);
                ultimate = true;
            }
            if (next.contains(Effect.DANMAKU_ART)) result.add(Effect.DANMAKU_ART);
        }
        return result;
    }
    public static boolean liveHas(Entity maid, Effect effect) {
        if (!TlmEntityAdapter.isMaidEntity(maid)) return false;
        try {
            Object value = maid.getClass().getMethod("getMaidBauble").invoke(maid);
            if (value instanceof IItemHandler slots) {
                return resolveSlots(slots).contains(effect);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { }
        return false;
    }
    /** Validated native inventory of this exact bound maid, never a player inventory item. */
    public static boolean has(ResonanceContract context, Effect effect) {
        if (context == null || effect == null) return false;
        Entity maid = context.liveMaid();
        return maid != null ? liveHas(maid, effect) : StoredMaidBaubleResolver.has(context, effect);
    }
    public static boolean hasWaiver(ResonanceContract context) {
        return has(context, Effect.RESONANCE_WAIVER);
    }
    private ContractBaubleEffects() { }
}
