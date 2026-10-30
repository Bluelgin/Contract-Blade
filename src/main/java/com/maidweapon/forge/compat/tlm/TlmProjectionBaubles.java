package com.maidweapon.forge.compat.tlm;

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
                var effects = ContractBaubleEffects.resolveSlots(slots);
                boolean weapon = effects.contains(ContractBaubleEffects.Effect.WEAPON_PROJECTION);
                boolean armor = effects.contains(ContractBaubleEffects.Effect.ARMOR_PROJECTION);
                return weapon && armor ? ContractProjectionMode.COMBINED
                        : weapon ? ContractProjectionMode.WEAPON : armor ? ContractProjectionMode.ARMOR : ContractProjectionMode.NONE;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Unsupported native versions fail closed: no free projections or task overrides.
        }
        return ContractProjectionMode.NONE;
    }

    private TlmProjectionBaubles() { }
}
