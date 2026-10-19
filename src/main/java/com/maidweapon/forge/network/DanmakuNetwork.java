package com.maidweapon.forge.network;

import com.maidweapon.forge.system.danmaku.DanmakuCombatService;
import com.maidweapon.forge.system.danmaku.DanmakuForm;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.*;

/**
 * Client transmits selected spell ID only. The server resolves the equipped
 * contract, native bauble, mastery, target, resonance payment, performance cap and damage itself.
 */
public final class DanmakuNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("maid_weapon:danmaku"), () -> "1", "1"::equals, "1"::equals);
    public record Cast(int form) { }
    public record AirSwing() { }
    public record Eligibility(String binding, boolean enabled, int mastery) { }
    private static final Map<UUID, Eligibility> LAST = new HashMap<>();
    private static final Map<UUID, Long> LAST_INPUT = new HashMap<>();

    public static void register() {
        CHANNEL.registerMessage(0, Cast.class,
                (packet, buf) -> buf.writeVarInt(packet.form()),
                buf -> new Cast(buf.readVarInt()),
                (packet, context) -> {
                    var ctx = context.get();
                    ServerPlayer sender = ctx.getSender();
                    if (sender != null) {
                        ctx.enqueueWork(() -> {
                            // LAST_INPUT is shared with logout cleanup: main-thread ownership only.
                            long now = System.nanoTime();
                            Long last = LAST_INPUT.get(sender.getUUID());
                            if (last == null || now - last >= 50_000_000L) {
                                LAST_INPUT.put(sender.getUUID(), now);
                                DanmakuCombatService.cast(sender, DanmakuForm.safe(packet.form()));
                            }
                        });
                    }
                    ctx.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        // Un-targeted left clicks are client-only in vanilla; authority, item,
        // funding and one-per-tick physical budget are all rechecked server-side.
        CHANNEL.registerMessage(2, AirSwing.class,
                (packet, buf) -> { },
                buf -> new AirSwing(),
                (packet, context) -> {
                    var ctx = context.get();
                    ServerPlayer sender = ctx.getSender();
                    if (sender != null)
                        ctx.enqueueWork(() -> DanmakuCombatService.airSwing(sender));
                    ctx.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, Eligibility.class,
                (packet, buf) -> {
                    buf.writeUtf(packet.binding(), 48);
                    buf.writeBoolean(packet.enabled());
                    buf.writeVarInt(packet.mastery());
                },
                buf -> new Eligibility(buf.readUtf(48), buf.readBoolean(), buf.readVarInt()),
                (packet, context) -> {
                    var ctx = context.get();
                    ctx.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                            net.minecraftforge.api.distmarker.Dist.CLIENT,
                            () -> () -> ClientReceiver.receive(packet)));
                    ctx.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void airSwing() { CHANNEL.sendToServer(new AirSwing()); }
    public static void cast(DanmakuForm form) {
        if (form != null) CHANNEL.sendToServer(new Cast(form.ordinal()));
    }
    public static void sync(ServerPlayer player, String binding, boolean enabled, int mastery) {
        if (player.connection == null || player instanceof net.minecraftforge.common.util.FakePlayer) return;
        Eligibility state = new Eligibility(binding == null ? "" : binding, enabled, mastery);
        if (state.equals(LAST.get(player.getUUID()))) return;
        LAST.put(player.getUUID(), state);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), state);
    }
    public static void forget(UUID player) { LAST.remove(player); LAST_INPUT.remove(player); }
    public static void clearAll() { LAST.clear(); LAST_INPUT.clear(); }
    private static final class ClientReceiver {
        static void receive(Eligibility packet) {
            com.maidweapon.forge.client.DanmakuClientState.receive(
                    packet.binding(), packet.enabled(), packet.mastery());
        }
    }
    private DanmakuNetwork() { }
}
