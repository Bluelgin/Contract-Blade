package com.maidweapon.forge.client;

import com.maidweapon.common.AkatsukiDomainConfig;
import com.maidweapon.forge.entity.SoulAnchorEntity;
import com.maidweapon.forge.init.ModSoulEntities;
import com.maidweapon.forge.network.SoulBurstNetwork.Cue;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** Batched untextured shards: a readable core, dark glass shell and brief target tether. */
public final class SoulAnchorRenderer extends EntityRenderer<SoulAnchorEntity> {
  public SoulAnchorRenderer(EntityRendererProvider.Context c) {
    super(c);
    shadowRadius = 0;
  }

  @Override
  public ResourceLocation getTextureLocation(SoulAnchorEntity e) {
    return ResourceLocation.parse("minecraft:textures/misc/white.png");
  }

  @Override
  public void render(
      SoulAnchorEntity e,
      float yaw,
      float partial,
      PoseStack pose,
      MultiBufferSource buffers,
      int light) {
    var camera = entityRenderDispatcher.camera.getPosition();
    if (e.distanceToSqr(camera) > 4096) return;
    double time = e.tickCount + partial;
    float shake = e.shakeAmount(partial);
    pose.pushPose();
    pose.translate(
        Math.sin(time * 3.1) * shake * .1,
        .85 + Math.sin(time * .09) * .08,
        Math.cos(time * 2.7) * shake * .1);
    pose.scale(.72f, .85f, .72f);
    var b = buffers.getBuffer(SoulRenderTypes.SHARDS);
    int quality = AkatsukiDomainConfig.QUALITY.get(),
        count = quality == 0 ? 3 : quality == 1 ? 6 : 9;
    for (int i = 0; i < count; i++) {
      pose.pushPose();
      pose.mulPose(Axis.YP.rotationDegrees((float) (i * 137.5 + time * .65)));
      pose.mulPose(Axis.ZP.rotationDegrees((float) (i * 27 + Math.sin(time * .07 + i) * 8)));
      double offset = i == 0 ? 0 : .28 + Math.sin(i * 2.1) * .08;
      shard(b, pose.last().pose(), offset, .22 + i % 3 * .08, .58 - i % 2 * .12, 0x2C050D, .56f);
      shard(b, pose.last().pose(), offset, .045, .48, 0xB81B35, .75f);
      pose.popPose();
    }
    float pulse = (float) (.12 + Math.sin(time * .17) * .025);
    shard(b, pose.last().pose(), 0, pulse, .25, 0xE33245, .9f);
    shard(b, pose.last().pose(), 0, .028, .18, 0xFFE1D5, .9f);
    // Purely client-side constellation: the target remains the original single hitbox.
    if (e.eclipse()) {
      pose.pushPose();
      pose.mulPose(entityRenderDispatcher.cameraOrientation());
      SoulEclipseGlyph.idle(b, pose.last().pose(), time, quality, e.chainProgress());
      pose.popPose();
    }
    if ((e.tickCount < 12 || shake > 0) && quality > 0) {
      var target = e.level().getEntity(e.targetId());
      if (target != null && target.distanceToSqr(e) < 1024) {
        var delta = target.getBoundingBox().getCenter().subtract(e.position().add(0, .85, 0));
        ribbon(
            b,
            pose.last().pose(),
            Vec3.ZERO,
            delta.multiply(1 / .72, 1 / .85, 1 / .72),
            .012f,
            0x9D142B,
            shake > 0 ? shake * .6f : (12 - e.tickCount) / 20f);
      }
    }
    if (owned(e)) {
      // The attack must land on the entity's real hitbox; this reticle is centered
      // on that hitbox, unlike the short decorative burst at its spawn position.
      var marker = buffers.getBuffer(SoulRenderTypes.OWNER_MARKER);
      pose.pushPose();
      pose.mulPose(entityRenderDispatcher.cameraOrientation());
      float glow = (float) (.84 + .12 * Math.sin(time * .18));
      ring(marker, pose.last().pose(), .72, .89, 0xFFE2CF, glow);
      ring(marker, pose.last().pose(), 1.00, 1.06, 0xFF3559, glow * .78f);
      cross(marker, pose.last().pose(), 0xFF6471, glow);
      pose.popPose();

      // A persistent, lightweight guide survives the first 12 ticks and is shown
      // even with minimum visual quality. It is owner-only, never another player's UI.
      var target = e.level().getEntity(e.targetId());
      if (target != null && target.distanceToSqr(e) < 1024) {
        var delta = target.getBoundingBox().getCenter().subtract(e.position().add(0, .85, 0));
        ribbon(marker, pose.last().pose(), Vec3.ZERO,
            delta.multiply(1 / .72, 1 / .85, 1 / .72),
            .035f, 0xFF4860, .50f);
      }
    }
    pose.popPose();
  }

  /** Billboard annulus, not an enlarged collision hitbox. */
  private static void ring(
      VertexConsumer b, Matrix4f m, double inner, double outer, int rgb, float alpha) {
    final int steps = 32;
    for (int i = 0; i < steps; i++) {
      double a = i * Math.PI * 2 / steps;
      double z = (i + 1) * Math.PI * 2 / steps;
      vertex(b, m, new Vec3(Math.cos(a) * outer, Math.sin(a) * outer, 0), rgb, alpha);
      vertex(b, m, new Vec3(Math.cos(z) * outer, Math.sin(z) * outer, 0), rgb, alpha);
      vertex(b, m, new Vec3(Math.cos(z) * inner, Math.sin(z) * inner, 0), rgb, alpha);
      vertex(b, m, new Vec3(Math.cos(a) * inner, Math.sin(a) * inner, 0), rgb, alpha);
    }
  }

  private static void cross(VertexConsumer b, Matrix4f m, int rgb, float alpha) {
    // Four short brackets direct the eye toward the actual core center.
    for (int i = 0; i < 4; i++) {
      double a = i * Math.PI / 2;
      double x = Math.cos(a), y = Math.sin(a);
      double sx = -y * .07, sy = x * .07;
      vertex(b, m, new Vec3(x * .31 + sx, y * .31 + sy, 0), rgb, alpha);
      vertex(b, m, new Vec3(x * .61 + sx, y * .61 + sy, 0), rgb, alpha);
      vertex(b, m, new Vec3(x * .61 - sx, y * .61 - sy, 0), rgb, alpha);
      vertex(b, m, new Vec3(x * .31 - sx, y * .31 - sy, 0), rgb, alpha);
    }
  }

  private static void shard(
      VertexConsumer b,
      Matrix4f m,
      double offset,
      double width,
      double height,
      int color,
      float alpha) {
    var top = new Vec3(offset, height, 0);
    var bottom = new Vec3(offset, -height, 0);
    for (int i = 0; i < 4; i++) {
      double a = i * Math.PI / 2, z = (i + 1) * Math.PI / 2;
      var p = new Vec3(offset + Math.cos(a) * width, 0, Math.sin(a) * width);
      var q = new Vec3(offset + Math.cos(z) * width, 0, Math.sin(z) * width);
      vertex(b, m, top, color, alpha);
      vertex(b, m, p, color, alpha);
      vertex(b, m, q, color, alpha);
      vertex(b, m, q, color, alpha);
      vertex(b, m, bottom, color, alpha * .7f);
      vertex(b, m, q, color, alpha * .7f);
      vertex(b, m, p, color, alpha * .7f);
      vertex(b, m, p, color, alpha * .7f);
    }
  }

  private static void ribbon(
      VertexConsumer b, Matrix4f m, Vec3 a, Vec3 z, float width, int color, float alpha) {
    var direction = z.subtract(a);
    var side = direction.cross(new Vec3(0, 1, 0));
    if (side.lengthSqr() < 1e-8) side = direction.cross(new Vec3(1, 0, 0));
    side = side.normalize().scale(width);
    vertex(b, m, a.add(side), color, alpha);
    vertex(b, m, z.add(side), color, alpha);
    vertex(b, m, z.subtract(side), color, alpha);
    vertex(b, m, a.subtract(side), color, alpha);
  }

  private static void vertex(VertexConsumer b, Matrix4f m, Vec3 v, int rgb, float alpha) {
    b.vertex(m, (float) v.x, (float) v.y, (float) v.z)
        .color(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, (int) (alpha * 255))
        .endVertex();
  }

  public static boolean owned(SoulAnchorEntity core) {
    var player = Minecraft.getInstance().player;
    return player != null && core.ownerId().filter(player.getUUID()::equals).isPresent();
  }

  public static void burst(Cue p) {
    var mc = Minecraft.getInstance();
    if (mc.level == null
        || p.stages() < 3
        || p.stages() > 5
        || p.stage() >= p.stages()
        || !Double.isFinite(p.target().lengthSqr())
        || !Double.isFinite(p.core().lengthSqr())) return;
    SoulEclipseVisuals.accept(p);
    AkatsukiDomainVisuals.soulBurst(p);
    if (mc.player != null && mc.player.distanceToSqr(p.target()) < 4096)
      mc.level.playLocalSound(
          p.target().x,
          p.target().y,
          p.target().z,
          p.stage() == p.stages() - 1 ? SoundEvents.TRIDENT_HIT : SoundEvents.PLAYER_ATTACK_SWEEP,
          SoundSource.PLAYERS,
          .65f,
          p.stage() == p.stages() - 1 ? .65f : 1.2f,
          false);
  }

  @Mod.EventBusSubscriber(
      modid = "maid_weapon",
      value = Dist.CLIENT,
      bus = Mod.EventBusSubscriber.Bus.MOD)
  public static final class Registration {
    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
      e.registerEntityRenderer(ModSoulEntities.ANCHOR.get(), SoulAnchorRenderer::new);
    }
  }
}
