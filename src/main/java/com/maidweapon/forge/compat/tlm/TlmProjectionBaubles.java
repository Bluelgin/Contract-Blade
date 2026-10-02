package com.maidweapon.forge.compat.tlm;

import com.maidweapon.forge.item.ContractProjectionBaubleItem;
import com.maidweapon.forge.system.deployment.ContractProjectionMode;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.items.IItemHandler;

/** Read only real native bauble slots, never the backpack, player or Curios. */
public final class TlmProjectionBaubles {
    public static ContractProjectionMode mode(Entity maid) {
        if (!TlmEntityAdapter.isMaidEntity(maid)) return ContractProjectionMode.NONE;
        try {
            Object value = maid.getClass().getMethod("getMaidBauble").invoke(maid);
            if (value instanceof IItemHandler slots) {
                for (int slot = 0; slot < slots.getSlots(); slot++) {
                    var stack = slots.getStackInSlot(slot);
                    if (!stack.isEmpty() && stack.getItem() instanceof ContractProjectionBaubleItem bauble) {
                        return bauble.mode();
                    }
                }
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Unsupported native versions fail closed: no free projections or task overrides.
        }
        return ContractProjectionMode.NONE;
    }

    private TlmProjectionBaubles() { }
}
