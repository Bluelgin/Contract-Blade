package com.maidweapon.forge.network;

import com.maidweapon.forge.entity.SoulAnchorEntity;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

/** Independent S2C channel; the existing rain protocol and stored ability IDs remain unchanged. */
public final class SoulBurstNetwork {
  private static final SimpleChannel CHANNEL =
      NetworkRegistry.newSimpleChannel(
          ResourceLocation.parse("maid_weapon:soul_burst"), () -> "2", "2"::equals, "2"::equals);

  public record Cue(Vec3 core, Vec3 target, int stage, int stages, boolean success, long seed) {}

  public static void register() {
    CHANNEL.registerMessage(
        0,
        Cue.class,
        (p, b) -> {
          b.writeDouble(p.core.x);
          b.writeDouble(p.core.y);
          b.writeDouble(p.core.z);
          b.writeDouble(p.target.x);
          b.writeDouble(p.target.y);
          b.writeDouble(p.target.z);
          b.writeByte(p.stage);
          b.writeByte(p.stages);
          b.writeBoolean(p.success);
          b.writeLong(p.seed);
        },
        b ->
            new Cue(
                new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
                new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
                b.readUnsignedByte(),
                b.readUnsignedByte(),
                b.readBoolean(),
                b.readLong()),
        (p, c) -> {
          c.get()
              .enqueueWork(
                  () ->
                      net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                          net.minecraftforge.api.distmarker.Dist.CLIENT,
                          () -> () -> com.maidweapon.forge.client.SoulAnchorRenderer.burst(p)));
          c.get().setPacketHandled(true);
        },
        Optional.of(NetworkDirection.PLAY_TO_CLIENT));
  }

  public static void send(
      ServerPlayer p,
      SoulAnchorEntity core,
      LivingEntity target,
      int stage,
      int stages,
      boolean success) {
    var cue =
        new Cue(
            core.getBoundingBox().getCenter(),
            target.getBoundingBox().getCenter(),
            stage,
            stages,
            success,
            core.getUUID().getLeastSignificantBits() + stage * 73856093L);
    CHANNEL.send(
        PacketDistributor.NEAR.with(
            () ->
                new PacketDistributor.TargetPoint(
                    core.getX(), core.getY(), core.getZ(), 64, p.level().dimension())),
        cue);
    // Distant target observers also see the attack; overlapping ranges receive only one packet.
    for (var viewer : p.serverLevel().players())
      if (viewer.distanceToSqr(core) > 4096 && viewer.distanceToSqr(target) < 4096)
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), cue);
  }

  private SoulBurstNetwork() {}
}
