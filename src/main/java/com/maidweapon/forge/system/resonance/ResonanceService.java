package com.maidweapon.forge.system.resonance;

import com.maidweapon.common.data.MaidWeaponData;
import com.maidweapon.forge.compat.tlm.ContractBaubleEffects;
import com.maidweapon.forge.system.contract.ContractCarrierData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import static com.maidweapon.forge.system.resonance.ResonanceCostPolicy.Payment;

/** Stable server entry point. Each execution owns one PaymentAttempt; reuse cannot charge twice. */
public final class ResonanceService {
    public static final class PaymentAttempt {
        private boolean completed;
        private PaymentAttempt() { }
    }
    public static PaymentAttempt newAttempt() { return new PaymentAttempt(); }
    public static boolean succeeded(Payment payment) { return payment == Payment.PAID || payment == Payment.WAIVED; }
    public static int current(Player owner, ItemStack carrier, String binding) {
        var context = ResonanceContract.resolveIdentity(owner, carrier, binding);
        if (context == null) return 0;
        var data = pool(context);
        // Never import from an unverified compressed entity. Full decoding is required
        // only for the first legacy import; steady-state HUD/eligibility reads remain cheap.
        if (!data.hasRecord(context)) context = ResonanceContract.resolve(owner, carrier, binding);
        return context == null ? 0 : Math.max(0, pool(context).current(context));
    }
    public static int maximum() { return ResonancePoolSavedData.maximum(); }
    private static ResonancePoolSavedData pool(ResonanceContract context) {
        return ResonancePoolSavedData.get(context.owner().getServer());
    }
    private static void sync(ResonanceContract context) {
        com.maidweapon.forge.network.ResonanceNetwork.sendIfChanged(context.owner(), context.maid(),
                pool(context).current(context), maximum());
    }
    /** Old config remains the migration denominator; new pools are independently configurable. */
    public static double legacyScale() {
        return (double) maximum() / Math.max(1,
                com.maidweapon.common.ContractRulesConfig.MAX_RESONANCE.get());
    }
    /** Scale old costs to preserve the pre-pool gameplay balance. */
    public static int legacyUnits(int cost) {
        if (cost <= 0) return 0;
        return (int) Math.min(Integer.MAX_VALUE, Math.round(cost * legacyScale()));
    }
    public static double recoveryMultiplier(Player owner) {
        return owner != null && owner.getServer() != null && !owner.level().isClientSide && owner.getServer().isSameThread()
                ? ResonanceProgressService.multiplier(owner) : 1;
    }
    public static boolean waived(Player owner, ItemStack carrier) {
        return carrier != null && waived(owner, carrier, ContractCarrierData.getBindingId(carrier));
    }
    public static boolean waived(Player owner, ItemStack carrier, String binding) {
        var context = ResonanceContract.resolve(owner, carrier, binding);
        return context != null && pool(context).current(context) >= 0
                && ContractBaubleEffects.hasWaiver(context);
    }
    public static Payment canPay(Player owner, ItemStack carrier, String binding, int cost) {
        var context = ResonanceContract.resolve(owner, carrier, binding);
        return decision(context, cost);
    }
    private static Payment decision(ResonanceContract context, int cost) {
        int available = context == null ? -1 : pool(context).current(context);
        return ResonanceCostPolicy.evaluate(available >= 0,
                available >= 0 && ContractBaubleEffects.hasWaiver(context), available, cost);
    }
    public static Payment pay(Player owner, ItemStack carrier, String binding, int cost, PaymentAttempt attempt) {
        var context = ResonanceContract.resolve(owner, carrier, binding);
        if (context == null || attempt == null) return Payment.UNAUTHORIZED;
        if (attempt.completed) return Payment.ALREADY_PAID;
        Payment result = decision(context, cost);
        if (result == Payment.PAID) {
            int before = pool(context).current(context);
            if (before < 0 || !pool(context).set(context, before - cost)) return Payment.UNAUTHORIZED;
            sync(context);
        }
        if (succeeded(result)) attempt.completed = true;
        return result;
    }
    /** Existing mandatory upkeep/damage costs drain to zero even when the full amount is unavailable. */
    public static void drain(Player owner, ItemStack carrier, int cost) {
        if (carrier == null) return;
        var context = ResonanceContract.resolve(owner, carrier, ContractCarrierData.getBindingId(carrier));
        if (context == null || cost <= 0 || ContractBaubleEffects.hasWaiver(context)) return;
        int before = pool(context).current(context);
        if (before >= 0 && pool(context).set(context, before - cost)) sync(context);
    }
    /** Already resolved server authority, used only by the oath session tick. Never waived. */
    public static void burnOath(ResonanceContract context, int cost) {
        if (cost <= 0 || !com.maidweapon.forge.system.oath.OathService.burning(context)) return;
        int current = pool(context).current(context);
        if (current >= 0 && pool(context).set(context, Math.max(0, current - cost))) sync(context);
    }
    public static boolean recover(Player owner, ItemStack carrier, String binding, int amount) {
        var context = ResonanceContract.resolve(owner, carrier, binding);
        if (context == null || amount <= 0) return false;
        int before = pool(context).current(context);
        if (before < 0 || !pool(context).set(context, (int) Math.min(maximum(), (long) before + amount))) return false;
        sync(context);
        return true;
    }
    /** Legacy gameplay events still specify costs in the old resource units. */
    public static boolean recoverLegacy(Player owner, ItemStack carrier, String binding, int amount) {
        return recover(owner, carrier, binding, legacyUnits(amount));
    }
    public static void drainLegacy(Player owner, ItemStack carrier, int amount) {
        drain(owner, carrier, legacyUnits(amount));
    }
    /** Explicit operator/test adjustment; never changes the historical migration source. */
    public static boolean set(Player owner, ItemStack carrier, String binding, int amount) {
        var context = ResonanceContract.resolve(owner, carrier, binding);
        if (context == null || !pool(context).set(context, amount)) return false;
        sync(context);
        return true;
    }
    public static ResonanceActivity activity(Player owner, ItemStack carrier, String binding) {
        var context = ResonanceContract.resolve(owner, carrier, binding);
        return context == null ? null : ResonanceActivityTracker.current(context);
    }
    public static boolean reportActivity(Player owner, ItemStack carrier, String binding, String source,
                                          ResonanceActivity activity, int ticks) {
        var context = ResonanceContract.resolve(owner, carrier, binding);
        if (context == null || activity == null) return false;
        ResonanceActivityTracker.report(context, source, activity, ticks); return true;
    }
    private ResonanceService() { }
}
