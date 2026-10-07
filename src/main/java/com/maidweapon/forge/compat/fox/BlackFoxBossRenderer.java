package com.maidweapon.forge.compat.fox;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.GeoReplacedEntityRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.RenderUtils;
import com.maidweapon.forge.compat.BlackFoxBladeRenderer;
import com.maidweapon.forge.entity.BlackFoxBossEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.lang.ref.WeakReference;

/** Generic animated geometry renderer. Deliberately has no maid renderer, layers or extensions. */
public class BlackFoxBossRenderer extends GeoReplacedEntityRenderer<BlackFoxBossEntity, BlackFoxBossPose> {
    private static WeakReference<BlackFoxBossRenderer> active = new WeakReference<>(null);
    private final Map<BlackFoxBossEntity, BlackFoxBossPose> poses = new IdentityHashMap<>();
    private int generation = -1;
    public BlackFoxBossRenderer(EntityRendererProvider.Context context) {
        super(context);
        widthScale = heightScale = .65f;
        active = new WeakReference<>(this);
    }
    static void clearWorld() {
        var renderer = active.get();
        if (renderer != null) renderer.poses.clear();
    }
    @Override public void render(BlackFoxBossEntity entity, float yaw, float partial, PoseStack stack,
                                 MultiBufferSource buffers, int light) {
        BlackFoxDimensionEffects.draw(entity, partial, stack, buffers);
        BlackFoxStandingIaidoVisual.draw(entity,partial,stack,buffers);
        BlackFoxSlashEffects.draw(entity,partial,stack,buffers);
        BlackFoxSwordRifts.draw(entity, partial, stack, buffers);
        BlackFoxCrossRifts.draw(entity, partial, stack, buffers);
        super.render(entity, yaw, partial, stack, buffers, light);
    }
    @Override public BlackFoxBossPose getAnimatableEntity(BlackFoxBossEntity entity) {
        BlackFoxBossResources.ensureLoaded();
        if (generation != BlackFoxBossResources.generation()) {
            poses.clear();
            generation = BlackFoxBossResources.generation();
        }
        // Different players' nearby encounters need independent animation clocks.
        poses.keySet().removeIf(old -> old.isRemoved() || old.level() != entity.level());
        return poses.computeIfAbsent(entity, BlackFoxBossPose::new);
    }
    @Override public void render(AnimatedGeoModel model, BlackFoxBossEntity entity, float partialTick,
            RenderType type, PoseStack stack, MultiBufferSource buffers, VertexConsumer vertices, int light, int overlay,
            float red, float green, float blue, float alpha) {
        // Native OBJ switches shared buffers (quads -> triangles/glint). Finish all body vertices first.
        float[] tint = spiritTint(entity != null && entity.phaseTwo(), entity != null
                && entity.motion() == com.maidweapon.forge.system.fox.challenge.BlackFoxFight.Motion.DEFEATED,
                entity == null ? 0 : entity.level().getGameTime() - entity.motionStartedAt() + partialTick);
        RenderType spirit = bodyType();
        boolean dimension = entity != null && entity.skill() == com.maidweapon.forge.system.fox.challenge.BlackFoxFight.Skill.DIMENSION_STRIKE;
        float bodyAlpha = dimension ? com.maidweapon.forge.system.fox.challenge.BlackFoxDimensionTimeline.bodyAlpha(entity.skillAge(partialTick)) : 1;
        stack.pushPose();
        if (dimension && entity.skillAge(partialTick) < com.maidweapon.forge.system.fox.challenge.BlackFoxDimensionTimeline.DIVE_END)
            stack.translate(0, -1.5 * (1-bodyAlpha), 0);
        super.render(model, entity, partialTick, spirit, stack, buffers, buffers.getBuffer(spirit), light, overlay,
                red * tint[0], green * tint[1], blue * tint[2], alpha * tint[3] * bodyAlpha);
        if (bodyAlpha > .5f && entity != null && entity.motion() != com.maidweapon.forge.system.fox.challenge.BlackFoxFight.Motion.DEFEATED)
            BlackFoxSpiritGlints.draw(model, stack, buffers, entity.tickCount + partialTick, entity.getId(),
                    entity.phaseTwo(), entity.comboStage(),
                    entity.level().getGameTime() - entity.comboStartedAt() + partialTick);
        if (bodyAlpha > .5f && entity != null) BlackFoxIaidoCharge.draw(model, entity, partialTick, stack, buffers);
        if (bodyAlpha > .5f) renderEquipment(model, entity, stack, buffers, light, partialTick);
        stack.popPose();
    }
    static RenderType bodyType() { return RenderType.entityTranslucent(BlackFoxBossResources.TEXTURE); }

    /** One translucent body pass, no duplicate shells. Only the encounter renderer gets this tint. */
    static float[] spiritTint(boolean phaseTwo, boolean defeated, float age) {
        float restored = defeated ? Math.min(1, Math.max(0, age / 60f)) : 0;
        float r = phaseTwo ? .32f : .28f, g = phaseTwo ? .10f : .16f, b = phaseTwo ? .48f : .40f;
        float opacity = phaseTwo ? .64f : .80f;
        return new float[]{r + (1-r) * restored, g + (1-g) * restored, b + (1-b) * restored,
                (opacity + (1-opacity) * restored) * (defeated ? Math.min(1, Math.max(0, (100-age) / 20f)) : 1)};
    }
    void renderEquipment(AnimatedGeoModel model, BlackFoxBossEntity entity, PoseStack stack,
                         MultiBufferSource buffers, int light, float partial) {
        if (com.maidweapon.forge.system.fox.challenge.BlackFoxStandingIaidoTimeline.pose(entity.skill())) {
            float age=entity.skillAge(partial);
            if(entity.skill()==com.maidweapon.forge.system.fox.challenge.BlackFoxFight.Skill.STANDING_IAIDO)
                age=com.maidweapon.forge.system.fox.challenge.BlackFoxStandingIaidoTimeline.poseAge(age);
            boolean drawn = com.maidweapon.forge.system.fox.challenge.BlackFoxIaidoTimeline.drawn(age);
            var mouth = hierarchy(model.bones(), "LeftHandLocator");
            renderPart(entity.getMainHandItem(), stack, buffers, light, mouth, true, false);
            renderPart(entity.getMainHandItem(), stack, buffers, light,
                    drawn ? hierarchy(model.bones(), "BladeLocator") : mouth, false, drawn);
            return;
        }
        renderEquipment(model, entity.getMainHandItem(), entity.comboStage(), stack, buffers, light);
    }
    void renderEquipment(AnimatedGeoModel model, ItemStack blade, int comboStage, PoseStack stack,
                         MultiBufferSource buffers, int light) {
        var waist = hierarchy(model.bones(), "BossSheathLocator");
        boolean drawn = comboStage > 0;
        renderPart(blade, stack, buffers, light, waist, true, false);
        renderPart(blade, stack, buffers, light,
                hierarchy(model.bones(), bladeLocator(comboStage)), false, drawn);
    }
    static String bladeLocator(int comboStage) { return comboStage > 0 ? "BladeLocator" : "BossSheathLocator"; }
    static List<AnimatedGeoBone> hierarchy(Map<String, AnimatedGeoBone> bones, String name) {
        List<AnimatedGeoBone> path = new ArrayList<>();
        for (var bone = bones.get(name); bone != null;
             bone = bone.geoBone().parent() == null ? null : bones.get(bone.geoBone().parent().name())) path.add(bone);
        Collections.reverse(path);
        return path;
    }
    private static void renderPart(ItemStack blade, PoseStack stack, MultiBufferSource buffers,
                                   int light, List<AnimatedGeoBone> locator, boolean sheath, boolean drawn) {
        if (locator.isEmpty()) return;
        stack.pushPose();
        try {
            if (RenderUtils.prepMatrixForLocator(stack, locator)) return;
            // Native OBJ points along -X. Held blade follows the preview's -Y grip axis;
            // the sheath (and idle blade) stays horizontal at the hip, never follows an arm.
            stack.mulPose(drawn ? Axis.ZP.rotationDegrees(90) : Axis.YP.rotationDegrees(90));
            stack.scale(.00625f, .00625f, .00625f);
            BlackFoxBladeRenderer.render(blade, sheath, stack, buffers, light);
        } finally { stack.popPose(); }
    }
}
