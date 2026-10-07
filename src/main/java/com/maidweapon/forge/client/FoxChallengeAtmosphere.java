package com.maidweapon.forge.client;

import com.maidweapon.forge.system.fox.challenge.FoxChallengeService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Cold teal void, with enough visibility for melee tells. Dedicated servers never load this class. */
@Mod.EventBusSubscriber(modid = "maid_weapon", value = Dist.CLIENT)
public final class FoxChallengeAtmosphere {
    @Mod.EventBusSubscriber(modid = "maid_weapon", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent
        public static void effects(RegisterDimensionSpecialEffectsEvent event) {
            event.register(FoxChallengeService.LEVEL.location(), new DimensionSpecialEffects.NetherEffects());
        }
    }

    private static boolean inside() {
        var level = Minecraft.getInstance().level;
        return level != null && level.dimension().equals(FoxChallengeService.LEVEL);
    }

    @SubscribeEvent
    public static void color(ViewportEvent.ComputeFogColor event) {
        if (!inside() || event.getCamera().getFluidInCamera() != net.minecraft.world.level.material.FogType.NONE) return;
        event.setRed(0.025f); event.setGreen(0.17f); event.setBlue(0.19f);
    }

    @SubscribeEvent
    public static void fog(ViewportEvent.RenderFog event) {
        if (!inside() || event.getType() != net.minecraft.world.level.material.FogType.NONE
                || event.getFarPlaneDistance() <= 20) return;
        event.setNearPlaneDistance(20);
        event.setFarPlaneDistance(Math.min(event.getFarPlaneDistance(), 88));
        event.setCanceled(true);
    }

    private FoxChallengeAtmosphere() { }
}
