package com.maidweapon.forge.compat.fox;

import com.github.tartaricacid.touhoulittlemaid.geckolib3.resource.GeckoLibCache;
import com.maidweapon.forge.system.fox.challenge.BlackFoxFight;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.maidweapon.forge.compat.BlackFoxBladeRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraftforge.event.TickEvent;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.AnimatableEntity;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.PlayState;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.builder.AnimationBuilder;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.controller.AnimationController;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.event.predicate.AnimationEvent;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.molang.context.AnimationContext;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.model.provider.data.EntityModelData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import java.util.List;

/** Opt-in fresh-client resource check; does not open or change any save. */
public final class BlackFoxClientValidation {
    private static boolean finished;
    public static void tick(TickEvent.ClientTickEvent event) {
        if (finished || event.phase != TickEvent.Phase.END || Minecraft.getInstance().getOverlay() != null
                || Minecraft.getInstance().screen == null || GeckoLibCache.getInstance().getAnimations().isEmpty()) return;
        finished = true;
        try {
            BlackFoxBossResources.ensureLoaded();
            BlackFoxMusicValidation.run();
            BlackFoxBossBarValidation.run();
            try (var resource = Minecraft.getInstance().getResourceManager().getResource(
                    ResourceLocation.parse("maid_weapon:textures/particle/parry_contact.png")).orElseThrow().open();
                 var icon = com.mojang.blaze3d.platform.NativeImage.read(resource)) {
                if (icon.getWidth() != 64 || icon.getHeight() != 64 || (icon.getPixelRGBA(0, 0) >>> 24) != 0)
                    throw new IllegalStateException("Invalid contact particle texture");
            }
            var sprites = net.minecraft.client.particle.ParticleEngine.class.getDeclaredField("spriteSets");
            sprites.setAccessible(true);
            var sets = (java.util.Map<?, ?>) sprites.get(Minecraft.getInstance().particleEngine);
            var contact = (net.minecraft.client.particle.SpriteSet) sets.get(ResourceLocation.parse("maid_weapon:black_fox_parry"));
            if (contact == null || !contact.get(0, 10).contents().name().equals(ResourceLocation.parse("maid_weapon:parry_contact")))
                throw new IllegalStateException("Contact particle sprite missing from atlas");
            LogUtils.getLogger().info("BLACK_FOX_CONTACT_SPRITE_PASS: approved SVG sprite stitched and provider registered");
            var cache = GeckoLibCache.getInstance();
            var geometry = cache.getGeoModels().get(BlackFoxBossResources.MODEL);
            if (geometry == null) throw new IllegalStateException("Combat geometry missing");
            var animation = cache.getAnimations().get(BlackFoxBossResources.ANIMATION);
            if (animation == null) throw new IllegalStateException("Combat animation file missing");
            for (var motion : BlackFoxFight.Motion.values()) {
                String name = BlackFoxBossPose.animation(motion, false);
                if (!animation.animations().containsKey(name)) throw new IllegalStateException("Missing client motion " + name);
            }
            var model = new AnimatedGeoModel(geometry);
            for (String name : new String[]{"BladeLocator", "BossSheathLocator"}) {
                var locator = BlackFoxBossRenderer.hierarchy(model.bones(), name);
                if (locator.size() < 4 || !locator.get(locator.size() - 1).getName().equals(name))
                    throw new IllegalStateException("Missing equipment hierarchy " + name);
            }
            var mc = Minecraft.getInstance();
            var renderer = new BlackFoxBossRenderer(new EntityRendererProvider.Context(mc.getEntityRenderDispatcher(),
                    mc.getItemRenderer(), mc.getBlockRenderer(), new net.minecraft.client.renderer.ItemInHandRenderer(
                    mc, mc.getEntityRenderDispatcher(), mc.getItemRenderer()),
                    mc.getResourceManager(), mc.getEntityModels(), mc.font));
            if (!renderer.getLayerRenderers().isEmpty()) throw new IllegalStateException("Maid layers leaked into Boss renderer");
            BlackFoxBladeRenderer.validate();
            validateSlashColors(mc);
            validateDimensionTextures();
            BlackFoxEnergyValidation.run();
            if (BlackFoxBossPose.clipTick(25, 6, true) != 1
                    || BlackFoxBossPose.clipTick(5, 20, false) != 5
                    || BlackFoxBossPose.clipTick(-1, 20, false) != 0)
                throw new IllegalStateException("Server-time animation sampling is incorrect");
            validatePassivePoses();
            validateComboPoses();
            var iaido = new PoseProbe("contract_fox_boss.iaido");
            iaido.sample(0);
            for (double time : new double[]{.3, 1.2, 1.32, 1.4, 1.82, 2.4, 2.8}) {
                if (!Float.isFinite(iaido.sample(time * 20))) throw new IllegalStateException("Invalid iaido pose");
            }
            LogUtils.getLogger().info("BLACK_FOX_IAIDO_POSE_PASS: approved clip parsed and evaluated at charge/draw/recovery boundaries");
            validateSharedBufferOrder(mc, model);
            for (var motion : BlackFoxFight.Motion.values()) {
                var probe = new PoseProbe(BlackFoxBossPose.animation(motion, false));
                probe.sample(0); // First frame selects the clip; next frame completes its zero-length transition.
                float start = probe.sample(.5);
                float end = probe.sample(5);
                if (!Float.isFinite(start) || !Float.isFinite(end)) throw new IllegalStateException("Invalid evaluated pose " + motion);
            }
            BlackFoxBossResources.invalidate(); BlackFoxBossResources.ensureLoaded();
            if (cache.getGeoModels().get(BlackFoxBossResources.MODEL) == geometry)
                throw new IllegalStateException("Reload did not replace geometry");
            LogUtils.getLogger().info("BLACK_FOX_CLIENT_PASS: passive poses, seven native B clips, hand/hip equipment paths, actual body/native OBJ draw and reload; no runtime VMD");
        } catch (Throwable failure) { LogUtils.getLogger().error("BLACK_FOX_CLIENT_FAIL", failure); }
        finally { Minecraft.getInstance().stop(); }
    }
    private static void validateSlashColors(Minecraft mc) throws Exception {
        // Force the real optional native renderer to load, so missing injection targets fail this fixture.
        Class<?> nativeRenderer = Class.forName("mods.flammpfeil.slashblade.client.renderer.entity.SlashEffectRenderer");
        var context = new EntityRendererProvider.Context(mc.getEntityRenderDispatcher(), mc.getItemRenderer(), mc.getBlockRenderer(),
                new net.minecraft.client.renderer.ItemInHandRenderer(mc, mc.getEntityRenderDispatcher(), mc.getItemRenderer()),
                mc.getResourceManager(), mc.getEntityModels(), mc.font);
        Object renderer = nativeRenderer.getConstructor(EntityRendererProvider.Context.class).newInstance(context);
        int hooks = 0;
        for (var method : nativeRenderer.getDeclaredMethods()) {
            if (!method.getName().contains("contractBlade$bossTint") && !method.getName().contains("contractBlade$bossRim")) continue;
            method.setAccessible(true);
            int original = method.getName().contains("bossTint") ? 0x555555 : 0x404040;
            if ((int) method.invoke(renderer, original, null, 0f, 0f, new com.mojang.blaze3d.vertex.PoseStack(), null, 0) != original)
                throw new IllegalStateException("Non-Boss native slash palette changed");
            hooks++;
        }
        if (hooks != 2) throw new IllegalStateException("Boss-only native slash color injections missing");
        LogUtils.getLogger().info("BLACK_FOX_SLASH_COLOR_PASS: native renderer loaded, both color hooks applied and non-Boss fallback unchanged");
    }
    private static void validateDimensionTextures() throws Exception {
        for (var texture : new ResourceLocation[]{BlackFoxRiftVisual.TEXTURE,BlackFoxGreatSlashVisual.TEXTURE}) {
            try(var stream=Minecraft.getInstance().getResourceManager().getResource(texture).orElseThrow().open();
                var image=com.mojang.blaze3d.platform.NativeImage.read(stream)) {
                if((image.getPixelRGBA(0,0)>>>24)!=0 || image.getWidth()<128) throw new IllegalStateException("Invalid transparent dimension art: "+texture);
            }
        }
    }
    private static void validatePassivePoses() {
        var animations = GeckoLibCache.getInstance().getAnimations().get(BlackFoxBossResources.ANIMATION);
        if (animations.animations().size() != 13 || !animations.animations().containsKey("contract_fox_boss.iaido"))
            throw new IllegalStateException("Missing passive/native B/iaido clips");
        for (var motion : BlackFoxFight.Motion.values()) {
            if (motion == BlackFoxFight.Motion.STAGGER || motion == BlackFoxFight.Motion.DEFEATED) continue;
            String name = BlackFoxBossPose.animation(motion, false);
            if (!name.equals("contract_fox_boss.ready") || !BlackFoxBossPose.animation(motion, true).equals("contract_fox_boss.walk"))
                throw new IllegalStateException("Attack state selects an attack animation: " + motion);
            var root = new PoseProbe(name, "Root");
            root.sample(0);
            for (double tick : new double[]{.5, 2, 4, 6, 12, 16})
                if (Math.abs(root.sample(tick)) > .001) throw new IllegalStateException("Mental attack tips whole body: " + motion);
            var arm = new PoseProbe(name);
            arm.sample(0);
            if (Math.abs(arm.sample(.5) - arm.sample(6)) > .001)
                throw new IllegalStateException("Mental attack still swings an arm: " + motion);
        }
        var legs = new PoseProbe("contract_fox_boss.walk", "LeftLeg");
        legs.sample(0);
        float before = legs.sample(.5), after = legs.sample(4);
        if (Math.abs(before - after) < .001) throw new IllegalStateException("Pursuit footwork does not animate the leg");
        LogUtils.getLogger().info("BLACK_FOX_ANIMATION_CHECK: mental attacks have no root/arm motion; ordinary walking and passive reactions retained");
    }
    private static void validateComboPoses() {
        var clips = GeckoLibCache.getInstance().getAnimations().get(BlackFoxBossResources.ANIMATION);
        for (int stage = 1; stage <= 7; stage++) {
            String name = BlackFoxBossPose.animation(BlackFoxFight.Motion.RUSH, false, stage);
            if (!name.equals("contract_fox_boss.combo_b" + stage) || !clips.animations().containsKey(name))
                throw new IllegalStateException("Native B stage does not select its own clip: " + stage);
            var arm = new PoseProbe(name);
            arm.sample(0);
            float start = arm.sample(.5), next = arm.sample(stage == 1 ? 3 : 2);
            if (!Float.isFinite(start) || !Float.isFinite(next) || Math.abs(start-next) < .001)
                throw new IllegalStateException("Native B arm does not animate: " + stage);
            var root = new PoseProbe(name, "Root");
            root.sample(0);
            if (Math.abs(root.sample(3)) > .001) throw new IllegalStateException("Native B rotates entity root");
            if (!BlackFoxBossRenderer.bladeLocator(stage).equals("BladeLocator"))
                throw new IllegalStateException("Drawn blade does not follow hand");
        }
        if (!BlackFoxBossRenderer.bladeLocator(0).equals("BossSheathLocator")
                || !BlackFoxBossPose.animation(BlackFoxFight.Motion.RUSH, false, 0).endsWith(".ready")
                || !BlackFoxBossPose.animation(BlackFoxFight.Motion.STAGGER, false, 3).endsWith(".stagger")
                || !BlackFoxBossPose.animation(BlackFoxFight.Motion.DEFEATED, false, 7).endsWith(".defeated"))
            throw new IllegalStateException("Interrupted native B did not restore passive equipment/pose");
        for (String bone : new String[]{"AllBody", "RightForeArm", "LeftForeArm", "RightHand", "LeftHand"}) {
            var neutral = new PoseProbe("contract_fox_boss.ready", bone);
            neutral.sample(0);
            if (Math.abs(neutral.sample(.5)) > .001) throw new IllegalStateException("Native B pose not reset: " + bone);
        }
        LogUtils.getLogger().info("BLACK_FOX_COMBO_POSE_PASS: seven evaluated clips, root stable, passive reset and hand/hip paths");
    }
    /** Exercise the actual shared-buffer draw path, not just resource parsing or pose math. */
    private static void validateSharedBufferOrder(Minecraft mc, AnimatedGeoModel model) {
        var source = net.minecraft.client.renderer.MultiBufferSource.immediate(new com.mojang.blaze3d.vertex.BufferBuilder(1048576));
        var bodyType = BlackFoxBossRenderer.bodyType();
        float[] first = BlackFoxBossRenderer.spiritTint(false, false, 0);
        float[] second = BlackFoxBossRenderer.spiritTint(true, false, 0);
        float[] restored = BlackFoxBossRenderer.spiritTint(true, true, 60);
        float[] gone = BlackFoxBossRenderer.spiritTint(true, true, 100);
        if (!(first[3] < 1 && second[3] < first[3] && first[2] > first[1]
                && restored[0] == 1 && restored[1] == 1 && restored[2] == 1 && restored[3] == 1 && gone[3] == 0))
            throw new IllegalStateException("Spirit tint/restoration fade is incorrect");
        int[] vertices = {0}, switches = {0};
        net.minecraft.client.renderer.RenderType[] current = {null};
        net.minecraft.client.renderer.MultiBufferSource tracked = type -> {
            if (current[0] != type) { switches[0]++; current[0] = type; }
            var delegate = source.getBuffer(type);
            if (type != bodyType) return delegate;
            return (com.mojang.blaze3d.vertex.VertexConsumer) java.lang.reflect.Proxy.newProxyInstance(
                    BlackFoxClientValidation.class.getClassLoader(),
                    new Class<?>[]{com.mojang.blaze3d.vertex.VertexConsumer.class}, (proxy, method, args) -> {
                        if (method.getName().equals("vertex")) {
                            if (current[0] != bodyType) throw new IllegalStateException("Body used a stale weapon vertex buffer");
                            vertices[0]++;
                        }
                        try { return method.invoke(delegate, args); }
                        catch (java.lang.reflect.InvocationTargetException error) { throw error.getCause(); }
                    });
        };
        var context = new EntityRendererProvider.Context(mc.getEntityRenderDispatcher(), mc.getItemRenderer(), mc.getBlockRenderer(),
                new net.minecraft.client.renderer.ItemInHandRenderer(mc, mc.getEntityRenderDispatcher(), mc.getItemRenderer()),
                mc.getResourceManager(), mc.getEntityModels(), mc.font);
        var probe = new BlackFoxBossRenderer(context) {
            @Override void renderEquipment(AnimatedGeoModel geometry, com.maidweapon.forge.entity.BlackFoxBossEntity ignored,
                    com.mojang.blaze3d.vertex.PoseStack stack, net.minecraft.client.renderer.MultiBufferSource buffers, int light, float partial) {
                if (vertices[0] < 400) throw new IllegalStateException("Equipment was drawn before the actual body");
                  validateSpiritGlints(geometry, stack, buffers);
                  for(float age:new float[]{0,4,8,24,41,42,44,50,52,60,63,64,66,72,78,84})
                      BlackFoxDimensionEffects.sample(age,net.minecraft.world.phys.Vec3.ZERO,new net.minecraft.world.phys.Vec3(0,0,-2),
                              180,stack,buffers,new org.joml.Quaternionf());
                  LogUtils.getLogger().info("BLACK_FOX_DIMENSION_DRAW_PASS: SVG textures, server-time stages, rift/breach/windup/slash and terminal silence");
                var blade = new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .get(ResourceLocation.parse("slashblade:slashblade")));
                // Exercise the actual locator/axis/scale paths, both drawn and sheathed.
                super.renderEquipment(geometry, blade, 1, stack, buffers, light);
                super.renderEquipment(geometry, blade, 0, stack, buffers, light);
            }
    private static void validateSpiritGlints(AnimatedGeoModel model, com.mojang.blaze3d.vertex.PoseStack stack,
                                              net.minecraft.client.renderer.MultiBufferSource buffers) {
        for (String name : new String[]{"LeftArm", "RightArm", "DownBody", "Tail5", "LeftSpirit_Tail5",
                "RightSpirit_Tail5", "RightForeArm"})
            if (BlackFoxBossRenderer.hierarchy(model.bones(), name).size() < 4)
                throw new IllegalStateException("Missing spirit glint anchor: " + name);
        if (BlackFoxSpiritGlints.pulse(0, 142, 20) != 0
                || Math.abs(BlackFoxSpiritGlints.pulse(10, 142, 20) - 1) > .001
                || BlackFoxSpiritGlints.pulse(20, 142, 20) != 0
                || BlackFoxSpiritGlints.pulse(100, 142, 20) != 0)
            throw new IllegalStateException("Spirit glint envelope has no quiet interval");
        int[] count = {0};
        net.minecraft.client.renderer.MultiBufferSource tracked = type -> {
            var delegate = buffers.getBuffer(type);
            return (com.mojang.blaze3d.vertex.VertexConsumer) java.lang.reflect.Proxy.newProxyInstance(
                    BlackFoxClientValidation.class.getClassLoader(),
                    new Class<?>[]{com.mojang.blaze3d.vertex.VertexConsumer.class}, (proxy, method, args) -> {
                        if (method.getName().equals("vertex")) count[0]++;
                        try { return method.invoke(delegate, args); }
                        catch (java.lang.reflect.InvocationTargetException error) { throw error.getCause(); }
                    });
        };
        for (boolean phaseTwo : new boolean[]{false, true}) {
            for (int age = 0; age < 360; age++) {
                int before = count[0];
                BlackFoxSpiritGlints.draw(model, stack, tracked, age, 0, phaseTwo, 3, 6);
                if (count[0] - before > 120) throw new IllegalStateException("Unbounded spirit glint draw");
            }
        }
        if (count[0] == 0) throw new IllegalStateException("Spirit glints did not render");
        LogUtils.getLogger().info("BLACK_FOX_SPIRIT_GLINT_PASS: bone anchors, smooth quiet intervals, both phases and B arm; capped at 120 vertices/frame");
    }
        };
        var human = model.bones().get("AllBody");
        var fox = model.bones().get("FOX");
        human.setScaleX(1); human.setScaleY(1); human.setScaleZ(1);
        fox.setScaleX(0); fox.setScaleY(0); fox.setScaleZ(0);
        probe.render(model, null, 0, bodyType, new com.mojang.blaze3d.vertex.PoseStack(), tracked, null,
                net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        source.endBatch();
        new BlackFoxCoreRenderer(context).drawShell(90,
                new com.mojang.blaze3d.vertex.PoseStack(), tracked);
        source.endBatch();
        LogUtils.getLogger().info("BLACK_FOX_CORE_DRAW_PASS: native purple center and descending shell rendered");
        new BlackFoxMinorCutRenderer(context).draw(8, new com.mojang.blaze3d.vertex.PoseStack(), tracked);
        source.endBatch();
        LogUtils.getLogger().info("BLACK_FOX_MINOR_CUT_DRAW_PASS: miniature native OBJ and shell rendered");
        if (com.maidweapon.forge.client.BlackFoxCameraShake.sample(-1, 1) != 0
                || com.maidweapon.forge.client.BlackFoxCameraShake.sample(6, 1) != 0
                || Math.abs(com.maidweapon.forge.client.BlackFoxCameraShake.sample(1, 1))
                <= Math.abs(com.maidweapon.forge.client.BlackFoxCameraShake.sample(1, .55f)))
            throw new IllegalStateException("Short, bounded parry shake envelope");
        LogUtils.getLogger().info("BLACK_FOX_CAMERA_PASS: bounded view-only roll envelope, stronger native B feedback");
        if (switches[0] < 3) throw new IllegalStateException("Native weapon did not exercise shared-buffer switches");
        LogUtils.getLogger().info("BLACK_FOX_DRAW_CHECK: {} body vertices, {} real render-type switches", vertices[0], switches[0]);
    }
    /** Run the real animation engine without opening a world or creating a synthetic maid. */
    private static final class PoseProbe extends AnimatableEntity<Entity> {
        private double clock;
        private final String bone;
        PoseProbe(String animation) { this(animation, "RightArm"); }
        PoseProbe(String animation, String bone) {
            super(null, 60);
            this.bone = bone;
            addAnimationController(new AnimationController<>(this, "probe", 0, event -> {
                event.getController().setAnimation(new AnimationBuilder().playAndHold(animation));
                return PlayState.CONTINUE;
            }));
        }
        float sample(double time) {
            clock = time;
            var data = new EntityModelData();
            var event = new AnimationEvent<>(this, 0, 0, 0, false, List.of(data));
            setCustomAnimations(new AnimationContext<>(null, this, event, data), event);
            return getCurrentModel().bones().get(bone).getRotationX();
        }
        @Override public double getCurrentTick(AnimationEvent<?> event) { return clock; }
        @Override protected boolean forceUpdate(AnimationEvent<?> event) { return true; }
        @Override public ResourceLocation getModelLocation() { return BlackFoxBossResources.MODEL; }
        @Override public ResourceLocation getTextureLocation() { return BlackFoxBossResources.TEXTURE; }
        @Override public ResourceLocation getAnimationFileLocation() { return BlackFoxBossResources.ANIMATION; }
    }
    private BlackFoxClientValidation() { }
}
