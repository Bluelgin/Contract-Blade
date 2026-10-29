package com.maidweapon.forge.system.oath;

import com.maidweapon.common.OathConfig;
import com.maidweapon.forge.compat.tlm.ContractBaubleEffects;
import com.maidweapon.forge.system.contract.ContractCarrierData;
import com.maidweapon.forge.system.contract.ContractChannelStorage;
import com.maidweapon.forge.system.deployment.*;
import com.maidweapon.forge.system.resonance.*;
import com.maidweapon.forge.system.InfusedMaidDeploymentSystem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** One server-thread session per owner. Interruption safely terminates without draining or backlash.
 * Crash/restart markers never grant buffs: login closes them through the same ending boundary.
 */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class OathService {
    private static final Set<UUID> ONLINE = new HashSet<>();
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static CompoundTag session(Player owner) { return OathSavedData.get(owner.getServer()).session(owner.getUUID()); }
    public static boolean active(Player owner) {
        return owner != null && !owner.level().isClientSide && owner.getServer() != null
                && ONLINE.contains(owner.getUUID()) && session(owner).getBoolean("Active");
    }
    public static boolean burning(ResonanceContract c) {
        return c != null && active(c.owner()) && c.maid().equals(session(c.owner()).getString("Maid"));
    }
    public static String binding(Player owner) { return active(owner) ? session(owner).getString("Binding") : ""; }
    public static boolean empowered(Entity maid) {
        if (!(maid instanceof TamableAnimal animal) || animal.getOwnerUUID() == null || maid.getServer() == null) return false;
        var owner = maid.getServer().getPlayerList().getPlayer(animal.getOwnerUUID());
        if (!active(owner)) return false;
        var state = session(owner);
        return maid.getStringUUID().equals(state.getString("Maid"))
                && state.getString("Binding").equals(maid.getPersistentData().getString(
                    com.maidweapon.forge.compat.tlm.ContractMaidKeys.ENTITY_BINDING_ID));
    }
    public static void activate(Player owner) {
        if (!(owner instanceof ServerPlayer) || !owner.isAlive() || owner.isSpectator() || active(owner)
                || owner.containerMenu != owner.inventoryMenu) return;
        ResonanceContract selected = null;
        var seen = new HashSet<String>();
        for (int i = 0; i < owner.getInventory().getContainerSize(); i++) {
            ItemStack carrier = owner.getInventory().getItem(i);
            for (var channel : ContractChannelStorage.channels(carrier)) {
                var c = ResonanceContract.resolve(owner, carrier, channel.getString("MaidBindingId"));
                if (c == null || c.liveMaid() == null || ContractCompanionService.isResident(owner, carrier)
                        || !seen.add(c.maid())) continue;
                if (selected != null) { message(owner, "one_companion"); return; }
                selected = c;
            }
        }
        if (selected == null || selected.liveMaid().level() != owner.level()
                || !ContractBaubleEffects.has(selected, ContractBaubleEffects.Effect.OATH_MANIFESTATION)
                || ResonanceService.current(owner, selected.carrier(), selected.binding()) <= 0) {
            message(owner, "unavailable"); return;
        }
        var state = new CompoundTag();
        state.putBoolean("Active", true); state.putString("Maid", selected.maid()); state.putString("Binding", selected.binding());
        OathSavedData.get(owner.getServer()).put(owner.getUUID(), state); ONLINE.add(owner.getUUID());
        com.maidweapon.forge.network.ResonanceNetwork.sendIfChanged(owner, selected.maid(),
                ResonanceService.current(owner, selected.carrier(), selected.binding()), ResonanceService.maximum());
        com.maidweapon.forge.network.OathNetwork.send(owner, selected.maid(), true);
        // A single immediate cue is sent even when only one resource point remains.
        if (selected.liveMaid().level() instanceof net.minecraft.server.level.ServerLevel level) {
            var maid = selected.liveMaid();
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, maid.getX(), maid.getY() + 1,
                    maid.getZ(), 30, .6, .8, .6, .03);
            level.playSound(null, maid.blockPosition(), net.minecraft.sounds.SoundEvents.TOTEM_USE,
                    net.minecraft.sounds.SoundSource.PLAYERS, .65f, 1.3f);
        }
        message(owner, "started");
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void tick(TickEvent.PlayerTickEvent e) {
        var owner = e.player;
        if (e.phase != TickEvent.Phase.START || owner.level().isClientSide || !active(owner)) return;
        var state = session(owner);
        var carrier = ContractWeaponLocator.findBoundWeaponByBinding(owner, state.getString("Binding"));
        var c = ResonanceContract.resolve(owner, carrier, state.getString("Binding"));
        if (!owner.isAlive() || c == null || !state.getString("Maid").equals(c.maid()) || c.liveMaid() == null
                || c.liveMaid().level() != owner.level() || ContractCompanionService.isResident(owner, carrier)) {
            end(owner, "identity_or_lifecycle_interrupted", false); return;
        }
        int current = ResonanceService.current(owner, carrier, c.binding());
        if (current == 0) { end(owner, "burned_out", true); return; }
        var step = OathBurn.tick(ResonanceService.maximum(), OathConfig.BURN.get(), state.getLong("Remainder"));
        state.putLong("Remainder", step.remainder()); OathSavedData.get(owner.getServer()).put(owner.getUUID(), state);
        // Dedicated resource operation bypasses waiver and recovery multipliers.
        ResonanceService.burnOath(c, step.cost());
        if (ResonanceService.current(owner, carrier, c.binding()) == 0) end(owner, "burned_out", true);
    }
    /** Clear first, so nested recall/failure hooks and later ticks cannot settle twice. */
    public static void end(Player owner, String reason, boolean exhausted) {
        if (owner == null || owner.getServer() == null) return;
        var state = session(owner);
        if (!state.getBoolean("Active")) return;
        state.putBoolean("Active", false); state.putString("LastOutcome", reason);
        state.putLong("EndedAt", owner.getServer().overworld().getGameTime());
        OathSavedData.get(owner.getServer()).put(owner.getUUID(), state); ONLINE.remove(owner.getUUID());
        com.maidweapon.forge.network.OathNetwork.send(owner, state.getString("Maid"), false);
        var carrier = ContractWeaponLocator.findBoundWeaponByBinding(owner, state.getString("Binding"));
        boolean recalled = !carrier.isEmpty() && InfusedMaidDeploymentSystem.forceRecall(owner, state.getString("Maid"), carrier, 0);
        // Existing recovery journal remains authoritative if the entity/carrier is unloaded or transferred.
        if (exhausted && owner.isAlive()) {
            var old = owner.getEffect(OathEffects.BACKLASH.get());
            int amplifier = old == null ? 0 : Math.min(254, old.getAmplifier() + 1);
            owner.removeEffect(OathEffects.BACKLASH.get());
            owner.addEffect(new MobEffectInstance(OathEffects.BACKLASH.get(), OathConfig.BACKLASH_DURATION.get(), amplifier));
        }
        LOG.info("Oath ended owner={} maid={} reason={} exhausted={} recalled={}", owner.getUUID(), state.getString("Maid"), reason, exhausted, recalled);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!e.getEntity().level().isClientSide) end(e.getEntity(), "disconnect", false);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        // No offline catch-up, fresh session, resource reset, or deferred backlash after death.
        end(e.getEntity(), "restart_recovery", false);
        ONLINE.remove(e.getEntity().getUUID());
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void death(LivingDeathEvent e) {
        if (!e.getEntity().level().isClientSide && e.getEntity() instanceof Player p) end(p, "death", false);
    }
    @SubscribeEvent public static void stop(ServerStoppingEvent e) {
        for (var player : e.getServer().getPlayerList().getPlayers()) end(player, "server_stopping", false);
        ONLINE.clear();
    }
    private static void message(Player p, String key) { p.displayClientMessage(net.minecraft.network.chat.Component.translatable("maid_weapon.oath." + key), true); }
    private OathService() { }
}
