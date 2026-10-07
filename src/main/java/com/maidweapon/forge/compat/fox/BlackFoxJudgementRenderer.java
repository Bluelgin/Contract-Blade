package com.maidweapon.forge.compat.fox;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.lang.reflect.Method;

/** Cached optional OBJ bridge. Reuses native judgement-cut geometry/materials without a proxy entity. */
final class BlackFoxJudgementRenderer {
    private static final ResourceLocation MODEL = ResourceLocation.parse("slashblade:model/util/slashdim.obj");
    private static final ResourceLocation TEXTURE = ResourceLocation.parse("slashblade:model/util/slashdim.png");
    private record Bridge(Object manager, Method model, Method color, Method luminous, Method wind) { }
    private static Bridge bridge;
    private static Bridge api() throws ReflectiveOperationException {
        if (bridge != null) return bridge;
        var manager = Class.forName("mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager");
        var obj = Class.forName("mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject");
        var state = Class.forName("mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState");
        Class<?>[] args = {ItemStack.class, obj, String.class, ResourceLocation.class, PoseStack.class, MultiBufferSource.class, int.class};
        return bridge = new Bridge(manager.getMethod("getInstance").invoke(null), manager.getMethod("getModel", ResourceLocation.class),
                state.getMethod("setCol", int.class), state.getMethod("renderOverridedReverseLuminous", args),
                state.getMethod("renderOverridedColorWrite", args));
    }
    static void draw(PoseStack stack, MultiBufferSource buffers, float age, boolean shell) {
        stack.pushPose();
        try {
            var api = api(); Object model = api.model().invoke(api.manager(), MODEL);
            stack.scale(.01f, .01f, .01f);
            stack.mulPose(Axis.YP.rotationDegrees(age * 4));
            api.color().invoke(null, shell ? 0x30632892 : 0xFFCE82FF);
            api.luminous().invoke(null, ItemStack.EMPTY, model, "base", TEXTURE, stack, buffers, 15728880);
            for (int i = 0; i < 3; i++) {
                stack.pushPose();
                stack.mulPose(Axis.XP.rotationDegrees(i * 60));
                stack.mulPose(Axis.ZP.rotationDegrees(age * 9 + i * 120));
                api.color().invoke(null, shell ? 0x709B46D1 : 0xFFD8A1FF);
                api.wind().invoke(null, ItemStack.EMPTY, model, "wind", TEXTURE, stack, buffers, 15728880);
                stack.popPose();
            }
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Native judgement cut render bridge", error); }
        finally { stack.popPose(); }
    }
    private BlackFoxJudgementRenderer() { }
}
