package com.maidweapon.forge.system.resonance;

import com.maidweapon.forge.system.contract.ContractCarrierData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Three spirit routes share ONE server-owned maid pool, but have distinct prices.
 * Units are basis points of pool maximum (100 = 1%). No free charges at tiny caps.
 * This class owns costs, not gameplay authorization: the owning ability must validate
 * its real carrier, original sword/spirit, target and activation first.
 */
public final class SpiritAbilityResonanceCosts {
    public enum Action {
        BLACK_SUMMON, BLACK_UPKEEP, BLACK_MODE,
        WHITE_CLEANSE, WHITE_HEAL, WHITE_MAID_HEAL, WHITE_CRISIS, WHITE_OATH,
        RED_DOMAIN_NORMAL, RED_DOMAIN_RAMPAGE, RED_SPACE_CUT, RED_CORE_BURST, RED_LIMIT_BREAK, RED_STELLAR_FINISH;
        public final int basisPoints = com.maidweapon.port.domain.resonance.SpiritCosts.Action.valueOf(name()).basisPoints;
    }

    public static int scaled(int maximum, Action action) {
        return scaledPoints(maximum, action.basisPoints);
    }
    public static int cost(Action action) { return scaled(ResonanceService.maximum(), action); }

    public static boolean affordable(ServerPlayer owner, ItemStack carrier, Action action) {
        return affordable(owner, carrier, action.basisPoints);
    }
    public static boolean affordable(ServerPlayer owner, ItemStack carrier, int basisPoints) {
        if (owner == null || carrier == null || carrier.isEmpty()) return false;
        int amount = scaledPoints(ResonanceService.maximum(), basisPoints);
        return amount != Integer.MAX_VALUE && ResonanceService.succeeded(ResonanceService.canPay(
                owner, carrier, ContractCarrierData.getBindingId(carrier), amount));
    }
    public static boolean pay(ServerPlayer owner, ItemStack carrier, Action action) {
        return payPoints(owner, carrier, action.basisPoints);
    }
    /** One atomic debit for multi-part events, avoiding partial charges. */
    public static boolean payPoints(ServerPlayer owner, ItemStack carrier, int basisPoints) {
        if (owner == null || carrier == null || carrier.isEmpty()) return false;
        int amount = scaledPoints(ResonanceService.maximum(), basisPoints);
        return amount != Integer.MAX_VALUE && ResonanceService.succeeded(ResonanceService.pay(
                owner, carrier, ContractCarrierData.getBindingId(carrier), amount,
                ResonanceService.newAttempt()));
    }
    public static int scaledPoints(int maximum, int basisPoints) {
        return com.maidweapon.port.domain.resonance.SpiritCosts.scaledPoints(maximum, basisPoints);
    }
    private SpiritAbilityResonanceCosts() { }
}
