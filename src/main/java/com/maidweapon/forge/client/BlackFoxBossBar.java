package com.maidweapon.forge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.UUID;

/** UUID-scoped presentation only; vanilla still owns health interpolation and bar ordering. */
@Mod.EventBusSubscriber(modid = "maid_weapon", value = Dist.CLIENT)
public final class BlackFoxBossBar {
    public static final ResourceLocation TEXTURE = ResourceLocation.parse("maid_weapon:textures/gui/black_fox_bar.png");
    private static ClientLevel source;
    private static UUID barId;
    private static int marks, style;
    private static final int[] FISSURES = {24, 73, 129};
    public static void state(UUID id, int count, int appearance, boolean active) {
        var level = Minecraft.getInstance().level;
        if (!active) { if (id.equals(barId)) clear(); return; }
        if (level == null) return;
        source = level; barId = id; marks = Math.max(0, Math.min(5, count)); style = Math.max(0, Math.min(2, appearance));
    }
    public static boolean matches(UUID id) { return source != null && source == Minecraft.getInstance().level && id.equals(barId); }
    @SubscribeEvent public static void render(CustomizeGuiOverlayEvent.BossEventProgress event) {
        if (!matches(event.getBossEvent().getId())) return;
        event.setCanceled(true); event.setIncrement(30);
        draw(event.getGuiGraphics(), event.getX(), event.getY(), event.getBossEvent().getProgress(), marks, style);
        event.getGuiGraphics().drawCenteredString(Minecraft.getInstance().font, event.getBossEvent().getName(),
                event.getX() + 91, event.getY() - 9, 0xe4d5f1);
    }
    public static int filled(float progress) { return Float.isFinite(progress) ? Math.round(Math.max(0, Math.min(1, progress)) * 176) : 0; }
    public static void draw(GuiGraphics gui, int x, int y, float progress, int count, int appearance) {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        int row = Math.max(0, Math.min(2, appearance));
        gui.blit(TEXTURE, x - 16, y - 9, 0, row * 32, 214, 32, 214, 96);
        int width = filled(progress);
        int bright = row == 2 ? 0xffdbcbef : row == 1 ? 0xffdf95fa : 0xffc47ee9;
        gui.fill(x + 3, y + 3, x + 3 + width, y + 6, row == 2 ? 0xff8f7ca8 : 0xff8751aa);
        gui.fill(x + 3, y + 3, x + 3 + width, y + 4, bright);
        if (row == 1) {
            // Fixed pixel fissures; no flashing, additive shader, or per-frame texture allocation.
            for (int step : FISSURES) {
                gui.fill(x + step, y, x + step + 2, y + 2, 0xffb680d3);
                gui.fill(x + step + 2, y + 2, x + step + 4, y + 4, 0xffb680d3);
            }
        }
        for (int i = 0; i < 5; i++) {
            int left = x + 72 + i * 8;
            int color = i < count ? bright : 0xff493a56;
            for (int j = 0; j < 4; j++) {
                if (row == 2 && j == 2) continue;
                gui.fill(left + j, y + 15 - j, left + j + 2, y + 17 - j, color);
            }
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
    private static void clear() { source = null; barId = null; marks = style = 0; }
    private BlackFoxBossBar() { }
}
