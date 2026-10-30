package com.maidweapon.forge.system.deployment;

import com.maidweapon.common.ContractRulesConfig;
import com.maidweapon.forge.compat.tlm.ContractBaubleEffects;
import com.maidweapon.forge.system.contract.ContractCarrierData;
import com.maidweapon.forge.system.resonance.ResonanceContract;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Qualification only; companion lifecycle still owns injury, cooldown, slots and actual spawning. */
public final class ContractGuardPolicy {
    public static boolean allows(Player owner, ItemStack carrier) {
        String mode = ContractRulesConfig.autoManifestMode();
        if (mode.equals("disabled")) return false;
        if (mode.equals("always")) return true;
        var c = ResonanceContract.resolve(owner, carrier, ContractCarrierData.getBindingId(carrier));
        return ContractBaubleEffects.has(c, ContractBaubleEffects.Effect.AUTO_GUARD);
    }
    private ContractGuardPolicy() { }
}
