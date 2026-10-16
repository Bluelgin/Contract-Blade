package com.maidweapon.forge.compat.fox;

import com.maidweapon.forge.entity.BlackFoxSwordBoltEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/** Delegates only drawing to the native renderer. Proxies never enter the world or run native hit logic. */
final class BlackFoxSwordBoltRenderer extends EntityRenderer<BlackFoxSwordBoltEntity> {
    private Entity sword, drive;
    BlackFoxSwordBoltRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public ResourceLocation getTextureLocation(BlackFoxSwordBoltEntity entity) { return ResourceLocation.parse("slashblade:model/util/ss.png"); }
    private Entity visual(BlackFoxSwordBoltEntity shot) {
        Entity cached = shot.blade() ? drive : sword;
        if (cached != null && cached.level() == shot.level()) return cached;
        cached = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(shot.blade() ? "slashblade:drive" : "slashblade:abstract_summoned_sword")).create(shot.level());
        if (cached == null) throw new IllegalStateException("Sword core visual unavailable");
        try {
            cached.getClass().getMethod("setColor", int.class).invoke(cached, 0xB04CE5);
            if (shot.blade()) {
                cached.getClass().getMethod("setBaseSize", float.class).invoke(cached, 2f);
                cached.getClass().getMethod("setLifetime", float.class).invoke(cached, 60f);
                cached.getClass().getMethod("setRotationRoll", float.class).invoke(cached, 45f);
            }
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Sword core visual API", error); }
        if (shot.blade()) drive = cached; else sword = cached;
        return cached;
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override public void render(BlackFoxSwordBoltEntity shot, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
        var proxy = visual(shot); proxy.tickCount = shot.tickCount;
        proxy.setYRot(shot.getYRot()); proxy.yRotO = shot.yRotO; proxy.setXRot(shot.getXRot()); proxy.xRotO = shot.xRotO;
        EntityRenderer renderer = net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(proxy);
        renderer.render(proxy, yaw, partial, stack, buffers, light);
    }
}
