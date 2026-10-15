package com.maidweapon.forge.client;

import static com.maidweapon.forge.network.AkatsukiDomainNetwork.*;
import static com.maidweapon.forge.system.akatsuki.AkatsukiDomainState.Phase.*;

import com.maidweapon.common.AkatsukiDomainConfig;
import com.maidweapon.forge.network.AkatsukiDomainNetwork.Cue;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Bounded client director: instantaneous layered fissures and native decorative sword formations.
 */
@Mod.EventBusSubscriber(modid = "maid_weapon", value = Dist.CLIENT)
public final class AkatsukiDomainVisuals {
  private static final class Domain {
    Cue cue;
    long end;
    int targetId = -1;
    Vec3 target;
    final AkatsukiVisualDirector director = new AkatsukiVisualDirector();

    Domain(Cue cue) {
      this.cue = cue;
      target = cue.target();
    }
  }

  private record Cut(
      UUID owner,
      Vec3 center,
      Vec3 collapseTo,
      long start,
      long seed,
      double length,
      double width) {}

  private record Sword(UUID owner, Vec3 start, Vec3 end, int targetId, long born, int flight) {}

  private static final Map<UUID, Domain> DOMAINS = new LinkedHashMap<>();
  private static final Deque<Cut> CUTS = new ArrayDeque<>();
  private static final Deque<Sword> SWORDS = new ArrayDeque<>();
  private static Object levelIdentity;
  private static long sequence;
  private static final int BLOOD = 0xAE1027, LIGHT = 0xFFE2DB, DARK = 0x100309;

  private static void world() {
    var level = Minecraft.getInstance().level;
    if (level != levelIdentity) {
      DOMAINS.clear();
      CUTS.clear();
      SWORDS.clear();
      levelIdentity = level;
      // The provider owns its shared tint timer. Our lease expires without touching native
      // execution.
    }
  }

  public static void accept(Cue p) {
    world();
    var level = Minecraft.getInstance().level;
    if (level == null
        || !Double.isFinite(p.center().lengthSqr())
        || !Double.isFinite(p.target().lengthSqr())
        || !Float.isFinite(p.radius())
        || p.radius() < 0
        || p.radius() > 16
        || p.ticks() < 0
        || p.ticks() > 400
        || p.kind() < 0
        || p.kind() > REIGNITE
        || p.phase() == null) return;
    long now = level.getGameTime();
    if (p.kind() == END) {
      DOMAINS.remove(p.owner());
      CUTS.removeIf(c -> c.owner().equals(p.owner()));
      SWORDS.removeIf(c -> c.owner().equals(p.owner()));
      return;
    }
    if (DOMAINS.size() >= 16 && !DOMAINS.containsKey(p.owner())) {
      var viewer = Minecraft.getInstance().player;
      if (viewer == null) return;
      var farthest =
          DOMAINS.entrySet().stream()
              .filter(entry -> !entry.getKey().equals(viewer.getUUID()))
              .max(
                  Comparator.comparingDouble(
                      entry -> center(entry.getValue().cue, 1).distanceToSqr(viewer.position())))
              .orElse(null);
      if (farthest == null
          || !p.owner().equals(viewer.getUUID())
              && p.center().distanceToSqr(viewer.position())
                  >= center(farthest.getValue().cue, 1).distanceToSqr(viewer.position())) return;
      DOMAINS.remove(farthest.getKey());
    }
    var d = DOMAINS.computeIfAbsent(p.owner(), id -> new Domain(p));
    var oldPhase = d.cue.phase();
    d.cue = p;
    d.end = now + 15;
    if (p.targetId() >= 0) {
      d.targetId = p.targetId();
      d.target = p.target();
    }
    if (p.kind() == COLLAPSE) {
      var converging =
          CUTS.stream()
              .filter(c -> c.owner().equals(p.owner()))
              .limit(32)
              .map(
                  c ->
                      new Cut(
                          c.owner(),
                          c.center(),
                          p.center().add(0, 1, 0),
                          now,
                          c.seed(),
                          c.length(),
                          c.width()))
              .toList();
      CUTS.removeIf(c -> c.owner().equals(p.owner()));
      CUTS.addAll(converging);
      SWORDS.removeIf(c -> c.owner().equals(p.owner()));
      for (int i = 0; i < 8; i++)
        cut(p.owner(), p.center().add(0, 1, 0), now + i, 3.5 - i * .3, .18);
      sound(p.center(), true);
    } else if (p.phase() == RESCUE_REIGNITE && oldPhase != RESCUE_REIGNITE) {
      for (int i = 0; i < 10; i++) cut(p.owner(), p.center().add(0, 1, 0), now + i % 3, 5, .24);
      sound(p.center(), false);
    } else if (p.kind() == EDGE) {
      d.director.attack(now);
      cut(p.owner(), p.target(), now, 2.4, .16); // Immediate response, independent of pulse timers.
    } else if (p.kind() == FINAL || p.kind() == EXECUTION) {
      cut(p.owner(), p.target(), now, p.kind() == FINAL ? 7 : 5, .48);
      sound(p.target(), false);
    } else if (p.kind() == RAIN || p.kind() == COUNTER || p.kind() == RING || p.kind() == CHAIN) {
      if (p.kind() == RAIN) formation(d, now, true);
      else for (int i = 0; i < 3; i++) cut(p.owner(), p.target(), now + i, 3, .2);
    }
  }

  private static void sound(Vec3 point, boolean collapse) {
    var mc = Minecraft.getInstance();
    if (mc.player == null || point.distanceToSqr(mc.player.position()) > 1024) return;
    mc.level.playLocalSound(
        point.x,
        point.y,
        point.z,
        collapse ? SoundEvents.RESPAWN_ANCHOR_DEPLETE.value() : SoundEvents.PLAYER_ATTACK_SWEEP,
        SoundSource.PLAYERS,
        collapse ? .8f : .45f,
        collapse ? .55f : .7f,
        false);
  }

  @SubscribeEvent
  public static void tick(TickEvent.ClientTickEvent e) {
    if (e.phase != TickEvent.Phase.END) return;
    world();
    var mc = Minecraft.getInstance();
    if (mc.level == null) return;
    long now = mc.level.getGameTime();
    DOMAINS.values().removeIf(d -> d.end <= now);
    CUTS.removeIf(c -> now - c.start() > 9);
    SWORDS.removeIf(s -> now - s.born() > s.flight() + 5);
    int quality = AkatsukiDomainConfig.QUALITY.get();
    for (var d : DOMAINS.values()) {
      var p = d.cue;
      boolean rampage = p.phase() == RAMPAGE;
      if (mc.player != null
          && p.owner().equals(mc.player.getUUID())
          && rampage
          && AkatsukiDomainConfig.TINT.get()
          && now % 4 == 0) AkatsukiNativeVisualBridge.renewTint();
      if (p.phase() != NORMAL && p.phase() != RAMPAGE
          || mc.player == null
          || center(p, 1).distanceToSqr(mc.player.position()) > (quality == 0 ? 576 : 2304))
        continue;
      var target = mc.level.getEntity(d.targetId);
      if (target == null
          || !target.isAlive()
          || target.position().distanceToSqr(center(p, 1)) > p.radius() * p.radius()) continue;
      d.target = target.getBoundingBox().getCenter();
      int count = d.director.cuts(now, rampage);
      count = Math.min(count, quality == 0 ? 1 : quality == 1 ? 4 : 5);
      for (int i = 0; i < count; i++)
        cut(
            p.owner(),
            d.target,
            now + i % 2,
            2.5 + .3 * d.director.intensity(now),
            rampage ? .23 : .17);
      if ((p.features() & 2) != 0 && d.director.swords(now, rampage)) formation(d, now, false);
    }
  }

  private static void cut(UUID owner, Vec3 center, long now, double length, double width) {
    int cap = AkatsukiDomainConfig.QUALITY.get() == 0 ? 48 : 192;
    while (CUTS.size() >= cap) CUTS.removeFirst();
    CUTS.addLast(
        new Cut(
            owner,
            center,
            null,
            now,
            ++sequence * 73428767L ^ owner.getLeastSignificantBits(),
            length,
            width));
  }

  private static void formation(Domain d, long now, boolean rain) {
    int quality = AkatsukiDomainConfig.QUALITY.get();
    boolean rampage = d.cue.phase() == RAMPAGE;
    int count = rain ? Mth.clamp(d.cue.blades(), 8, 96) * (rampage ? 2 : 1) : rampage ? 6 : 3;
    count =
        Math.max(
            1,
            (int)
                (count
                    * AkatsukiDomainConfig.DENSITY.get()
                    * (quality == 0 ? .25 : quality == 1 ? .7 : 1)));
    var random = new Random(++sequence);
    var origin = center(d.cue, 1);
    for (int i = 0; i < count; i++) {
      double angle = (i + .5) * Math.PI * 2 / count;
      Vec3 end =
          rain
              ? d.target.add((random.nextDouble() - .5) * 5, 0, (random.nextDouble() - .5) * 5)
              : d.target;
      Vec3 start =
          rain
              ? end.add(.6, 8 + random.nextDouble() * 3, .3)
              : origin.add(Math.cos(angle) * 2.2, 2.5 + .3 * (i % 3), Math.sin(angle) * 2.2);
      int cap = quality == 0 ? 48 : quality == 1 ? 160 : 256;
      while (SWORDS.size() >= cap) SWORDS.removeFirst();
      SWORDS.addLast(
          new Sword(
              d.cue.owner(),
              start,
              end,
              rain ? -1 : d.targetId,
              now + i % (rain ? 8 : 3),
              rampage ? 8 : 12));
    }
  }

  @SubscribeEvent
  public static void render(RenderLevelStageEvent e) {
    if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
    world();
    var mc = Minecraft.getInstance();
    if (mc.level == null || CUTS.isEmpty() && SWORDS.isEmpty()) return;
    var camera = e.getCamera().getPosition();
    double time = mc.level.getGameTime() + e.getPartialTick();
    double maxDistance = AkatsukiDomainConfig.QUALITY.get() == 0 ? 576 : 2304;
    var pose = e.getPoseStack();
    pose.pushPose();
    pose.translate(-camera.x, -camera.y, -camera.z);
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    RenderSystem.disableCull();
    RenderSystem.depthMask(false);
    try {
      RenderSystem.setShader(GameRenderer::getPositionColorShader);
      var b = Tesselator.getInstance().getBuilder();
      b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
      for (var c : CUTS)
        if (c.center().distanceToSqr(camera) < maxDistance)
          fissure(b, pose.last().pose(), c, time, camera);
      BufferUploader.drawWithShader(b.end());
      var buffers = mc.renderBuffers().bufferSource();
      for (var s : SWORDS) {
        double age = time - s.born();
        if (age < 0 || age > s.flight() + 5 || s.start().distanceToSqr(camera) > maxDistance)
          continue;
        Vec3 end = s.end();
        var entity = mc.level.getEntity(s.targetId());
        if (entity != null && entity.isAlive()) end = entity.getBoundingBox().getCenter();
        double progress = Mth.clamp((age - 2) / s.flight(), 0, 1);
        Vec3 point = s.start().lerp(end, progress * progress);
        Vec3 direction = end.subtract(s.start()).normalize();
        pose.pushPose();
        try {
          pose.translate(point.x, point.y, point.z);
          float yaw = (float) Math.toDegrees(Math.atan2(direction.z, direction.x));
          float pitch = (float) Math.toDegrees(Math.asin(Mth.clamp(direction.y, -1, 1)));
          pose.mulPose(Axis.YP.rotationDegrees(-yaw));
          pose.mulPose(Axis.ZP.rotationDegrees(pitch));
          pose.scale(.0075f, .0075f, .0075f);
          pose.mulPose(Axis.YP.rotationDegrees(90));
          AkatsukiNativeVisualBridge.sword(
              pose, buffers, (float) Math.min(1, (s.flight() + 5 - age) / 4));
        } finally {
          pose.popPose();
        }
      }
      buffers.endBatch();
    } finally {
      pose.popPose();
      RenderSystem.depthMask(true);
      RenderSystem.enableCull();
      RenderSystem.disableBlend();
      RenderSystem.setShaderColor(1, 1, 1, 1);
    }
  }

  private static Vec3 center(Cue cue, float partial) {
    var p = Minecraft.getInstance().level.getPlayerByUUID(cue.owner());
    return p == null
        ? cue.center()
        : new Vec3(
            Mth.lerp(partial, p.xo, p.getX()),
            Mth.lerp(partial, p.yo, p.getY()),
            Mth.lerp(partial, p.zo, p.getZ()));
  }

  /**
   * Camera-facing, tapered, asymmetric crack. Telegraph -> black cavity/red lips -> white impact ->
   * afterimage.
   */
  private static void fissure(BufferBuilder b, Matrix4f m, Cut c, double time, Vec3 camera) {
    double age = time - c.start();
    if (age < 0 || age > 8) return;
    double converge = c.collapseTo() == null ? 0 : Math.min(1, age / 8);
    Vec3 location = c.collapseTo() == null ? c.center() : c.center().lerp(c.collapseTo(), converge);
    double length = c.length() * (1 - converge * .8);
    var random = new Random(c.seed());
    double angle = random.nextDouble() * Math.PI * 2;
    Vec3 normal = camera.subtract(location).normalize();
    Vec3 right = normal.cross(new Vec3(0, 1, 0));
    if (right.lengthSqr() < .001) right = new Vec3(1, 0, 0);
    right = right.normalize();
    Vec3 up = right.cross(normal).normalize();
    Vec3 along = right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
    Vec3 side = normal.cross(along).normalize();
    Vec3 center = location.add(side.scale((random.nextDouble() - .5) * 1.2));
    float fade = (float) Math.max(0, 1 - Math.max(0, age - 2) / 6);
    double width = age < 1 ? .008 : c.width() * Math.exp(-Math.max(0, age - 2) * .48);
    // Every segment tapers to a sharp tip, with jagged lips and a near-black interior.
    for (int i = 0; i < 8; i++) {
      double t0 = i / 8d, t1 = (i + 1) / 8d;
      double w0 = Math.sin(t0 * Math.PI) * width * (.7 + random.nextDouble() * .5);
      double w1 = Math.sin(t1 * Math.PI) * width * (.7 + random.nextDouble() * .5);
      Vec3 a = center.add(along.scale((t0 - .5) * length));
      Vec3 z = center.add(along.scale((t1 - .5) * length));
      feather(b, m, a, z, side, w0, w1, BLOOD, fade * .8f);
      strip(b, m, a.add(normal.scale(.002)), z.add(normal.scale(.002)), side, w0, w1, DARK, fade);
      if (age < 3)
        strip(
            b,
            m,
            a.add(side.scale(w0)).add(normal.scale(.004)),
            z.add(side.scale(w1)).add(normal.scale(.004)),
            side,
            .012,
            .008,
            LIGHT,
            fade);
    }
  }

  private static void feather(
      BufferBuilder b,
      Matrix4f m,
      Vec3 a,
      Vec3 z,
      Vec3 side,
      double wa,
      double wz,
      int color,
      float alpha) {
    for (int sign : new int[] {-1, 1}) {
      Vec3 p = a.add(side.scale(sign * wa)), q = z.add(side.scale(sign * wz));
      Vec3 r = z.add(side.scale(sign * wz * 2.4)), s = a.add(side.scale(sign * wa * 2.4));
      vertex(b, m, p, color, alpha);
      vertex(b, m, q, color, alpha);
      vertex(b, m, r, color, 0);
      vertex(b, m, p, color, alpha);
      vertex(b, m, r, color, 0);
      vertex(b, m, s, color, 0);
    }
  }

  private static void strip(
      BufferBuilder b,
      Matrix4f m,
      Vec3 a,
      Vec3 z,
      Vec3 side,
      double wa,
      double wz,
      int color,
      float alpha) {
    Vec3 p = a.add(side.scale(wa)), q = z.add(side.scale(wz));
    Vec3 r = z.subtract(side.scale(wz)), s = a.subtract(side.scale(wa));
    vertex(b, m, p, color, alpha);
    vertex(b, m, q, color, alpha);
    vertex(b, m, r, color, alpha);
    vertex(b, m, p, color, alpha);
    vertex(b, m, r, color, alpha);
    vertex(b, m, s, color, alpha);
  }

  private static void vertex(BufferBuilder b, Matrix4f m, Vec3 v, int color, float alpha) {
    b.vertex(m, (float) v.x, (float) v.y, (float) v.z)
        .color(
            color >> 16 & 255,
            color >> 8 & 255,
            color & 255,
            Mth.clamp((int) (alpha * 255), 0, 255))
        .endVertex();
  }

  @Mod.EventBusSubscriber(
      modid = "maid_weapon",
      value = Dist.CLIENT,
      bus = Mod.EventBusSubscriber.Bus.MOD)
  public static final class Reload {
    @SubscribeEvent
    public static void register(
        net.minecraftforge.client.event.RegisterClientReloadListenersEvent e) {
      e.registerReloadListener(
          (net.minecraft.server.packs.resources.ResourceManagerReloadListener)
              manager -> {
                CUTS.clear();
                SWORDS.clear();
                AkatsukiNativeVisualBridge.reload();
              });
    }
  }

  private AkatsukiDomainVisuals() {}
}
