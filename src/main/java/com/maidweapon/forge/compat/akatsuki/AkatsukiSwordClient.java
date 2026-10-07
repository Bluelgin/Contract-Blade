package com.maidweapon.forge.compat.akatsuki;

import com.maidweapon.common.MaidWeaponConstants;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;

@Mod.EventBusSubscriber(modid=MaidWeaponConstants.MOD_ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class AkatsukiSwordClient {
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
        if(supportedProvider())
            event.registerReloadListener((ResourceManagerReloadListener)manager->AkatsukiSwordplay.clear());
    }
    private static boolean supportedProvider() {
        return ModList.get().getModContainerById("touhou_little_maid")
                .map(mod->mod.getModInfo().getVersion().compareTo(
                        new org.apache.maven.artifact.versioning.DefaultArtifactVersion("1.5.3"))>=0)
                .orElse(false);
    }
    private AkatsukiSwordClient() { }
    @Mod.EventBusSubscriber(modid=MaidWeaponConstants.MOD_ID,value=Dist.CLIENT)
    public static final class WorldEvents {
        @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            if(supportedProvider())AkatsukiSwordplay.clear();
        }
    }
}
