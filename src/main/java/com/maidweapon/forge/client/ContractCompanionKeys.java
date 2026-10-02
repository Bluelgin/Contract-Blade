package com.maidweapon.forge.client;

import com.maidweapon.common.MaidWeaponConstants;
import com.maidweapon.forge.network.ContractCompanionNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = MaidWeaponConstants.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ContractCompanionKeys {
    public static final KeyMapping CALL = new KeyMapping("key.maid_weapon.call_companion", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_BRACKET, "key.categories.maid_weapon");
    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) { event.register(CALL); }

    @Mod.EventBusSubscriber(modid = MaidWeaponConstants.MOD_ID, value = Dist.CLIENT)
    public static final class Input {
        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            var client = Minecraft.getInstance();
            while (CALL.consumeClick()) {
                if (client.player != null && client.screen == null && !client.player.isSpectator())
                    ContractCompanionNetwork.CHANNEL.sendToServer(new ContractCompanionNetwork.Call());
            }
        }
    }
    private ContractCompanionKeys() { }
}
