package com.maidweapon.forge.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.*;
import java.util.Optional;

/** Small owner-only balance snapshots. The client never sends resonance authority to the server. */
public final class ResonanceNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("maid_weapon:resonance_pool"), () -> "1", "1"::equals, "1"::equals);
    private record Snapshot(UUID maid, int current, int maximum) { }
    private static final Map<UUID, Map<UUID, String>> SENT = new HashMap<>();

    public static void register() {
        CHANNEL.registerMessage(0, Snapshot.class,
                (message, buffer) -> {
                    buffer.writeUUID(message.maid());
                    buffer.writeVarInt(message.current());
                    buffer.writeVarInt(message.maximum());
                },
                buffer -> new Snapshot(buffer.readUUID(), buffer.readVarInt(), buffer.readVarInt()),
                (message, supplier) -> {
                    var context = supplier.get();
                    context.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                            net.minecraftforge.api.distmarker.Dist.CLIENT,
                            () -> () -> ClientView.receive(message)));
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void sendIfChanged(net.minecraft.world.entity.player.Player player, String maidId,
                                     int current, int maximum) {
        if (!(player instanceof ServerPlayer server) || server.connection == null
                || player instanceof net.minecraftforge.common.util.FakePlayer || current < 0) return;
        UUID maid;
        try { maid = UUID.fromString(maidId); } catch (IllegalArgumentException malformed) { return; }
        var sent = SENT.computeIfAbsent(server.getUUID(), id -> new HashMap<>());
        String value = current + ":" + maximum;
        if (value.equals(sent.get(maid))) return;
        sent.put(maid, value);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> server), new Snapshot(maid, current, maximum));
    }
    public static void clear(UUID player) { SENT.remove(player); }
    public static int clientCurrent(net.minecraft.world.entity.player.Player viewer, String maid) {
        return ClientView.current(viewer, maid);
    }
    public static int clientMaximum(net.minecraft.world.entity.player.Player viewer, String maid) {
        return ClientView.maximum(viewer, maid);
    }
    private static final class ClientView {
        private static net.minecraft.world.entity.player.Player activePlayer;
        private static final Map<UUID, Snapshot> snapshots = new HashMap<>();
        static void receive(Snapshot snapshot) {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (activePlayer != player) { activePlayer = player; snapshots.clear(); }
            if (player != null) snapshots.put(snapshot.maid(), snapshot);
        }
        static Snapshot get(net.minecraft.world.entity.player.Player viewer, String maid) {
            if (viewer == null || maid == null || viewer != net.minecraft.client.Minecraft.getInstance().player) return null;
            if (activePlayer != viewer) { activePlayer = viewer; snapshots.clear(); }
            try { return snapshots.get(UUID.fromString(maid)); }
            catch (IllegalArgumentException malformed) { return null; }
        }
        static int current(net.minecraft.world.entity.player.Player viewer, String maid) {
            Snapshot packet = get(viewer, maid);
            return packet == null ? -1 : packet.current();
        }
        static int maximum(net.minecraft.world.entity.player.Player viewer, String maid) {
            Snapshot packet = get(viewer, maid);
            return packet == null ? -1 : packet.maximum();
        }
    }
    private ResonanceNetwork() { }
}