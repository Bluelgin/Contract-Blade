package com.maidweapon.forge.system.akatsuki;

import com.maidweapon.forge.system.akatsuki.soul.*;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Opt-in real Forge server, real HP and optional real boss JAR comparisons. */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class SoulDamageValidation {
  private static LivingEntity reduce, cancel;
  private static boolean running;

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void reduction(LivingHurtEvent e) {
    if (running && e.getEntity() == reduce) e.setAmount(.25f);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void cancel(LivingAttackEvent e) {
    if (running && e.getEntity() == cancel) e.setCanceled(true);
  }

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void reductionFinal(LivingDamageEvent e) {
    if (running && e.getEntity() == reduce) e.setAmount(.1f);
  }

  @SubscribeEvent
  public static void started(ServerStartedEvent e) {
    if (!Boolean.getBoolean("contractblade.soul.validation")) return;
    running = true;
    try {
      run(e.getServer());
      LogUtils.getLogger().info("SOUL_SERVER_PASS");
    } catch (Throwable error) {
      LogUtils.getLogger().error("SOUL_SERVER_FAIL", error);
    } finally {
      running = false;
      reduce = null;
      cancel = null;
      e.getServer().halt(false);
    }
  }

  private static void run(MinecraftServer server) throws Exception {
    SoulProvenanceValidation.run(server);
  }

  private static void check(boolean ok, String detail) {
    if (!ok) throw new IllegalStateException(detail);
  }

  private SoulDamageValidation() {}
}
