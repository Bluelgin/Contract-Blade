package com.maidweapon.forge.client;

import com.maidweapon.forge.init.ModItems;
import com.maidweapon.forge.system.danmaku.DanmakuForm;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.Locale;

/** Approved SVG exports, composed with the actual animated native item and localized labels. */
public final class DanmakuWheelPainter {
    public static void render(GuiGraphics gui, Font font, int cx, int cy, int radius,
                              DanmakuForm hovered, DanmakuForm selected, int mastery, float partialTick) {
        int size = radius * 256 / 119;
        texture(gui, "wheel_base", cx, cy, size, 1);
        int icon = Math.max(12, radius * 32 / 119), offset = DanmakuWheelGeometry.iconOffset(radius);
        double clock = System.nanoTime() / 1e9;
        for (var form : DanmakuForm.values()) {
            int x = cx + (form == DanmakuForm.FLOWER ? offset : form == DanmakuForm.GUARD ? -offset : 0);
            int y = cy + (form == DanmakuForm.NEEDLE ? -offset : form == DanmakuForm.FANTASY ? offset : 0);
            boolean unlocked = form.unlocked(mastery);
            if (unlocked && (form == hovered || form == selected)) {
                gui.pose().pushPose(); gui.pose().translate(x, y, 0);
                gui.pose().mulPose(com.mojang.math.Axis.ZP.rotation((float)(clock * .22)));
                texture(gui, "selection", 0, 0, icon * 3 / 2,
                        (float)(.48 + .22 * Math.sin(clock * 2.4)));
                gui.pose().popPose();
            }
            texture(gui, "icon_" + form.name().toLowerCase(Locale.ROOT), x, y, icon, unlocked ? 1 : .28f);
            if (!unlocked) texture(gui, "lock", x + icon / 3, y + icon / 3, Math.max(9, icon / 2), 1);
            Component label = Component.translatable("maid_weapon.danmaku.short." + form.name().toLowerCase(Locale.ROOT))
                    .append(" ").append(Component.translatable("maid_weapon.gui.danmaku.level", form.requiredLevel()));
            gui.pose().pushPose(); gui.pose().translate(x, y + icon / 2 + 4, 0);
            float scale = Math.min(.8f, Math.max(.5f, radius / 115f)); gui.pose().scale(scale, scale, 1);
            gui.drawCenteredString(font, label, 0, 0, unlocked ? 0xFFEEDBC6 : 0xFF998898);
            gui.pose().popPose();
        }
        gui.pose().pushPose(); gui.pose().translate(cx, cy, 0);
        float scale = Math.min(1.5f, radius / 57f); gui.pose().scale(scale, scale, 1);
        gui.renderItem(new ItemStack(ModItems.MANKA_TALISMAN.get()), -8, -8);
        gui.pose().popPose();
    }
    private static void texture(GuiGraphics gui, String name, int x, int y, int size, float alpha) {
        var texture = DanmakuTextures.path(name);
        if (!DanmakuTextures.present(texture)) {
            if (name.equals("wheel_base")) gui.fill(x-size/2,y-size/2,x+size/2,y+size/2,0xDD261B32);
            return;
        }
        gui.setColor(alpha, alpha, alpha, alpha); RenderSystem.enableBlend();
        gui.blit(texture, x-size/2, y-size/2, size, size, 0, 0,
                name.equals("wheel_base") ? 256 : name.equals("lock") || name.startsWith("icon_") ? 32 : 64,
                name.equals("wheel_base") ? 256 : name.equals("lock") || name.startsWith("icon_") ? 32 : 64,
                name.equals("wheel_base") ? 256 : name.equals("lock") || name.startsWith("icon_") ? 32 : 64,
                name.equals("wheel_base") ? 256 : name.equals("lock") || name.startsWith("icon_") ? 32 : 64);
        gui.setColor(1,1,1,1);
    }
    private DanmakuWheelPainter() { }
}
