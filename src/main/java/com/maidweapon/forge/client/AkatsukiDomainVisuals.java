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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
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
    final AkatsukiRainDirector rain;

    Domain(Cue cue) {
      this.cue = cue;
      target = cue.target();
      rain = new AkatsukiRainDirector(cue.owner().getLeastSignificantBits() ^ ++sequence);
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

  private record Sword(
      UUID owner,
      Vec3 start,
      Vec3 end,
      int targetId,
      double born,
      double reveal,
      double flight,
      boolean rain,
      float size) {
    double lifetime() {
      return reveal + flight + (rain ? 1.5 : 3);
    }
  }

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

  public static void soulBurst(com.maidweapon.forge.network.SoulBurstNetwork.Cue cue) {
    world();
    var mc = Minecraft.getInstance();
    if (mc.level == null || mc.player == null) return;
    long now = mc.level.getGameTime();
    if (mc.player.distanceToSqr(cue.core()) <= 4096)
      appendCut(
          new Cut(
              new UUID(0, cue.seed()),
              cue.core(),
              null,
              now,
              cue.seed() ^ 19349663L,
              cue.stage() == cue.stages() - 1 ? 1.6 : .9,
              cue.success() ? .045 : .015));
    if (mc.player.distanceToSqr(cue.target()) > 4096) return;
    int count = AkatsukiDomainConfig.QUALITY.get() == 0 ? 2 : cue.stage() == 0 ? 3 : 7;
    for (int i = 0; i < count; i++)
      appendCut(
          new Cut(
              new UUID(0, cue.seed()),
              cue.target(),
              cue.stage() == cue.stages() - 1 ? cue.target() : null,
              now + i % 3,
              cue.seed() + i * 19349663L,
              cue.stage() == cue.stages() - 2 ? 7 : 4,
              cue.success() ? .17 : .055));
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
    if (p.phase() != NORMAL && p.phase() != RAMPAGE) d.rain.cancel();
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
      for (int i = 0; i < 8; i++) {
        double angle = i * Math.PI / 4;
        Vec3 from = p.center().add(Math.cos(angle) * 1.8, 1.4, Math.sin(angle) * 1.8);
        appendCut(
            new Cut(
                p.owner(),
                from,
                p.center().add(0, 1.4, 0),
                now + i % 3,
                ++sequence * 73428767L,
                .9,
                .025));
      }
      sound(p.center(), true);
    } else if (p.phase() == RESCUE_REIGNITE && oldPhase != RESCUE_REIGNITE) {
      for (int i = 0; i < 10; i++) cut(p.owner(), p.center().add(0, 1, 0), now + i % 3, 5, .09);
      sound(p.center(), false);
    } else if (p.kind() == EDGE) {
      d.director.attack(now);
      cut(
          p.owner(),
          p.target(),
          now,
          2.4,
          .045); // Immediate response, independent of pulse timers.
    } else if (p.kind() == FINAL || p.kind() == EXECUTION) {
      cut(p.owner(), p.target(), now, p.kind() == FINAL ? 7 : 5, .16);
      sound(p.target(), false);
    } else if (p.kind() == RAIN || p.kind() == COUNTER || p.kind() == RING || p.kind() == CHAIN) {
      if (p.kind() == RAIN && (p.phase() == NORMAL || p.phase() == RAMPAGE))
        d.rain.begin(
            now,
            p.blades(),
            p.phase() == RAMPAGE,
            AkatsukiDomainConfig.QUALITY.get(),
            AkatsukiDomainConfig.DENSITY.get());
      else for (int i = 0; i < 3; i++) cut(p.owner(), p.target(), now + i, 3, .055);
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
    CUTS.removeIf(c -> now - c.start() > 6);
    SWORDS.removeIf(s -> now - s.born() > s.lifetime());
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
          || center(p, 1).distanceToSqr(mc.player.position()) > (quality == 0 ? 576 : 2304)) {
        d.rain.cancel();
        continue;
      }
      var target = mc.level.getEntity(d.targetId);
      if (target == null
          || !target.isAlive()
          || target.position().distanceToSqr(center(p, 1)) > p.radius() * p.radius()) {
        d.rain.cancel();
        continue;
      }
      d.target = target.getBoundingBox().getCenter();
      int count = d.director.cuts(now, rampage);
      count = Math.min(count, quality == 0 ? 1 : quality == 1 ? 4 : 5);
      for (int i = 0; i < count; i++)
        cut(
            p.owner(),
            d.target,
            now + i % 2,
            2.5 + .3 * d.director.intensity(now),
            rampage ? .065 : .045);
      var motion = target.position().subtract(new Vec3(target.xo, target.yo, target.zo));
      if (d.director.intensity(now) <= 0) d.rain.cancel();
      for (var drop : d.rain.poll(now, motion.x, motion.z)) {
        Vec3 point = target.position().add(drop.x(), 0, drop.z());
        if (!mc.level.hasChunkAt(net.minecraft.core.BlockPos.containing(point))) continue;
        var hit =
            mc.level.clip(
                new ClipContext(
                    point.add(0, 2.5, 0),
                    point.add(0, -6, 0),
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    mc.player));
        // Swords land at solid terrain, not a hovering target center or the bottom of the world.
        if (hit.getType() != HitResult.Type.BLOCK) continue;
        Vec3 end = hit.getLocation().add(0, .35, 0);
        appendSword(
            new Sword(
                p.owner(),
                end.add(drop.leanX(), drop.height(), drop.leanZ()),
                end,
                -1,
                drop.born(),
                drop.reveal(),
                drop.flight(),
                true,
                .0038f));
      }
    }
  }

  private static void cut(UUID owner, Vec3 center, long now, double length, double width) {
    appendCut(
        new Cut(
            owner,
            center,
            null,
            now,
            ++sequence * 73428767L ^ owner.getLeastSignificantBits(),
            length,
            width));
  }

  private static void appendCut(Cut cut) {
    int cap = AkatsukiDomainConfig.QUALITY.get() == 0 ? 48 : 192;
    while (CUTS.size() >= cap) CUTS.removeFirst();
    CUTS.addLast(cut);
  }

  private static void appendSword(Sword sword) {
    int quality = AkatsukiDomainConfig.QUALITY.get();
    int cap = quality == 0 ? 48 : quality == 1 ? 160 : 256;
    while (SWORDS.size() >= cap) SWORDS.removeFirst();
    SWORDS.addLast(sword);
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
      for (var s : SWORDS)
        if (s.rain() && s.end().distanceToSqr(camera) < maxDistance)
          rainTrace(b, pose.last().pose(), s, time, camera);
      BufferUploader.drawWithShader(b.end());
      var buffers = mc.renderBuffers().bufferSource();
      // Two native passes, each batched across the whole formation: red body then luminous edge.
      for (int pass = 0; pass < 2; pass++)
        for (var s : SWORDS) {
          double age = time - s.born();
          if (age < 0 || age > s.lifetime() || s.start().distanceToSqr(camera) > maxDistance)
            continue;
          Vec3 end = s.end();
          var entity = mc.level.getEntity(s.targetId());
          if (entity != null && entity.isAlive()) end = entity.getBoundingBox().getCenter();
          double progress = AkatsukiRainDirector.progress(age, s.reveal(), s.flight());
          Vec3 point = s.start().lerp(end, s.rain() ? progress : progress * progress);
          if (point.distanceToSqr(camera) < 2.25)
            continue; // Avoid a sword crossing the first-person near plane.
          Vec3 direction = end.subtract(s.start()).normalize();
          pose.pushPose();
          try {
            pose.translate(point.x, point.y, point.z);
            float yaw = (float) Math.toDegrees(Math.atan2(direction.z, direction.x));
            float pitch = (float) Math.toDegrees(Math.asin(Mth.clamp(direction.y, -1, 1)));
            pose.mulPose(Axis.YP.rotationDegrees(-yaw));
            pose.mulPose(Axis.ZP.rotationDegrees(pitch));
            pose.scale(s.size(), s.size(), s.size());
            pose.mulPose(Axis.YP.rotationDegrees(90));
            AkatsukiNativeVisualBridge.sword(
                pose,
                buffers,
                (float) Math.min(1, (s.lifetime() - age) / (s.rain() ? 1.5 : 4)),
                pass == 0);
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

  private static void rainTrace(BufferBuilder b, Matrix4f m, Sword s, double time, Vec3 camera) {
    double age = time - s.born();
    if (age < s.reveal() || age > s.lifetime()) return;
    double progress = AkatsukiRainDirector.progress(age, s.reveal(), s.flight());
    if (progress < 1) {
      Vec3 tip = s.start().lerp(s.end(), progress);
      if (tip.distanceToSqr(camera) < 2.25) return;
      Vec3 tail =
          s.start().lerp(s.end(), AkatsukiRainDirector.progress(age - .65, s.reveal(), s.flight()));
      Vec3 side = tip.subtract(tail).cross(camera.subtract(tip)).normalize();
      feather(b, m, tail, tip, side, .002, .025, BLOOD, .65f);
      strip(b, m, tail, tip, side, .001, .005, LIGHT, .65f);
    } else {
      float alpha = (float) Math.max(0, 1 - (age - s.reveal() - s.flight()) / 1.5);
      Vec3 point = s.end().add(0, -.30, 0);
      Vec3 diagonal = new Vec3(.23, 0, .17);
      feather(
          b,
          m,
          point.subtract(diagonal),
          point.add(diagonal),
          new Vec3(-.17, 0, .23).normalize(),
          .025,
          .002,
          BLOOD,
          alpha);
      strip(
          b,
          m,
          point.subtract(diagonal.scale(.6)),
          point.add(diagonal.scale(.6)),
          new Vec3(-.17, 0, .23).normalize(),
          .008,
          .001,
          LIGHT,
          alpha);
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
    if (age < 0 || age > 5.5) return;
    double converge = c.collapseTo() == null ? 0 : Math.min(1, age / 5.5);
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
    Vec3 center =
        location
            .add(side.scale((random.nextDouble() - .5) * 1.8))
            .add(along.scale((random.nextDouble() - .5) * 1.2));
    float fade = (float) Math.max(0, 1 - Math.max(0, age - 1.5) / 4);
    double width = age < .7 ? .004 : c.width() * Math.exp(-Math.max(0, age - 1.5) * .8);
    // Every segment tapers to a sharp tip, with jagged lips and a near-black interior.
    for (int i = 0; i < 8; i++) {
      double t0 = i / 8d, t1 = (i + 1) / 8d;
      double w0 = Math.sin(t0 * Math.PI) * width * (.85 + random.nextDouble() * .25);
      double w1 = Math.sin(t1 * Math.PI) * width * (.85 + random.nextDouble() * .25);
      Vec3 a = center.add(along.scale((t0 - .5) * length));
      Vec3 z = center.add(along.scale((t1 - .5) * length));
      feather(b, m, a, z, side, w0, w1, BLOOD, fade * .8f);
      strip(b, m, a.add(normal.scale(.002)), z.add(normal.scale(.002)), side, w0, w1, DARK, fade);
      if (age < 2)
        strip(
            b,
            m,
            a.add(side.scale(w0)).add(normal.scale(.004)),
            z.add(side.scale(w1)).add(normal.scale(.004)),
            side,
            Math.max(.001, w0 * .18),
            Math.max(.001, w1 * .18),
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
                DOMAINS.values().forEach(d -> d.rain.cancel());
                AkatsukiNativeVisualBridge.reload();
              });
    }
  }

  private AkatsukiDomainVisuals() {}
}
