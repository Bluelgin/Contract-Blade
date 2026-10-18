package com.maidweapon.forge.system.danmaku;

import com.maidweapon.forge.api.EmbeddedSpiritApi;
import com.maidweapon.forge.compat.tlm.ContractBaubleEffects;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.system.contract.ContractCarrierData;
import com.maidweapon.forge.system.fox.FoxSpiritState;
import com.maidweapon.forge.system.resonance.ResonanceContract;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * One authorization boundary: ordinary bound maid in the player's actual main-hand
 * weapon, validated native bauble, no intrinsic/fox spirit. No client metadata grants it.
 */
public final class DanmakuEligibility {
    public static ResonanceContract context(ServerPlayer owner) {
        if (owner == null || !owner.isAlive() || owner.isSpectator()
                || !net.minecraftforge.fml.ModList.get().isLoaded("touhou_little_maid")) return null;
        ItemStack carrier = owner.getMainHandItem();
        if (!MaidInfusion.isInfused(carrier) || FoxSpiritState.hasResident(carrier)) return null;
        String binding = ContractCarrierData.getBindingId(carrier);
        if (binding == null || binding.isBlank()) return null;
        var context = ResonanceContract.resolve(owner, carrier, binding);
        if (context == null || !context.channel().getString(EmbeddedSpiritApi.TAG_SPIRIT_ID).isBlank()) return null;
        return ContractBaubleEffects.has(context, ContractBaubleEffects.Effect.DANMAKU_ART) ? context : null;
    }
    private DanmakuEligibility() { }
}
