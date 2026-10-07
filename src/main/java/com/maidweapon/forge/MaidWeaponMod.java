package com.maidweapon.forge;

import com.maidweapon.common.MaidWeaponConfig;
import com.maidweapon.common.MaidWeaponConstants;
import com.maidweapon.forge.compat.SlashBladeCompat;
import com.maidweapon.forge.compat.TaczCompat;
import com.maidweapon.forge.compat.TlmReflection;
import com.maidweapon.forge.init.ModBlocks;
import com.maidweapon.forge.init.ModCreativeTab;
import com.maidweapon.forge.init.ModItems;
import com.maidweapon.forge.init.ModMenus;
import com.maidweapon.forge.init.ModRecipeSerializers;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Forge bootstrap for Contract Blade Core.
 *
 * <p>Gameplay interaction events are deliberately owned by the item/event
 * handlers and {@code ContractInteractionService}. The bootstrap only wires
 * registries, configuration and compatibility diagnostics.</p>
 */
@Mod(MaidWeaponConstants.MOD_ID)
public final class MaidWeaponMod {
    private static final Logger LOGGER = LogUtils.getLogger();

    public MaidWeaponMod() {
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.CLIENT,
                com.maidweapon.common.AkatsukiClientConfig.SPEC, "maid_weapon-akatsuki-client.toml");
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.CLIENT,
                com.maidweapon.common.BlackFoxClientConfig.SPEC, "maid_weapon-client.toml");
        com.maidweapon.forge.api.ContractAuthorization.register("maid_weapon:fox_spirit",
                com.maidweapon.forge.system.fox.FoxSpiritTransferService::authorizesContract);
        com.maidweapon.forge.compat.fox.FoxModelPackBootstrap.install();
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();

        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(
                net.minecraftforge.fml.config.ModConfig.Type.COMMON, MaidWeaponConfig.SPEC);
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(
                net.minecraftforge.fml.config.ModConfig.Type.SERVER,
                com.maidweapon.common.ContractRulesConfig.SPEC, "maid_weapon-server.toml");
        ModItems.ITEMS.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModRecipeSerializers.RECIPE_SERIALIZERS.register(modBus);
        ModCreativeTab.TABS.register(modBus);
        com.maidweapon.forge.worldgen.ShrineWorldgen.register(modBus);
        com.maidweapon.forge.network.ContractCompanionNetwork.register();
        TaczCompat.bootstrap(modBus);
        com.maidweapon.forge.compat.WhiteFoxSpecialEffectCompat.bootstrap(modBus);
        com.maidweapon.forge.compat.fox.BlackFoxBossCompat.bootstrap(modBus);

        modBus.addListener(this::commonSetup);

        LOGGER.info("[{}] Loading Contract Blade Core {}", MaidWeaponConstants.MOD_ID,
                MaidWeaponConstants.MOD_VERSION);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            if (net.minecraftforge.fml.ModList.get().isLoaded("touhou_little_maid"))
                com.maidweapon.forge.compat.tlm.ContractMaidRebirthBridge.register();
            com.maidweapon.forge.system.fox.challenge.BlackFoxEncounters.setup();
            com.maidweapon.forge.compat.BlackFoxSlashCompat.bootstrap();
            LOGGER.info("[MaidWeapon] TLM compatibility: {}", TlmReflection.diagnostics());
            LOGGER.info("[MaidWeapon] SlashBlade compatibility: {}", SlashBladeCompat.diagnostics());
            LOGGER.info("[MaidWeapon] TACZ compatibility: {}",
                    TaczCompat.isLoaded() ? "enabled" : "not installed");
        });
    }
}
