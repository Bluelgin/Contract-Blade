package com.maidweapon.forge.client;

import com.maidweapon.common.AkatsukiDomainConfig;
import com.maidweapon.forge.network.SoulBurstNetwork.Cue;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-only, bounded afterimage timeline. Stage packets are one-way cues;
 * rendering NEVER initiates strikes or changes server-authoritative HP.
 */
@Mod.EventBusSubscriber(modid = "maid_weapon", value = Dist.CLIENT)
public final class SoulEclipseVisuals {
  private record Frame(Cue cue, double started) {}
  private static final Deque<Frame> FRAMES = new ArrayDeque<>();
  private static Object world;

  public static void accept(Cue cue) {
    var level = Minecraft.getInstance().level;
    if (level == null || !cue.eclipse()) return;
    if (world != level) {
      FRAMES.clear();
      world = level;
    }
    if (cue.stages() < 3 || cue.stages() > 5 || cue.stage() < 0
        || cue.stage() >= cue.stages()
        || !Double.isFinite(cue.core().lengthSqr())
        || cue.core().lengthSqr() > 3e14) return;
    while (FRAMES.size() >= 64) FRAMES.removeFirst();
    FRAMES.addLast(new Frame(cue, level.getGameTime()));
  }

  @SubscribeEvent
  public static void render(RenderLevelStageEvent e) {
    if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
    var mc = Minecraft.getInstance();
    if (mc.level != world) {
      FRAMES.clear();
      world = mc.level;
    }
    if (mc.level == null || FRAMES.isEmpty()) return;
    double now = mc.level.getGameTime() + e.getPartialTick();
    FRAMES.removeIf(f -> now - f.started() >= 5 || now < f.started() - 1);
    if (FRAMES.isEmpty()) return;
    var eye = e.getCamera().getPosition();
    var buffer = mc.renderBuffers().bufferSource();
    var consumer = buffer.getBuffer(SoulRenderTypes.SHARDS);
    PoseStack pose = e.getPoseStack();
    boolean drew = false;
    for (var frame : FRAMES) {
      Cue cue = frame.cue();
      if (cue.core().distanceToSqr(eye) > 2304) continue;
      pose.pushPose();
      try {
        pose.translate(cue.core().x - eye.x, cue.core().y - eye.y + .65,
            cue.core().z - eye.z);
        pose.mulPose(e.getCamera().rotation());
        SoulEclipseGlyph.stage(consumer, pose.last().pose(), now,
            cue.stage(), cue.stages(), now - frame.started(),
            cue.success(), AkatsukiDomainConfig.QUALITY.get());
        drew = true;
      } finally {
        pose.popPose();
      }
    }
    if (drew) buffer.endBatch(SoulRenderTypes.SHARDS);
  }

  private SoulEclipseVisuals() {}
}
