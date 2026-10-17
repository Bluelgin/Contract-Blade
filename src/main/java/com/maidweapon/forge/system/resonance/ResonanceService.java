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
        var context = ResonanceContract.resolve(owner, carrier, binding); return context == null ? 0 : context.data().getResonance();
    }
    public static int maximum() { return MaidWeaponData.maximumResonance(); }
    public static double recoveryMultiplier(Player owner) {
        return owner != null && owner.getServer() != null && !owner.level().isClientSide && owner.getServer().isSameThread()
                ? ResonanceProgressService.multiplier(owner) : 1;
    }
    public static boolean waived(Player owner, ItemStack carrier) {
        return waived(owner, carrier, ContractCarrierData.getBindingId(carrier));
    }
    public static boolean waived(Player owner, ItemStack carrier, String binding) {
        var context = ResonanceContract.resolve(owner, carrier, binding);
        return context != null && ContractBaubleEffects.hasWaiver(context);
    }
    public static Payment canPay(Player owner, ItemStack carrier, String binding, int cost) {
        var context = ResonanceContract.resolve(owner, carrier, binding);
        return decision(context, cost);
    }
    private static Payment decision(ResonanceContract context, int cost) {
        return ResonanceCostPolicy.evaluate(context != null, context != null && ContractBaubleEffects.hasWaiver(context),
                context == null ? 0 : context.data().getResonance(), cost);
    }
    public static Payment pay(Player owner, ItemStack carrier, String binding, int cost, PaymentAttempt attempt) {
        var context = ResonanceContract.resolve(owner, carrier, binding);
        if (context == null || attempt == null) return Payment.UNAUTHORIZED;
        if (attempt.completed) return Payment.ALREADY_PAID;
        Payment result = decision(context, cost);
        if (result == Payment.PAID) {
            var data = context.data(); data.reduceResonance(cost); context.write(data);
        }
        if (succeeded(result)) attempt.completed = true;
        return result;
    }
    /** Existing mandatory upkeep/damage costs drain to zero even when the full amount is unavailable. */
    public static void drain(Player owner, ItemStack carrier, int cost) {
        var context = ResonanceContract.resolve(owner, carrier, ContractCarrierData.getBindingId(carrier));
        if (context == null || cost <= 0 || ContractBaubleEffects.hasWaiver(context)) return;
        var data = context.data(); data.reduceResonance(cost); context.write(data);
    }
    public static boolean recover(Player owner, ItemStack carrier, String binding, int amount) {
        var context = ResonanceContract.resolve(owner, carrier, binding);
        if (context == null || amount <= 0) return false;
        var data = context.data(); data.addResonance(Math.min(amount, maximum() - data.getResonance())); context.write(data); return true;
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
