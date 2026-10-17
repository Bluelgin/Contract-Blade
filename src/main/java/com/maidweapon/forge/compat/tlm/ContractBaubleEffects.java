package com.maidweapon.forge.compat.tlm;

import com.maidweapon.forge.item.ContractProjectionBaubleItem;
import com.maidweapon.forge.system.resonance.ResonanceContract;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import java.util.EnumSet;

/** Capability composition shared by old projection items and the ultimate upgrade. */
public final class ContractBaubleEffects {
    public enum Effect { WEAPON_PROJECTION, ARMOR_PROJECTION, AUTO_WORK, RESONANCE_WAIVER }
    public static EnumSet<Effect> effects(ItemStack stack) {
        var result = EnumSet.noneOf(Effect.class);
        if (stack.isEmpty() || !(stack.getItem() instanceof ContractProjectionBaubleItem item)) return result;
        if (item.mode().weapon()) { result.add(Effect.WEAPON_PROJECTION); result.add(Effect.AUTO_WORK); }
        if (item.mode().armor()) result.add(Effect.ARMOR_PROJECTION);
        if (item.resonanceWaiver()) result.add(Effect.RESONANCE_WAIVER);
        return result;
    }
    public static boolean liveHas(Entity maid, Effect effect) {
        if (!TlmEntityAdapter.isMaidEntity(maid)) return false;
        try {
            Object value = maid.getClass().getMethod("getMaidBauble").invoke(maid);
            if (value instanceof IItemHandler slots) {
                for (int slot = 0; slot < slots.getSlots(); slot++)
                    if (effects(slots.getStackInSlot(slot)).contains(effect)) return true;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { }
        return false;
    }
    public static boolean hasWaiver(ResonanceContract context) {
        Entity maid = context.liveMaid();
        return maid != null ? liveHas(maid, Effect.RESONANCE_WAIVER) : StoredMaidBaubleResolver.hasWaiver(context);
    }
    private ContractBaubleEffects() { }
}
