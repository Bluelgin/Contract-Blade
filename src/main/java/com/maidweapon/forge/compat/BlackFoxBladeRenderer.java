package com.maidweapon.forge.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.lang.reflect.Method;
import java.util.Optional;

/** Client-only optional native OBJ bridge; renders blade and sheath separately at the fox's bones. */
public final class BlackFoxBladeRenderer {
    private record Bridge(Object manager, Method model, Method solid, Method luminous,
                          Method texture, Method modelId, Method broken) { }
    private static Bridge bridge;
    private static boolean failed;
    private static Bridge bridge() throws ReflectiveOperationException {
        if (bridge != null) return bridge;
        Class<?> manager = Class.forName("mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager");
        Class<?> obj = Class.forName("mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject");
        Class<?> render = Class.forName("mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState");
        Class<?> state = Class.forName("mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState");
        Class<?>[] args = {ItemStack.class, obj, String.class, ResourceLocation.class,
                PoseStack.class, MultiBufferSource.class, int.class};
        return bridge = new Bridge(manager.getMethod("getInstance").invoke(null), manager.getMethod("getModel", ResourceLocation.class),
                render.getMethod("renderOverrided", args), render.getMethod("renderOverridedLuminous", args),
                state.getMethod("getTexture"), state.getMethod("getModel"), state.getMethod("isBroken"));
    }
    public static void render(ItemStack blade, boolean sheath, PoseStack stack, MultiBufferSource buffers, int light) {
        if (failed || blade.isEmpty()) return;
        try {
            Object state = SlashBladeCompat.resolveBladeState(blade);
            if (state == null) return;
            var api = bridge();
            var texture = location(api.texture().invoke(state), "slashblade:model/blade.png");
            Object model = api.model().invoke(api.manager(), location(api.modelId().invoke(state), "slashblade:model/blade.obj"));
            String part = sheath ? "sheath" : Boolean.TRUE.equals(api.broken().invoke(state)) ? "blade_damaged" : "blade";
            api.solid().invoke(null, blade, model, part, texture, stack, buffers, light);
            api.luminous().invoke(null, blade, model, part + "_luminous", texture, stack, buffers, light);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            failed = true;
            CompatDiagnostics.warnOnce("BlackFox.nativeBladeRenderer", failure);
        }
    }
    private static ResourceLocation location(Object value, String fallback) {
        Object result = ((Optional<?>) value).orElse(null);
        return result instanceof ResourceLocation id ? id : ResourceLocation.parse(fallback);
    }
    public static void validate() throws ReflectiveOperationException { bridge(); }
    private BlackFoxBladeRenderer() { }
}
