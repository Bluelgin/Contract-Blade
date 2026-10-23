package com.maidweapon.forge.client;

import com.maidweapon.forge.system.danmaku.DanmakuForm;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.Locale;

/** Lightweight client-only declaration HUD. No resource is required before Astra assets arrive. */
@Mod.EventBusSubscriber(modid = "maid_weapon", value = Dist.CLIENT)
public final class DanmakuVisualController {
    private static Object world;
    private static DanmakuForm current;
    private static int remaining;
    private static final int DURATION_TICKS = 42;

    public static void show(DanmakuForm form) {
        world = Minecraft.getInstance().level;
        current = form;
        remaining = DURATION_TICKS;
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || remaining <= 0) return;
        var mc = Minecraft.getInstance();
        if (world != mc.level || mc.player == null || !mc.player.isAlive()) {
            world = mc.level; remaining = 0; return;
        }
        remaining--;
    }

    @SubscribeEvent public static void render(RenderGuiEvent.Post event) {
        if (remaining <= 0 || current == null) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        var gui = event.getGuiGraphics();
        Component name = Component.translatable("maid_weapon.danmaku.form."
                + current.name().toLowerCase(Locale.ROOT));
        int width = Math.max(136, mc.font.width(name) + 28);
        int left = Math.max(2, mc.getWindow().getGuiScaledWidth() - width - 12);
        int top = Math.max(26, mc.getWindow().getGuiScaledHeight() / 6);
        int alpha = Math.min(230, remaining * 12);
        int bg = (alpha << 24) | 0x251B30;
        gui.fill(left, top, left + width, top + 31, bg);
        gui.fill(left, top, left + 3, top + 31, 0xFFE0BD86);
        gui.fill(left + 8, top + 5, left + width - 7, top + 6, 0xAADEB985);
        gui.drawString(mc.font, Component.translatable("maid_weapon.gui.danmaku.title"),
                left + 11, top + 9, 0xFFE0BD86, false);
        gui.drawString(mc.font, name, left + 11, top + 20, 0xFFFFE5CF, false);
    }

    private DanmakuVisualController() { }
}
