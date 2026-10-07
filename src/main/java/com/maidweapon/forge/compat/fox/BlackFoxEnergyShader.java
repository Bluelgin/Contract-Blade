package com.maidweapon.forge.compat.fox;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterShadersEvent;
import com.mojang.logging.LogUtils;
import java.io.IOException;

/** Local additive energy, not a world-light or screen-space bloom pipeline. Reload is owned by Forge. */
final class BlackFoxEnergyShader extends RenderType {
    private static ShaderInstance shader;
    private static final RenderType RIFT = type("rift");
    private static final RenderType SLASH = type("great_slash");
    private static final RenderType[] ARC = {arcType("core"),arcType("band"),arcType("halo"),arcType("trail")};
    private BlackFoxEnergyShader() {
        super("unused",DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,VertexFormat.Mode.QUADS,256,
                false,true,()->{},()->{});
    }
    static void register(RegisterShadersEvent event) {
        shader = null;
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.parse("maid_weapon:black_fox_energy"),DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP),
                    loaded -> shader = loaded);
        } catch (IOException failure) {
            LogUtils.getLogger().error("Black Fox energy shader unavailable; retaining emissive texture fallback",failure);
        }
    }
    static boolean ready() { return shader != null; }
    static RenderType type(boolean slash) { return slash ? SLASH : RIFT; }
    static RenderType arc(int layer) { return ARC[layer]; }
    private static RenderType arcType(String layer) {
        return create("black_fox_arc_"+layer,DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,true,
                CompositeState.builder().setShaderState(RENDERTYPE_EYES_SHADER)
                        .setTextureState(new TextureStateShard(ResourceLocation.parse(
                                "maid_weapon:textures/effect/black_fox_arc_"+layer+".png"),true,false))
                        .setTransparencyState(layer.equals("band") || layer.equals("trail")
                                ? TRANSLUCENT_TRANSPARENCY : LIGHTNING_TRANSPARENCY).setCullState(NO_CULL)
                        .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    }
    private static RenderType type(String part) {
        return create("black_fox_energy_"+part,DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
                VertexFormat.Mode.QUADS,256,false,true,CompositeState.builder()
                        .setShaderState(new ShaderStateShard(()->shader))
                        .setTextureState(new TextureStateShard(ResourceLocation.parse(
                                "maid_weapon:textures/effect/black_fox_"+part+"_energy.png"),true,false))
                        .setTransparencyState(LIGHTNING_TRANSPARENCY).setCullState(NO_CULL)
                        .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));
    }
}
