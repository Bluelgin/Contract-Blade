package com.maidweapon.forge.system.akatsuki;

import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Opt-in real Forge HP matrix; legacy independent-damage expectations are superseded by proven
 * debt.
 */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class SoulDamageValidation {
  @SubscribeEvent
  public static void started(ServerStartedEvent e) {
    if (!Boolean.getBoolean("contractblade.soul.validation")) return;
    try {
      SoulProvenanceValidation.run(e.getServer());
      com.mojang.logging.LogUtils.getLogger().info("SOUL_SERVER_PASS");
    } catch (Throwable failure) {
      com.mojang.logging.LogUtils.getLogger().error("SOUL_SERVER_FAIL", failure);
    } finally {
      e.getServer().halt(false);
    }
  }

  private SoulDamageValidation() {}
}
