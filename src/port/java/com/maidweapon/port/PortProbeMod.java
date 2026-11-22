package com.maidweapon.port;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/** Quarantined API probe. NOT the real maid_weapon mod. */
@Mod(PortProbeMod.ID)
public final class PortProbeMod {
    public static final String ID = "maid_weapon_port_probe";
    private static final Logger LOG = LogUtils.getLogger();

    public PortProbeMod(IEventBus bus, ModContainer container) {
        LOG.warn("Contract Blade 1.21.1 PORT PROBE; gameplay and world migration are NOT implemented.");
    }
}
