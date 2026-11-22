package com.maidweapon.port;

import com.mojang.logging.LogUtils;
import com.maidweapon.port.init.ProbeItems;
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
        ProbeItems.ITEMS.register(bus);
        NeoForge.EVENT_BUS.addListener(NativeTlmRoundTripCommand::register);
        LOG.warn("Contract Blade NeoForge port probe loaded; this build contains NO release gameplay.");
    }
}
