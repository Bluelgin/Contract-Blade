package com.maidweapon.forge.client;

import com.maidweapon.common.MaidWeaponConstants;
import com.maidweapon.forge.init.ModMenus;
import com.maidweapon.forge.compat.tlm.TlmPecoVoiceBridge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = MaidWeaponConstants.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientRegistration {
    @SubscribeEvent
    public static void registerScreens(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.MAID_INJECTOR.get(), MaidInjectorScreen::new);
            // The sound adapter has optional TLM client classes. Do not register
            // or resolve its event handler on profiles without Touhou Little Maid.
            if (ModList.get().isLoaded("touhou_little_maid"))
                {
                    MinecraftForge.EVENT_BUS.register(TlmPecoVoiceBridge.class);
                    MinecraftForge.EVENT_BUS.register(
                            com.maidweapon.forge.compat.tlm.TlmPecoVoicePreviewCommands.class);
                }
        });
    }

    private ClientRegistration() {
    }
}
