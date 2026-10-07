package com.maidweapon.forge.network;

import com.maidweapon.forge.system.fox.challenge.BlackFoxCombatant;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.Optional;

/** Server-to-challenger feedback only. It carries no damage, scoring or client-authorized combat input. */
public final class BlackFoxFeedbackNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.parse("maid_weapon:fox_feedback"), () -> "4", "4"::equals, "4"::equals);
    private record Contact(int bossId, int strength) { }
    private record Music(int bossId, java.util.UUID identity, boolean active) { }
    private record Bar(java.util.UUID id, int count, int style, boolean active) { }
    public static void register() {
        CHANNEL.registerMessage(0, Contact.class, (packet, buffer) -> {
            buffer.writeVarInt(packet.bossId()); buffer.writeByte(packet.strength());
        }, buffer -> new Contact(buffer.readVarInt(), buffer.readUnsignedByte()), (packet, context) -> {
            var ctx = context.get();
            ctx.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                    net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> {
                        com.maidweapon.forge.client.BlackFoxCameraShake.contact(packet.bossId(), packet.strength());
                        com.maidweapon.forge.client.BlackFoxMusic.contact(packet.bossId(), packet.strength());
                    }));
            ctx.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(2, Bar.class, (packet, buffer) -> {
            buffer.writeUUID(packet.id()); buffer.writeByte(packet.count()); buffer.writeByte(packet.style()); buffer.writeBoolean(packet.active());
        }, buffer -> new Bar(buffer.readUUID(), buffer.readUnsignedByte(), buffer.readUnsignedByte(), buffer.readBoolean()), (packet, context) -> {
            var ctx = context.get();
            ctx.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.maidweapon.forge.client.BlackFoxBossBar.state(packet.id(), packet.count(), packet.style(), packet.active())));
            ctx.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, Music.class, (packet, buffer) -> {
            buffer.writeVarInt(packet.bossId()); buffer.writeUUID(packet.identity()); buffer.writeBoolean(packet.active());
        }, buffer -> new Music(buffer.readVarInt(), buffer.readUUID(), buffer.readBoolean()), (packet, context) -> {
            var ctx = context.get();
            ctx.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.maidweapon.forge.client.BlackFoxMusic.session(packet.bossId(), packet.identity(), packet.active())));
            ctx.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void contact(BlackFoxCombatant boss, ServerPlayer player, int strength) {
        if (!boss.challenger().equals(player.getUUID())) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Contact(boss.body().getId(), strength));
    }
    public static void music(BlackFoxCombatant boss, ServerPlayer player, boolean active) {
        if (player != null && boss.challenger().equals(player.getUUID()))
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Music(boss.body().getId(), boss.body().getUUID(), active));
    }
    public static void bar(BlackFoxCombatant boss, ServerPlayer player, java.util.UUID id, int count, int style, boolean active) {
        if (player != null && boss.challenger().equals(player.getUUID()))
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Bar(id, count, style, active));
    }
    private BlackFoxFeedbackNetwork() { }
}
