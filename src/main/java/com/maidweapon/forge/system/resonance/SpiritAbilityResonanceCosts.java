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
        BLACK_SUMMON(500), BLACK_UPKEEP(40), BLACK_MODE(100),
        WHITE_CLEANSE(50), WHITE_HEAL(70), WHITE_MAID_HEAL(50),
        WHITE_CRISIS(300), WHITE_OATH(600),
        RED_DOMAIN_NORMAL(15), RED_DOMAIN_RAMPAGE(35),
        RED_SPACE_CUT(100), RED_CORE_BURST(200),
        RED_LIMIT_BREAK(500), RED_STELLAR_FINISH(800);
        public final int basisPoints;
        Action(int basisPoints) { this.basisPoints = basisPoints; }
    }

    public static int scaled(int maximum, Action action) {
        if (maximum <= 0) return Integer.MAX_VALUE;
        return (int)Math.max(1L, Math.min(Integer.MAX_VALUE,
                ((long)maximum * action.basisPoints + 9999L) / 10000L));
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
        if (maximum <= 0 || basisPoints < 0 || basisPoints > 10000) return Integer.MAX_VALUE;
        if (basisPoints == 0) return 0;
        return (int)Math.min(Integer.MAX_VALUE, Math.max(1L,
                ((long)maximum * basisPoints + 9999) / 10000));
    }
    private SpiritAbilityResonanceCosts() { }
}
