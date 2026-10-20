package com.maidweapon.forge.client;

import com.maidweapon.forge.init.ModItems;
import com.maidweapon.forge.system.danmaku.DanmakuForm;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.Locale;

/**
 * Zhu-ying radial spell wheel: entirely native GUI primitives, after an original SVG design.
 * This deliberately uses no Shader, external UI textures or world render state.
 */
public final class DanmakuWheelPainter {
    private static final int GOLD = 0xFFE0BD86;
    private static final int IVORY = 0xFFFFE5BF;
    private static final int DIM = 0xFF998898;

    private DanmakuWheelPainter() { }

    public static void render(GuiGraphics gui, Font font, int cx, int cy, int radius,
                              DanmakuForm hovered, DanmakuForm selected, int mastery,
                              float partialTick) {
        // Each horizontal run is emitted as one GUI quad, rather than one draw call per pixel.
        // Fixed-step sector colors also remain legible at low GUI scales.
        int bound = radius + 4;
        double pulse = 0.5 + 0.5 * Math.sin((System.currentTimeMillis() % 2600L) * Math.PI / 1300.0);
        for (int dy = -bound; dy <= bound; dy++) {
            int lastColor = 0;
            int runStart = -bound;
            for (int dx = -bound; dx <= bound + 1; dx++) {
                int color = dx <= bound
                        ? colorAt(dx, dy, radius, hovered, selected, mastery, pulse) : 0;
                if (dx == -bound) { lastColor = color; runStart = dx; continue; }
                if (color != lastColor) {
                    if (lastColor != 0) {
                        gui.fill(cx + runStart, cy + dy, cx + dx, cy + dy + 1, lastColor);
                    }
                    runStart = dx;
                    lastColor = color;
                }
            }
        }

        // Gilded nodes and slow twinkle on the frame, matching the hand-drawn talisman.
        for (int i = 0; i < 16; i++) {
            double angle = Math.toRadians(i * 22.5 - 90);
            int x = cx + (int) Math.round(Math.cos(angle) * (radius + 2));
            int y = cy + (int) Math.round(Math.sin(angle) * (radius + 2));
            int size = i % 4 == 0 ? 3 : 2;
            gui.fill(x - size / 2, y - size / 2, x + size / 2 + 1, y + size / 2 + 1,
                    i % 4 == 0 ? GOLD : 0xBBD1A272);
        }

        int icon = Math.max(28, radius * 64 / 86);
        int offset = (int) (radius * .67);
        symbol(gui, cx, cy - offset, DanmakuForm.NEEDLE, DanmakuForm.NEEDLE.unlocked(mastery), hovered == DanmakuForm.NEEDLE, icon);
        symbol(gui, cx + offset, cy, DanmakuForm.FLOWER, DanmakuForm.FLOWER.unlocked(mastery), hovered == DanmakuForm.FLOWER, icon);
        symbol(gui, cx - offset, cy, DanmakuForm.GUARD, DanmakuForm.GUARD.unlocked(mastery), hovered == DanmakuForm.GUARD, icon);
        symbol(gui, cx, cy + offset, DanmakuForm.FANTASY, DanmakuForm.FANTASY.unlocked(mastery), hovered == DanmakuForm.FANTASY, icon);

        label(gui, font, cx, cy - offset + 20, DanmakuForm.NEEDLE, mastery, hovered, selected);
        label(gui, font, cx + offset, cy + 21, DanmakuForm.FLOWER, mastery, hovered, selected);
        label(gui, font, cx - offset, cy + 21, DanmakuForm.GUARD, mastery, hovered, selected);
        label(gui, font, cx, cy + offset + 22, DanmakuForm.FANTASY, mastery, hovered, selected);

        // Central crest comes from the real game item model, so it animates with the item.
        gui.renderItem(new ItemStack(ModItems.MANKA_TALISMAN.get()), cx - 8, cy - 8);
    }

    private static int colorAt(int x, int y, int r, DanmakuForm hovered, DanmakuForm selected,
                               int mastery, double pulse) {
        int sq = x * x + y * y;
        double distance = Math.sqrt(sq);
        if (distance > r + 4 || distance < 0) return 0;
        if (distance >= r + 1) return 0xAA523F45;
        if (distance >= r - 1) return GOLD;
        if (distance >= r - 4) return 0xFF6F3D4B;
        if (distance <= r * 0.35) {
            if (distance >= r * 0.32) return GOLD;
            return distance <= r * .27 ? 0xF5221C2B : 0xF53B283A;
        }
        // Four sectors exactly match the input angle test (axis-dominant wedges).
        DanmakuForm form = Math.abs(x) > Math.abs(y)
                ? (x > 0 ? DanmakuForm.FLOWER : DanmakuForm.GUARD)
                : (y > 0 ? DanmakuForm.FANTASY : DanmakuForm.NEEDLE);
        if (Math.abs(Math.abs(x) - Math.abs(y)) <= 1.1) return 0xBBDAB384;
        if (!form.unlocked(mastery)) return 0xE8262533;
        if (form == hovered) return pulse > .5 ? 0xED994A61 : 0xE582405A;
        if (form == selected) return 0xE85C475D;
        return 0xE73A3047;
    }

    private static void label(GuiGraphics gui, Font font, int x, int y, DanmakuForm form,
                              int mastery, DanmakuForm hovered, DanmakuForm selected) {
        boolean unlocked = form.unlocked(mastery);
        int color = !unlocked ? DIM : form == hovered ? 0xFFFFE5BA
                : form == selected ? 0xFFBDE9F8 : 0xFFEEDBC6;
        Component name = Component.translatable("maid_weapon.danmaku.form." +
                form.name().toLowerCase(Locale.ROOT));
        gui.drawCenteredString(font, name, x, y, color);
        if (!unlocked) gui.drawCenteredString(font, "Lv." + form.requiredLevel(), x, y + 10, 0xFFB8A3B0);
    }

    private static void symbol(GuiGraphics gui, int cx, int cy, DanmakuForm form,
                               boolean unlocked, boolean highlighted, int scale) {
        int gold = unlocked ? (highlighted ? 0xFFFFEDD4 : IVORY) : 0xFF726677;
        int rose = unlocked ? 0xFFC65B76 : 0xFF585265;
        int y = cy - 16;
        switch (form) {
            case NEEDLE -> {
                for (int x = -11; x <= 11; x += 11) {
                    line(gui, cx + x, y, cx + x / 2, y + 19, gold);
                    line(gui, cx + x - 3, y + 6, cx + x, y - 2, gold);
                    line(gui, cx + x + 3, y + 6, cx + x, y - 2, gold);
                }
            }
            case FLOWER -> {
                // Five original stylized petals around a small golden pistil.
                for (int i = 0; i < 5; i++) {
                    double angle = Math.toRadians(i * 72 - 90);
                    int px = cx + (int) Math.round(Math.cos(angle) * 9);
                    int py = y + 11 + (int) Math.round(Math.sin(angle) * 9);
                    gui.fill(px - 4, py - 5, px + 5, py + 6, rose);
                    gui.fill(px - 2, py - 3, px + 3, py + 3, gold);
                }
                gui.fill(cx - 3, y + 8, cx + 4, y + 15, GOLD);
            }
            case GUARD -> {
                line(gui, cx, y - 1, cx + 15, y + 7, gold);
                line(gui, cx + 15, y + 7, cx + 10, y + 22, gold);
                line(gui, cx + 10, y + 22, cx, y + 29, gold);
                line(gui, cx, y + 29, cx - 10, y + 22, gold);
                line(gui, cx - 10, y + 22, cx - 15, y + 7, gold);
                line(gui, cx - 15, y + 7, cx, y - 1, gold);
                line(gui, cx - 6, y + 14, cx - 1, y + 19, rose);
                line(gui, cx - 1, y + 19, cx + 9, y + 8, gold);
            }
            case FANTASY -> {
                // Four-pointed star in a cardinal ring, grayed while Lv.10 is locked.
                for (int i = 0; i < 12; i++) {
                    double a = 2 * Math.PI * i / 12;
                    int x = cx + (int) Math.round(Math.cos(a) * 15);
                    int yy = y + 13 + (int) Math.round(Math.sin(a) * 15);
                    gui.fill(x, yy, x + 2, yy + 2, gold);
                }
                line(gui, cx, y - 5, cx + 5, y + 11, gold);
                line(gui, cx + 5, y + 11, cx + 15, y + 13, gold);
                line(gui, cx + 15, y + 13, cx + 4, y + 16, gold);
                line(gui, cx + 4, y + 16, cx, y + 31, gold);
                line(gui, cx, y + 31, cx - 4, y + 16, gold);
                line(gui, cx - 4, y + 16, cx - 15, y + 13, gold);
                line(gui, cx - 15, y + 13, cx - 5, y + 11, gold);
                line(gui, cx - 5, y + 11, cx, y - 5, gold);
            }
        }
    }

    private static void line(GuiGraphics gui, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        for (;;) {
            gui.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = error * 2;
            if (e2 >= dy) { error += dy; x0 += sx; }
            if (e2 <= dx) { error += dx; y0 += sy; }
        }
    }
}
