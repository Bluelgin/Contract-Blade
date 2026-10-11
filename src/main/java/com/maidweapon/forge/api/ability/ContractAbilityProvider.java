package com.maidweapon.forge.api.ability;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Implement in your mod; do not register/link optional mod classes until that mod is installed. */
@FunctionalInterface
public interface ContractAbilityProvider {
    /** Verify actual ability ID on this carrier AND independently earned entitlement (quest/victory/origin). */
    boolean entitled(ServerPlayer player, ItemStack carrier);
}
