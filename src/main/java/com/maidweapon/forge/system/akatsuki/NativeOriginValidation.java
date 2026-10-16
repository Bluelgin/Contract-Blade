package com.maidweapon.forge.system.akatsuki;

import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class NativeOriginValidation {
  @SubscribeEvent
  public static void started(ServerStartedEvent e) {
    if (!Boolean.getBoolean("contractblade.origin.validation")) return;
    try {
      SoulProvenanceValidation.nativeInvocationProof(e.getServer());
    } catch (Throwable failure) {
      com.mojang.logging.LogUtils.getLogger().error("SOUL_NATIVE_PLAYER_INVOCATION_FAIL", failure);
    } finally {
      e.getServer().halt(false);
    }
  }

  private NativeOriginValidation() {}
}
