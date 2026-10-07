package com.maidweapon.forge.compat.fox;

import com.maidweapon.forge.client.BlackFoxBossBar;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import org.joml.Matrix4f;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Opt-in actual GUI/FBO test, including unrelated-boss fallback. */
final class BlackFoxBossBarValidation {
    static void run() throws Exception {
        var mc = Minecraft.getInstance();
        try (var image = NativeImage.read(mc.getResourceManager().open(BlackFoxBossBar.TEXTURE))) {
            require(image.getWidth() == 214 && image.getHeight() == 96, "atlas size");
            require((image.getPixelRGBA(213, 31) >>> 24) == 0, "transparent margin");
        }
        require(BlackFoxBossBar.filled(1) == 176 && BlackFoxBossBar.filled(.5f) == 88
                && BlackFoxBossBar.filled(0) == 0 && BlackFoxBossBar.filled(Float.NaN) == 0
                && BlackFoxBossBar.filled(-1) == 0 && BlackFoxBossBar.filled(2) == 176, "health clipping");
        var unknown = new LerpingBossEvent(UUID.randomUUID(), Component.literal("Other Boss"), 1,
                BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS, false, false, false);
        var buffers = MultiBufferSource.immediate(new BufferBuilder(4096));
        var gui = new GuiGraphics(mc, buffers);
        var event = new CustomizeGuiOverlayEvent.BossEventProgress(mc.getWindow(), gui, 0, unknown, 25, 18, 19);
        BlackFoxBossBar.render(event);
        require(!event.isCanceled() && event.getIncrement() == 19, "unrelated purple boss changed");
        var target = new TextureTarget(750, 300, true, Minecraft.ON_OSX);
        var projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting = RenderSystem.getVertexSorting();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        try {
            modelView.setIdentity(); RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, 750, 300, 0, -1000, 1000), VertexSorting.ORTHOGRAPHIC_Z);
            target.setClearColor(.045f, .035f, .06f, 1); target.clear(Minecraft.ON_OSX); target.bindWrite(true);
            gui.pose().scale(3, 3, 1);
            for (int row = 0; row < 3; row++) {
                int y = 18 + row * 30;
                BlackFoxBossBar.draw(gui, 25, y, row == 0 ? .85f : .5f, row == 2 ? 5 : 2, row);
                gui.drawCenteredString(mc.font, Component.translatable("entity.maid_weapon.black_fox_boss"), 116, y - 9, 0xe4d5f1);
            }
            gui.flush();
            RenderSystem.bindTexture(target.getColorTextureId());
            try (var image = new NativeImage(750, 300, false)) {
                image.downloadTexture(0, false); image.flipY();
                for (int row = 0; row < 3; row++) {
                    int color = image.getPixelRGBA(100, (21 + row * 30) * 3);
                    require((color & 255) > 100 && ((color >>> 16) & 255) > 100, "fill not drawn for style " + row);
                }
                var output = Path.of("..", "art", "black_fox", "boss_presentation", "runtime-bar-preview.png").toAbsolutePath().normalize();
                Files.createDirectories(output.getParent()); image.writeToFile(output);
            }
        } finally {
            modelView.popPose(); RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting);
            mc.getMainRenderTarget().bindWrite(true); target.destroyBuffers();
        }
        LogUtils.getLogger().info("BLACK_FOX_BAR_PASS: actual GUI draw of three styles, health clipping, transparent atlas and unrelated boss fallback");
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private BlackFoxBossBarValidation() { }
}
