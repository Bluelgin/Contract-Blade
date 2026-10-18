package com.maidweapon.forge.client;

import com.maidweapon.forge.system.danmaku.DanmakuForm;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Non-pausing, keyboard/mouse configurable, confirm-on-release radial selection. */
public final class DanmakuWheelScreen extends Screen {
    private static final int RADIUS = 84;
    public DanmakuWheelScreen() { super(Component.translatable("maid_weapon.gui.danmaku.title")); }
    @Override public boolean isPauseScreen() { return false; }

    private DanmakuForm hover(double mouseX, double mouseY) {
        double dx = mouseX - width / 2.0;
        double dy = mouseY - height / 2.0;
        if (dx * dx + dy * dy < 18 * 18) return null;
        if (Math.abs(dx) > Math.abs(dy)) return dx > 0 ? DanmakuForm.FLOWER : DanmakuForm.GUARD;
        return dy > 0 ? DanmakuForm.FANTASY : DanmakuForm.NEEDLE;
    }
    @Override public void render(GuiGraphics gui, int mouseX, int mouseY, float delta) {
        gui.fill(0, 0, width, height, 0x55091020);
        int cx = width / 2, cy = height / 2;
        gui.fill(cx - RADIUS, cy - RADIUS, cx + RADIUS, cy + RADIUS, 0xDB202136);
        gui.fill(cx - 3, cy - RADIUS, cx + 3, cy + RADIUS, 0xCC0B1020);
        gui.fill(cx - RADIUS, cy - 3, cx + RADIUS, cy + 3, 0xCC0B1020);
        var chosen = hover(mouseX, mouseY);
        for (var form : DanmakuForm.values()) {
            int px = cx + (form == DanmakuForm.FLOWER ? 49 : form == DanmakuForm.GUARD ? -49 : 0);
            int py = cy + (form == DanmakuForm.FANTASY ? 49 : form == DanmakuForm.NEEDLE ? -49 : 0);
            boolean unlocked = form.unlocked(DanmakuClientState.mastery());
            int color = !unlocked ? 0xFF64677A
                    : form == chosen ? 0xFFFFD5F5
                    : form == DanmakuClientState.selected() ? 0xFFADDFFF : 0xFFE8DBF2;
            if (form == chosen && unlocked) gui.fill(px - 38, py - 14, px + 38, py + 14, 0xAA775CA5);
            gui.drawCenteredString(font, Component.translatable("maid_weapon.danmaku.form." + form.name().toLowerCase(java.util.Locale.ROOT)),
                    px, py - 4, color);
            if (!unlocked) gui.drawCenteredString(font, "Lv." + form.requiredLevel(), px, py + 7, 0xFFBCA3A3);
        }
        gui.fill(cx - 17, cy - 17, cx + 17, cy + 17, 0xFF2D254C);
        gui.drawCenteredString(font, "✿", cx, cy - 4, 0xFFF3A9CC);
        gui.drawCenteredString(font, title, cx, cy - RADIUS - 23, 0xFFF4DFFF);
        gui.drawCenteredString(font, Component.translatable("maid_weapon.gui.danmaku.release"),
                cx, cy + RADIUS + 13, 0xFFCBC8D9);
        super.render(gui, mouseX, mouseY, delta);
    }
    private void commit(double x, double y) {
        var selected = hover(x, y);
        if (selected != null) DanmakuClientState.select(selected);
        onClose();
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        var bound = DanmakuKeys.CAST.getKey();
        if (bound.getType() == InputConstants.Type.MOUSE && bound.getValue() == button) {
            commit(x, y);
            return true;
        }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean keyReleased(int key, int scancode, int modifiers) {
        var bound = DanmakuKeys.CAST.getKey();
        if (bound.getType() != InputConstants.Type.MOUSE && bound.equals(InputConstants.getKey(key, scancode))) {
            var handler = minecraft.mouseHandler;
            commit(handler.xpos() * width / minecraft.getWindow().getScreenWidth(),
                    handler.ypos() * height / minecraft.getWindow().getScreenHeight());
            return true;
        }
        return super.keyReleased(key, scancode, modifiers);
    }
}
