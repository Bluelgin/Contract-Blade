package com.maidweapon.forge.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.Optional;

/** Session transitions only. Balance snapshots remain owned by ResonanceNetwork. */
public final class OathNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("maid_weapon", "oath"), () -> "1", "1"::equals, "1"::equals);
    private record State(String maid, boolean active) { }
    public static void register() {
        CHANNEL.registerMessage(0, State.class,
                (m, b) -> { b.writeUtf(m.maid(), 36); b.writeBoolean(m.active()); },
                b -> new State(b.readUtf(36), b.readBoolean()),
                (m, supplier) -> {
                    var context = supplier.get();
                    context.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                            net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> ClientView.receive(m)));
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void send(Player player, String maid, boolean active) {
        if (player instanceof ServerPlayer server && server.connection != null
                && !(player instanceof net.minecraftforge.common.util.FakePlayer))
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> server), new State(maid, active));
    }
    public static String clientMaid(Player player) { return ClientView.maid(player); }
    public static boolean clientActive(Player player) { return ClientView.valid(player) && ClientView.active; }
    public static float clientAsh(Player player) {
        return ClientView.valid(player) && !ClientView.active
                ? Math.max(0, 1 - (player.level().getGameTime() - ClientView.ended) / 30f) : 0;
    }
    private static final class ClientView {
        private static Player viewer;
        private static String maid;
        private static boolean active;
        private static long ended;
        static boolean valid(Player p) {
            if (p == null || viewer != p) { viewer = p; maid = null; active = false; ended = Long.MIN_VALUE / 2; }
            return p != null && maid != null;
        }
        static void receive(State m) {
            var p = net.minecraft.client.Minecraft.getInstance().player;
            valid(p); if (p == null) return;
            maid = m.maid(); active = m.active();
            if (!active) ended = p.level().getGameTime();
        }
        static String maid(Player p) { return valid(p) && (active || p.level().getGameTime() - ended < 30) ? maid : null; }
    }
    private OathNetwork() { }
}
