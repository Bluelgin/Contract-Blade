package com.maidweapon.forge.client;

import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Capture real rendered frames in the opt-in disposable integrated-server fixture. */
@Mod.EventBusSubscriber(modid = "maid_weapon", value = Dist.CLIENT)
public final class AkatsukiVisualCapture {
  private static final Set<String> TAKEN = new HashSet<>();
  private static long start = -1;
  private static int loadingTicks;

  @SubscribeEvent
  public static void loading(net.minecraftforge.event.TickEvent.ClientTickEvent e) {
    if (!Boolean.getBoolean("contractblade.akatsukiVisual.validation")
        || e.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
    var mc = Minecraft.getInstance();
    if (mc.level != null || ++loadingTicks % 200 != 0) return;
    // Only the named, generated fixture save; never accept arbitrary confirmation screens.
    if (mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen
        && mc.screen.getTitle().getContents()
            instanceof net.minecraft.network.chat.contents.TranslatableContents title
        && title.getKey().equals("selectWorld.backupQuestion.experimental")
        && new java.io.File(mc.gameDirectory, "saves/akatsuki-visual-validation").isDirectory()) {
      for (var child : mc.screen.children()) {
        if (child instanceof net.minecraft.client.gui.components.Button button
            && button
                .getMessage()
                .getString()
                .equals(
                    net.minecraft.network.chat.Component.translatable("gui.proceed").getString())) {
          button.onPress();
          LogUtils.getLogger().info("AKATSUKI_VISUAL_DISPOSABLE_WORLD_PROCEED");
          return;
        }
      }
    }
    LogUtils.getLogger()
        .info(
            "AKATSUKI_VISUAL_LOADING screen={} title={}",
            mc.screen == null ? "none" : mc.screen.getClass().getName(),
            mc.screen == null ? "" : mc.screen.getTitle().getString());
    Screenshot.grab(
        mc.gameDirectory, "akatsuki-loading.png", mc.getMainRenderTarget(), message -> {});
  }

  @SubscribeEvent
  public static void frame(RenderGuiEvent.Post e) {
    if (!Boolean.getBoolean("contractblade.akatsukiVisual.validation")) return;
    var mc = Minecraft.getInstance();
    if (mc.level == null
        || mc.player == null
        || mc.getSingleplayerServer() == null
        || !mc.getSingleplayerServer()
            .getWorldData()
            .getLevelName()
            .equals("Akatsuki Visual Validation")) return;
    start =
        com.maidweapon.forge.system.akatsuki.AkatsukiVisualValidation.started(mc.player.getUUID());
    if (start < 0) return;
    if (TAKEN.isEmpty()) mc.gui.getChat().clearMessages(true);
    long age = mc.level.getGameTime() - start;
    if (Boolean.getBoolean("contractblade.akatsukiRain.validation")) {
      rainFrame(mc, age);
      return;
    }
    if (age >= 600) {
      LogUtils.getLogger().info("AKATSUKI_VISUAL_CLIENT_COMPLETE captures={}", TAKEN.size());
      mc.stop();
      return;
    }
    if (age >= 380 && age < 450) {
      if (com.maidweapon.common.AkatsukiDomainConfig.QUALITY.get() != 2)
        com.maidweapon.common.AkatsukiDomainConfig.QUALITY.set(2);
      mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    } else if (age >= 450 && age < 510) {
      if (com.maidweapon.common.AkatsukiDomainConfig.QUALITY.get() != 0)
        com.maidweapon.common.AkatsukiDomainConfig.QUALITY.set(0);
    } else if (age >= 510) {
      if (com.maidweapon.common.AkatsukiDomainConfig.QUALITY.get() != 1)
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
            LogUtils.getLogger()
                .info(
                    "AKATSUKI_VISUAL_CAPTURE {} fps={} {}",
                    name,
                    mc.getFps(),
                    message.getString()));
  }

  private static void rainFrame(Minecraft mc, long age) {
    if (age >= 840) {
      LogUtils.getLogger().info("AKATSUKI_RAIN_CLIENT_COMPLETE captures={}", TAKEN.size());
      mc.stop();
      return;
    }
    int quality = age >= 730 ? 0 : age >= 650 ? 2 : 1;
    com.maidweapon.common.AkatsukiDomainConfig.QUALITY.set(quality);
    mc.options.setCameraType(age >= 650 ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON);
    String name =
        age >= 765
            ? "rain-06-low"
            : age >= 665
                ? "rain-05-high-third-person"
                : age >= 515
                    ? "rain-04-rampage-moving"
                    : age >= 370
                        ? "rain-03-rampage"
                        : age >= 275
                            ? "rain-02-normal-moving"
                            : age >= 70 ? "rain-01-normal" : null;
    if (name == null || !TAKEN.add(name)) return;
    Screenshot.grab(
        mc.gameDirectory,
        "akatsuki-" + name + ".png",
        mc.getMainRenderTarget(),
        message ->
            LogUtils.getLogger()
                .info(
                    "AKATSUKI_RAIN_CAPTURE {} fps={} {}", name, mc.getFps(), message.getString()));
  }

  private AkatsukiVisualCapture() {}
}
