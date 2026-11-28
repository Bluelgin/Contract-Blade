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

/** Native platform entry point. Incomplete development build; legacy-world import is not enabled. */
@Mod(ContractBladeMod.ID)
public final class ContractBladeMod {
    public static final String ID = "maid_weapon";
    public static final String FIXTURE_ID = "maid_weapon_port_probe";
    private static final Logger LOG = LogUtils.getLogger();

    public ContractBladeMod(IEventBus bus, ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON,com.maidweapon.port.config.MaidWeaponConfig.SPEC,"maid_weapon-common.toml");
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER,com.maidweapon.port.config.ContractRulesConfig.SPEC,"maid_weapon-server.toml");
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER,com.maidweapon.port.config.OathConfig.SPEC,"maid_weapon-oath-server.toml");
        PortDataTypes.COMPONENTS.register(bus);
        PortDataTypes.ATTACHMENTS.register(bus);
        ProbeItems.ITEMS.register(bus);
        com.maidweapon.port.content.NativeContent.register(bus);
        com.maidweapon.port.fox.FoxModelPackBootstrap.install();
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST,com.maidweapon.port.tlm.NativeSoulSlabService::interact);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST,com.maidweapon.port.tlm.NativeSoulSlabService::captureInteraction);
        com.maidweapon.port.tlm.NativeIntrinsicSpirits.registerProbe();
        com.maidweapon.port.ability.progression.NativeGrowthBootstrap.register();
        NeoForge.EVENT_BUS.addListener(com.maidweapon.port.ability.progression.ContractAbilityReloadListener::register);
        NeoForge.EVENT_BUS.addListener(com.maidweapon.port.ability.progression.ContractAbilityReloadListener::stopped);
        bus.addListener(PortStatusNetwork::register);
        NeoForge.EVENT_BUS.addListener(NativeRecoveryDiagnostics::register);
        NeoForge.EVENT_BUS.addListener(NativeRecoveryTick::post);
        NeoForge.EVENT_BUS.addListener(com.maidweapon.port.fox.NativeWhiteFoxService::tick);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,com.maidweapon.port.fox.NativeWhiteFoxService::beforeDamage);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,com.maidweapon.port.fox.NativeWhiteFoxService::afterDamage);
        NeoForge.EVENT_BUS.addListener(com.maidweapon.port.fox.NativeWhiteFoxService::logout);
        NeoForge.EVENT_BUS.addListener(com.maidweapon.port.fox.NativeWhiteFoxService::death);
        NeoForge.EVENT_BUS.addListener(com.maidweapon.port.fox.NativeWhiteFoxService::dimension);
        NeoForge.EVENT_BUS.addListener(com.maidweapon.port.fox.NativeWhiteFoxService::stop);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST,NativeOathService::tick);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST,NativeOathService::damage);
        NeoForge.EVENT_BUS.addListener(NativeOathService::logout);
        NeoForge.EVENT_BUS.addListener(NativeOathService::dimension);
        NeoForge.EVENT_BUS.addListener(NativeOathService::death);
        NeoForge.EVENT_BUS.addListener(NativeOathService::stop);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,com.maidweapon.port.recovery.NativeCheckpointService::stop);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, NativeCombatCredits::damaged);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, NativeCombatCredits::death);
        NeoForge.EVENT_BUS.addListener(NativeTlmRoundTripCommand::register);
        LOG.warn("Contract Blade native development build loaded; gameplay migration is incomplete. Do not upgrade existing worlds.");
    }
}
