package com.maidweapon.forge.client;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Depth-tested translucent color-only writes; standard Minecraft shader, no shader pack. */
public final class DanmakuRenderTypes extends RenderType {
    private static final Map<ResourceLocation, RenderType> TYPES = new HashMap<>();
    private DanmakuRenderTypes(String n, VertexFormat f, VertexFormat.Mode m, int size,
                              boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
        super(n, f, m, size, crumbling, sort, setup, clear);
    }
    public static RenderType texture(ResourceLocation texture) {
        return TYPES.computeIfAbsent(texture, t -> create("manka_glyph", DefaultVertexFormat.POSITION_TEX_COLOR,
                VertexFormat.Mode.QUADS, 4096, false, true, CompositeState.builder()
                .setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
                .setTextureState(new TextureStateShard(t, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).createCompositeState(false)));
    }
}
