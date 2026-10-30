package com.maidweapon.forge.system.resonance;

import com.maidweapon.forge.system.contract.ContractCarrierData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Contract-wide billing: neither extra targets nor carrier/session switches multiply costs. */
@Mod.EventBusSubscriber
public final class ResonanceAttackCosts {
    private static final Map<UUID, Map<String, ResonancePaymentWindow>> WINDOWS = new HashMap<>();

    private static ResonancePaymentWindow window(ServerPlayer owner, ResonanceContract context,
                                                 SpiritAbilityResonanceCosts.Action action) {
        var windows = WINDOWS.computeIfAbsent(owner.getUUID(), id -> new HashMap<>());
        long now = owner.getServer().overworld().getGameTime();
        if (windows.size() >= 512) windows.values().removeIf(w -> !w.covers(now));
        return windows.computeIfAbsent(context.maid() + ":" + action.name(), id -> new ResonancePaymentWindow());
    }

    public static boolean affordable(ServerPlayer owner, ItemStack carrier, SpiritAbilityResonanceCosts.Action action) {
        var context = ResonanceContract.resolve(owner, carrier, ContractCarrierData.getBindingId(carrier));
        return context != null && (context.liveMaid() == null || com.maidweapon.forge.system.oath.OathService.burning(context))
                && (window(owner, context, action).covers(owner.getServer().overworld().getGameTime())
                    || SpiritAbilityResonanceCosts.affordable(owner, carrier, action));
    }

    public static boolean pay(ServerPlayer owner, ItemStack carrier, SpiritAbilityResonanceCosts.Action action, int ticks) {
        var context = ResonanceContract.resolve(owner, carrier, ContractCarrierData.getBindingId(carrier));
        if (context == null || (context.liveMaid() != null && !com.maidweapon.forge.system.oath.OathService.burning(context))) return false;
        boolean paid = window(owner, context, action).pay(owner.getServer().overworld().getGameTime(), ticks,
                () -> SpiritAbilityResonanceCosts.pay(owner, carrier, action));
        if (paid) ResonanceActivityTracker.report(context, "spirit_attack", ResonanceActivity.ABILITY_ATTACKING,
                Math.max(ticks, Math.max(1, Math.min(1200,
                        com.maidweapon.common.MaidWeaponConfig.RESONANCE_RECOVERY_DELAY.get()))));
        return paid;
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { WINDOWS.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { WINDOWS.clear(); }
    private ResonanceAttackCosts() { }
}
