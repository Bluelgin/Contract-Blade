package com.maidweapon.forge.network;

import com.maidweapon.forge.api.ability.ContractAbilityFeatureSet;
import com.maidweapon.forge.system.akatsuki.AkatsukiDomainGrowth;
import com.maidweapon.forge.system.akatsuki.AkatsukiDomainState.Phase;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

/** Bounded server-to-client snapshots/cues. No client packet can request damage or an opening. */
public final class AkatsukiDomainNetwork {
  public static final int STATE = 0,
      END = 1,
      EDGE = 2,
      RING = 4,
      LOCK = 5,
      COUNTER = 7,
      CHAIN = 8,
      RAIN = 10,
      FINAL = 11,
      EXECUTION = 12,
      COLLAPSE = 13,
      REIGNITE = 14;
  private static final SimpleChannel CHANNEL =
      NetworkRegistry.newSimpleChannel(
          ResourceLocation.parse("maid_weapon:akatsuki_domain"),
          () -> "2",
          "2"::equals,
          "2"::equals);

  public record Cue(
      UUID owner,
      int kind,
      Vec3 center,
      Vec3 target,
      float radius,
      int features,
      int ticks,
      int blades,
      Phase phase,
      int targetId) {}

  public static void register() {
    CHANNEL.registerMessage(
        0,
        Cue.class,
        (p, b) -> {
          b.writeUUID(p.owner());
          b.writeByte(p.kind());
          b.writeDouble(p.center().x);
          b.writeDouble(p.center().y);
          b.writeDouble(p.center().z);
          b.writeDouble(p.target().x);
          b.writeDouble(p.target().y);
          b.writeDouble(p.target().z);
          b.writeFloat(p.radius());
          b.writeVarInt(p.features());
          b.writeVarInt(p.ticks());
          b.writeVarInt(p.blades());
          b.writeEnum(p.phase());
          b.writeInt(p.targetId());
        },
        b ->
            new Cue(
                b.readUUID(),
                b.readUnsignedByte(),
                new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
                new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
                b.readFloat(),
                b.readVarInt(),
                b.readVarInt(),
                b.readVarInt(),
                b.readEnum(Phase.class),
                b.readInt()),
        (p, c) -> {
          var context = c.get();
          context.enqueueWork(
              () ->
                  net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                      net.minecraftforge.api.distmarker.Dist.CLIENT,
                      () -> () -> com.maidweapon.forge.client.AkatsukiDomainVisuals.accept(p)));
          context.setPacketHandled(true);
        },
        Optional.of(NetworkDirection.PLAY_TO_CLIENT));
  }

  public static void send(
      ServerPlayer player,
      int kind,
      Vec3 target,
      float radius,
      ContractAbilityFeatureSet features,
      int ticks,
      Phase phase,
      int targetId) {
    int mask = 0;
    for (int i = 0; i < AkatsukiDomainGrowth.ROUTE.size(); i++)
      if (AkatsukiDomainGrowth.has(features, AkatsukiDomainGrowth.ROUTE.get(i))) mask |= 1 << i;
    var cue =
        new Cue(
            player.getUUID(),
            kind,
            player.position(),
            target,
            radius,
            mask,
            Math.min(400, Math.max(0, ticks)),
            com.maidweapon.common.AkatsukiDomainConfig.RAIN_COUNT.get(),
            phase,
            targetId);
    CHANNEL.send(
        PacketDistributor.NEAR.with(
            () ->
                new PacketDistributor.TargetPoint(
                    player.getX(), player.getY(), player.getZ(), 64, player.level().dimension())),
        cue);
  }

  private AkatsukiDomainNetwork() {}
}
