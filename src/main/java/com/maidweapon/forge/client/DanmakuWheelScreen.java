package com.maidweapon.forge.client;

import com.maidweapon.forge.system.danmaku.DanmakuForm;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.Objects;

/** Non-pausing radial picker: only a short tap casts, holding and releasing changes form. */
public final class DanmakuWheelScreen extends Screen {
    private final String openedBinding = DanmakuClientState.binding();
    private int radius = DanmakuWheelGeometry.RADIUS;

    public DanmakuWheelScreen() {
        super(Component.translatable("maid_weapon.gui.danmaku.title"));
    }

    @Override public boolean isPauseScreen() { return false; }

    private DanmakuForm hover(double x, double y) {
        return DanmakuWheelGeometry.pick(x - width / 2.0, y - height / 2.0, radius);
    }

    @Override public void render(GuiGraphics gui, int mouseX, int mouseY, float delta) {
        gui.fill(0, 0, width, height, 0x55091020);
        radius = DanmakuWheelGeometry.fitRadius(width, height);
        int cx = width / 2, cy = height / 2;
        gui.drawCenteredString(font, title, cx, cy - radius - 22, 0xFFFFE4C5);
        DanmakuWheelPainter.render(gui, font, cx, cy, radius, hover(mouseX, mouseY),
                DanmakuClientState.selected(), DanmakuClientState.mastery(), delta);
        var focus = hover(mouseX, mouseY);
        if (focus != null) gui.drawCenteredString(font, Component.translatable("maid_weapon.danmaku.form." +
                focus.name().toLowerCase(java.util.Locale.ROOT)), cx, cy + radius + 2, 0xFFFFE4C5);
        gui.drawCenteredString(font, Component.translatable("maid_weapon.gui.danmaku.release"),
                cx, cy + radius + 13, 0xFFE1D1C4);
        super.render(gui, mouseX, mouseY, delta);
    }

    @Override public void tick() {
        if (!DanmakuClientState.available() || !openedBinding.equals(DanmakuClientState.binding())) onClose();
    }
    @Override public void onClose() {
        DanmakuKeys.CAST.setDown(false);
        while (DanmakuKeys.CAST.consumeClick()) { }
        super.onClose();
    }
    private void commit(double x, double y) {
        DanmakuForm form = hover(x, y);
        if (form != null && form.unlocked(DanmakuClientState.mastery())) {
            DanmakuClientState.select(form);
        }
        onClose();
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        var key = DanmakuKeys.CAST.getKey();
        if (key.getType() == InputConstants.Type.MOUSE && key.getValue() == button) {
            commit(x, y);
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override public boolean keyReleased(int key, int scancode, int modifiers) {
        var bound = DanmakuKeys.CAST.getKey();
        if (bound.getType() != InputConstants.Type.MOUSE
                && bound.equals(InputConstants.getKey(key, scancode))) {
            var client = Objects.requireNonNull(minecraft);
            var mouse = client.mouseHandler;
            commit(mouse.xpos() * width / client.getWindow().getScreenWidth(),
                    mouse.ypos() * height / client.getWindow().getScreenHeight());
            return true;
        }
        return super.keyReleased(key, scancode, modifiers);
    }
}
