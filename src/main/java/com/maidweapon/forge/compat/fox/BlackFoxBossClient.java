package com.maidweapon.forge.compat.fox;

import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

/** Private renderer and resource lifecycle; does not register a maid model or global animation state. */
public final class BlackFoxBossClient {
    public static void bootstrap(IEventBus bus) {
        bus.addListener(BlackFoxBossClient::renderers);
        bus.addListener(BlackFoxBossClient::reloads);
        bus.addListener(BlackFoxBossClient::particles);
        bus.addListener(BlackFoxEnergyShader::register);
        MinecraftForge.EVENT_BUS.addListener(BlackFoxBossClient::logout);
        if (Boolean.getBoolean("contractblade.blackFox.clientValidation"))
            MinecraftForge.EVENT_BUS.addListener(BlackFoxClientValidation::tick);
    }
    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(BlackFoxBossCompat.type(), BlackFoxBossRenderer::new);
        event.registerEntityRenderer(BlackFoxBossCompat.coreType(), BlackFoxCoreRenderer::new);
        event.registerEntityRenderer(BlackFoxBossCompat.minorCutType(), BlackFoxMinorCutRenderer::new);
    }
    private static void particles(net.minecraftforge.client.event.RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(BlackFoxBossCompat.parryParticle(), BlackFoxParryParticle.Provider::new);
    }
    private static void reloads(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> BlackFoxBossResources.invalidate());
    }
    private static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        BlackFoxBossRenderer.clearWorld();
    }
    private BlackFoxBossClient() { }
}
