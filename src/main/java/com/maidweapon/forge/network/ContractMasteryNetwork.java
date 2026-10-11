package com.maidweapon.forge.network;

import com.maidweapon.common.data.ContractMasteryData;
import com.maidweapon.forge.system.mastery.ContractMasteryCodec;
import com.maidweapon.forge.system.mastery.ContractMasteryService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.Optional;

/** Owner-only read-only snapshots. No serverbound mastery message is registered. */
public final class ContractMasteryNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("maid_weapon:mastery"), () -> "1", "1"::equals, "1"::equals);
    private record Snapshot(ContractMasteryData data) { }
    public static void register() {
        CHANNEL.registerMessage(0, Snapshot.class, (packet, buffer) -> buffer.writeNbt(ContractMasteryCodec.write(packet.data())),
                buffer -> new Snapshot(ContractMasteryCodec.read(java.util.Objects.requireNonNull(buffer.readNbt()))), (packet, context) -> {
                    var ctx = context.get();
                    ctx.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                            () -> () -> ClientView.apply(packet.data())));
                    ctx.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void send(ServerPlayer player, ContractMasteryData data) {
        if (player.connection == null || player instanceof net.minecraftforge.common.util.FakePlayer) return; // Isolated FakePlayer fixtures have no network connection.
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Snapshot(data));
    }
    /** Separate class keeps LocalPlayer out of dedicated-server method verification. */
    private static final class ClientView {
        private static void apply(ContractMasteryData data) {
            ContractMasteryService.applyClientSnapshot(net.minecraft.client.Minecraft.getInstance().player, data);
        }
    }
    private ContractMasteryNetwork() { }
}
