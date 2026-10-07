package com.maidweapon.forge.compat.fox;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.AnimatableEntity;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.PlayState;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.builder.AnimationBuilder;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.controller.AnimationController;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.event.predicate.AnimationEvent;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.molang.context.AnimationContext;
import com.maidweapon.forge.entity.BlackFoxBossEntity;
import com.maidweapon.forge.system.fox.challenge.BlackFoxFight;
import net.minecraft.resources.ResourceLocation;

/** Client-only pose of an ordinary combat entity; no IMaid, model capability or work controller. */
final class BlackFoxBossPose extends AnimatableEntity<BlackFoxBossEntity> {
    private String previous;
    private long previousStart = Long.MIN_VALUE;
    BlackFoxBossPose(BlackFoxBossEntity entity) {
        super(entity, 60);
        addAnimationController(new AnimationController<>(this, "combat", 0, event -> {
            String name = animation(entity);
            var controller = event.getController();
            if (!name.equals(previous) || previousStart != startedAt(entity)) {
                previous = name; previousStart = startedAt(entity); controller.markNeedsReload();
            }
            controller.setAnimation(name.equals("contract_fox_boss.walk")
                    ? new AnimationBuilder().loop(name) : new AnimationBuilder().playAndHold(name));
            return PlayState.CONTINUE;
        }) {
            @Override public double adjustTick(double tick) {
                double fallback = super.adjustTick(tick);
                String expected = animation(entity);
                if (currentAnimation == null || !currentAnimation.animationName.equals(expected)
                        || animationState != com.github.tartaricacid.touhoulittlemaid.geckolib3.core.AnimationState.RUNNING) return fallback;
                double elapsed = entity.level().getGameTime() - startedAt(entity)
                        + net.minecraft.client.Minecraft.getInstance().getPartialTick();
                if (expected.equals("contract_fox_boss.iaido")) {
                    elapsed = entity.skill()==BlackFoxFight.Skill.STANDING_IAIDO
                            ? com.maidweapon.forge.system.fox.challenge.BlackFoxStandingIaidoTimeline.animationTick((float)elapsed)
                            : com.maidweapon.forge.system.fox.challenge.BlackFoxIaidoTimeline.animationTick((float) elapsed);
                }
                return clipTick(elapsed, currentAnimation.animationLength,
                        expected.equals("contract_fox_boss.walk"));
            }
        });
        addAnimationController(new AnimationController<>(this, "tails", 0, event -> {
            event.getController().setAnimation(new AnimationBuilder().loop("contract_fox_boss.tails"));
            return PlayState.CONTINUE;
        }));
    }
    @Override public ResourceLocation getModelLocation() { return BlackFoxBossResources.MODEL; }
    @Override public ResourceLocation getTextureLocation() { return BlackFoxBossResources.TEXTURE; }
    @Override public ResourceLocation getAnimationFileLocation() { return BlackFoxBossResources.ANIMATION; }
    @Override protected boolean forceUpdate(AnimationEvent<?> event) {
        return !animation(entity).equals(previous) || previousStart != startedAt(entity);
    }
    private static String animation(BlackFoxBossEntity entity) {
        if (com.maidweapon.forge.system.fox.challenge.BlackFoxStandingIaidoTimeline.pose(entity.skill())) return "contract_fox_boss.iaido";
        return animation(entity.motion(), entity.onGround() && entity.getDeltaMovement().horizontalDistance() > .03,
                entity.comboStage());
    }
    private static long startedAt(BlackFoxBossEntity entity) {
        if (com.maidweapon.forge.system.fox.challenge.BlackFoxStandingIaidoTimeline.pose(entity.skill())) return entity.skillStartedAt();
        return entity.comboStage() > 0 ? entity.comboStartedAt() : entity.motionStartedAt();
    }
    static String animation(BlackFoxFight.Motion motion, boolean moving, int comboStage) {
        if (motion != BlackFoxFight.Motion.STAGGER && motion != BlackFoxFight.Motion.DEFEATED
                && comboStage >= 1 && comboStage <= 7) return "contract_fox_boss.combo_b" + comboStage;
        return animation(motion, moving);
    }
    static String animation(BlackFoxFight.Motion motion, boolean moving) {
        return "contract_fox_boss." + switch (motion) {
            case STAGGER -> "stagger"; case DEFEATED -> "defeated";
            default -> moving ? "walk" : "ready";
        };
    }
    static double clipTick(double elapsed, double length, boolean loop) {
        double time = Math.max(0, elapsed);
        return loop ? time % Math.max(.001, length) : Math.min(time, Math.max(0, length - .00001));
    }
    @Override public boolean setCustomAnimations(AnimationContext<?> context, AnimationEvent<?> event) {
        boolean updated = super.setCustomAnimations(context, event);
        var model = getCurrentModel();
        if (model != null) {
            var human = model.bones().get("AllBody");
            human.setScaleX(1); human.setScaleY(1); human.setScaleZ(1);
            var fox = model.bones().get("FOX");
            fox.setScaleX(0); fox.setScaleY(0); fox.setScaleZ(0);
            // Positional tracks must not leak into passive/Combo B poses after cancellation.
            if (!com.maidweapon.forge.system.fox.challenge.BlackFoxStandingIaidoTimeline.pose(entity.skill())) {
                var root = model.bones().get("Root");
                root.setPositionX(0); root.setPositionY(0); root.setPositionZ(0);
            }
        }
        return updated;
    }
}
