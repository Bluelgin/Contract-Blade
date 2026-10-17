package com.maidweapon.forge.system.resonance;

import com.maidweapon.common.MaidWeaponConfig;
import com.maidweapon.forge.compat.tlm.StoredMaidBaubleResolver;
import com.maidweapon.forge.system.contract.ContractChannelStorage;
import net.minecraft.world.entity.Mob;
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
        if (event.phase != TickEvent.Phase.END || owner.level().isClientSide || !owner.isAlive()) return;
        var accumulators = ACCUMULATORS.computeIfAbsent(owner.getUUID(), id -> new HashMap<>());
        var seen = new HashSet<String>();
        for (int slot = -1; slot < owner.getInventory().getContainerSize(); slot++) {
            var carrier = slot < 0 ? owner.containerMenu.getCarried() : owner.getInventory().getItem(slot);
            if (carrier.isEmpty()) continue;
            for (var channel : ContractChannelStorage.channels(carrier)) {
                var context = ResonanceContract.resolve(owner, carrier, channel.getString("MaidBindingId"));
                if (context == null || !seen.add(context.maid())) continue;
                var activity = ResonanceActivityTracker.current(context);
                double rate = switch (activity) {
                    case STORED -> MaidWeaponConfig.RESONANCE_STORED_RECOVERY.get();
                    case MANIFESTED_IDLE -> MaidWeaponConfig.RESONANCE_PASSIVE_RECOVERY.get();
                    case ABILITY_IDLE -> MaidWeaponConfig.RESONANCE_ABILITY_IDLE_RECOVERY.get();
                    case COMBAT, ABILITY_ATTACKING, BERSERK -> MaidWeaponConfig.RESONANCE_COMBAT_RECOVERY.get();
                };
                long units = ResonanceRecoveryPolicy.perTick(rate, ResonanceProgressService.multiplier(owner));
                var data = context.data();
                int raw = context.channel().getCompound("MaidData").getInt("ContractResonance");
                int recovered = accumulators.computeIfAbsent(context.maid(), id -> new ResonanceAccumulator())
                        .tick(owner.getServer().overworld().getGameTime(), units, data.getResonance(), ResonanceService.maximum());
                if (recovered > 0 || raw > ResonanceService.maximum()) {
                    data.addResonance(recovered); context.write(data);
                }
            }
        }
        // Keep fractions through item/channel switches, bounded per online owner.
        if (accumulators.size() > 512) accumulators.keySet().retainAll(seen);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID(); ACCUMULATORS.remove(id);
        ResonanceActivityTracker.clear(id); StoredMaidBaubleResolver.clear(id);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        ACCUMULATORS.clear(); ResonanceActivityTracker.clearAll(); StoredMaidBaubleResolver.clearAll();
    }
    private ResonanceRecoveryEvents() { }
}
