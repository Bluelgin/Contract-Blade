package com.maidweapon.forge.client;

import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Capture real rendered frames in the opt-in disposable integrated-server fixture. */
@Mod.EventBusSubscriber(modid = "maid_weapon", value = Dist.CLIENT)
public final class AkatsukiVisualCapture {
  private static final Set<String> TAKEN = new HashSet<>();
  private static long start = -1;

  @SubscribeEvent
  public static void frame(RenderLevelStageEvent e) {
    if (!Boolean.getBoolean("contractblade.akatsukiVisual.validation")
        || e.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
    var mc = Minecraft.getInstance();
    if (mc.level == null
        || mc.player == null
        || mc.getSingleplayerServer() == null
        || !mc.getSingleplayerServer()
            .getWorldData()
            .getLevelName()
            .equals("Akatsuki Visual Validation")) return;
    if (start < 0) start = mc.level.getGameTime();
    long age = mc.level.getGameTime() - start;
    if (age >= 380 && age < 450) {
      com.maidweapon.common.AkatsukiDomainConfig.QUALITY.set(2);
      mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    } else if (age >= 450 && age < 510) {
      com.maidweapon.common.AkatsukiDomainConfig.QUALITY.set(0);
    } else if (age >= 510) {
      com.maidweapon.common.AkatsukiDomainConfig.QUALITY.set(1);
      mc.options.setCameraType(CameraType.FIRST_PERSON);
    }
    String name =
        age >= 535
            ? "08-native-execution-coexist"
            : age >= 460
                ? "07-low"
                : age >= 400
                    ? "06-high-third-person"
                    : age >= 330
                        ? "05-reignited"
                        : age >= 301
                            ? "04-rescue"
                            : age >= 260
                                ? "03-rampage-rain"
                                : age >= 230 ? "02-rampage" : age >= 100 ? "01-normal" : null;
    if (name == null || !TAKEN.add(name)) return;
    Screenshot.grab(
        mc.gameDirectory,
        "akatsuki-" + name + ".png",
        mc.getMainRenderTarget(),
        message ->
            LogUtils.getLogger().info("AKATSUKI_VISUAL_CAPTURE {} {}", name, message.getString()));
  }

  private AkatsukiVisualCapture() {}
}
