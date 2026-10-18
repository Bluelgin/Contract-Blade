package com.maidweapon.forge.client;

import com.maidweapon.common.MaidWeaponConstants;
import com.maidweapon.forge.network.DanmakuNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Tap to cast, hold six client ticks to open a selection wheel; no spell fires on release from wheel. */
@Mod.EventBusSubscriber(modid = MaidWeaponConstants.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DanmakuKeys {
    public static final KeyMapping CAST = new KeyMapping("key.maid_weapon.danmaku",
            InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_MIDDLE, "key.categories.maid_weapon");
    @SubscribeEvent public static void register(RegisterKeyMappingsEvent event) { event.register(CAST); }

    @Mod.EventBusSubscriber(modid = MaidWeaponConstants.MOD_ID, value = Dist.CLIENT)
    public static final class Input {
        private static boolean pressed;
        private static int ticks;

        @SubscribeEvent public static void handleTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft client = Minecraft.getInstance();
            while (CAST.consumeClick()) { } // Clear stale presses from previous screens.
            if (client.screen instanceof DanmakuWheelScreen) { pressed = false; ticks = 0; return; }
            if (client.screen != null || !DanmakuClientState.available()) {
                pressed = false; ticks = 0; return;
            }
            boolean down = CAST.isDown();
            if (down && !pressed) { pressed = true; ticks = 0; }
            else if (down && ++ticks >= 6) {
                pressed = false; ticks = 0;
                client.setScreen(new DanmakuWheelScreen());
            } else if (!down && pressed) {
                pressed = false; ticks = 0;
                DanmakuNetwork.cast(DanmakuClientState.selected());
            }
        }

        /**
         * Air attacks do not generate a vanilla server-side AttackEntityEvent.
         * The packet contains no target or damage and is always authenticated by the server.
         */
        @SubscribeEvent public static void emptyAirAttack(PlayerInteractEvent.LeftClickEmpty event) {
            Minecraft client = Minecraft.getInstance();
            if (client.screen == null && event.getEntity() == client.player
                    && DanmakuClientState.available()) DanmakuNetwork.airSwing();
        }

        /** Pick-block stays vanilla unless this exact rebindable talisman key is active. */
        @SubscribeEvent public static void blockVanillaPick(InputEvent.InteractionKeyMappingTriggered event) {
            if (!DanmakuClientState.available()) return;
            Minecraft client = Minecraft.getInstance();
            if (client.screen != null) return;
            if (CAST.getKey().equals(event.getKeyMapping().getKey())) {
                event.setSwingHand(false);
                event.setCanceled(true);
            }
        }
    }
    private DanmakuKeys() { }
}
