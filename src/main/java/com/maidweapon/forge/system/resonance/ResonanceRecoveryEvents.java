package com.maidweapon.forge.system.resonance;

import com.maidweapon.common.MaidWeaponConfig;
import com.maidweapon.forge.compat.tlm.StoredMaidBaubleResolver;
import com.maidweapon.forge.system.contract.ContractChannelStorage;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Single natural recovery listener, enumerating all real independent channels once. */
@Mod.EventBusSubscriber
public final class ResonanceRecoveryEvents {
    private static final Map<UUID, Map<String, ResonanceAccumulator>> ACCUMULATORS = new HashMap<>();
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        var owner = event.player;
        if (event.phase != TickEvent.Phase.END || owner.level().isClientSide || !owner.isAlive()
                || owner.level().getGameTime() % 20 != 0) return;
        var accumulators = ACCUMULATORS.computeIfAbsent(owner.getUUID(), id -> new HashMap<>());
        var seen = new HashSet<String>();
        for (int slot = -1; slot < owner.getInventory().getContainerSize(); slot++) {
            var carrier = slot < 0 ? owner.containerMenu.getCarried() : owner.getInventory().getItem(slot);
            if (carrier.isEmpty()) continue;
            for (var channel : ContractChannelStorage.channels(carrier)) {
                var context = ResonanceContract.resolve(owner, carrier, channel.getString("MaidBindingId"));
                if (context == null || !seen.add(context.maid())) continue;
                var activity = ResonanceActivityTracker.current(context);
                double rate = ResonanceRecoveryPolicy.rate(context.liveMaid() != null, activity,
                        MaidWeaponConfig.RESONANCE_STORED_RECOVERY.get(),
                        MaidWeaponConfig.RESONANCE_PASSIVE_RECOVERY.get(),
                        MaidWeaponConfig.RESONANCE_ABILITY_IDLE_RECOVERY.get(),
                        MaidWeaponConfig.RESONANCE_COMBAT_RECOVERY.get(),
                        MaidWeaponConfig.RESONANCE_MANIFESTED_COMBAT_RECOVERY.get());
                long units = ResonanceRecoveryPolicy.perTick(rate * ResonanceService.legacyScale(),
                        ResonanceProgressService.multiplier(owner)) * 20;
                var pools = ResonancePoolSavedData.get(owner.getServer());
                int previous = pools.current(context);
                if (previous < 0) continue;
                int recovered = accumulators.computeIfAbsent(context.maid(), id -> new ResonanceAccumulator())
                        .tick(owner.getServer().overworld().getGameTime(), units, previous, ResonanceService.maximum());
                if (recovered > 0 || pools.rawCurrent(context) > ResonanceService.maximum())
                    pools.set(context, (int) Math.min(ResonanceService.maximum(), (long) previous + recovered));
                com.maidweapon.forge.network.ResonanceNetwork.sendIfChanged(owner, context.maid(),
                        pools.current(context), ResonanceService.maximum());
            }
        }
        // Keep fractions through item/channel switches, bounded per online owner.
        if (accumulators.size() > 512) accumulators.keySet().retainAll(seen);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID(); ACCUMULATORS.remove(id);
        ResonanceActivityTracker.clear(id); StoredMaidBaubleResolver.clear(id);
        com.maidweapon.forge.network.ResonanceNetwork.clear(id);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        ACCUMULATORS.clear(); ResonanceActivityTracker.clearAll(); StoredMaidBaubleResolver.clearAll();
        com.maidweapon.forge.network.ResonanceNetwork.clearAll();
    }
    private ResonanceRecoveryEvents() { }
}
