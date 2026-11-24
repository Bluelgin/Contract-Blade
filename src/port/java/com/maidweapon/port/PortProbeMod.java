package com.maidweapon.port;

import com.mojang.logging.LogUtils;
import com.maidweapon.port.init.ProbeItems;
import com.maidweapon.port.network.PortStatusNetwork;
import com.maidweapon.port.recovery.NativeRecoveryDiagnostics;
import com.maidweapon.port.resonance.NativeRecoveryTick;
import com.maidweapon.port.mastery.NativeCombatCredits;
import com.maidweapon.port.oath.NativeOathService;
import com.maidweapon.port.init.PortDataTypes;
import com.maidweapon.port.tlm.NativeTlmRoundTripCommand;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/** Quarantined API probe. NOT the real maid_weapon mod. */
@Mod(PortProbeMod.ID)
public final class PortProbeMod {
    public static final String ID = "maid_weapon_port_probe";
    private static final Logger LOG = LogUtils.getLogger();

    public PortProbeMod(IEventBus bus, ModContainer container) {
        PortDataTypes.COMPONENTS.register(bus);
        PortDataTypes.ATTACHMENTS.register(bus);
        ProbeItems.ITEMS.register(bus);
        bus.addListener(PortStatusNetwork::register);
        NeoForge.EVENT_BUS.addListener(NativeRecoveryDiagnostics::register);
        NeoForge.EVENT_BUS.addListener(NativeRecoveryTick::post);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST,NativeOathService::tick);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST,NativeOathService::damage);
        NeoForge.EVENT_BUS.addListener(NativeOathService::logout);
        NeoForge.EVENT_BUS.addListener(NativeOathService::dimension);
        NeoForge.EVENT_BUS.addListener(NativeOathService::death);
        NeoForge.EVENT_BUS.addListener(NativeOathService::stop);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, NativeCombatCredits::damaged);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, NativeCombatCredits::death);
        NeoForge.EVENT_BUS.addListener(NativeTlmRoundTripCommand::register);
        LOG.warn("Contract Blade NeoForge port probe loaded; this build contains NO release gameplay.");
    }
}
