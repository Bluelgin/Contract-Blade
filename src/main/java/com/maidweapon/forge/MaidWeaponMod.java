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
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();

        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(
                net.minecraftforge.fml.config.ModConfig.Type.COMMON, MaidWeaponConfig.SPEC);
        ModItems.ITEMS.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModRecipeSerializers.RECIPE_SERIALIZERS.register(modBus);
        ModCreativeTab.TABS.register(modBus);
        TaczCompat.bootstrap(modBus);

        modBus.addListener(this::commonSetup);

        LOGGER.info("[{}] Loading Contract Blade Core {}", MaidWeaponConstants.MOD_ID,
                MaidWeaponConstants.MOD_VERSION);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LOGGER.info("[MaidWeapon] TLM compatibility: {}", TlmReflection.diagnostics());
            LOGGER.info("[MaidWeapon] SlashBlade compatibility: {}", SlashBladeCompat.diagnostics());
            LOGGER.info("[MaidWeapon] TACZ compatibility: {}",
                    TaczCompat.isLoaded() ? "enabled" : "not installed");
        });
    }
}
