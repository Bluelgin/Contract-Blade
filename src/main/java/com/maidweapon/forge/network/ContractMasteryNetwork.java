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
            new ResourceLocation("maid_weapon:mastery"), () -> "2", "2"::equals, "2"::equals);
    private record Snapshot(ContractMasteryData data, net.minecraft.nbt.CompoundTag tooltip) { }
    private static final java.util.Map<java.util.UUID, net.minecraft.nbt.CompoundTag> LAST_TOOLTIP =
            new java.util.HashMap<>();
    public static void register() {
        CHANNEL.registerMessage(0, Snapshot.class, (packet, buffer) -> {
                    buffer.writeNbt(ContractMasteryCodec.write(packet.data()));
                    buffer.writeNbt(packet.tooltip());
                },
                buffer -> new Snapshot(
                    ContractMasteryCodec.read(java.util.Objects.requireNonNull(buffer.readNbt())),
                    java.util.Objects.requireNonNull(buffer.readNbt())), (packet, context) -> {
                    var ctx = context.get();
                    ctx.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                            () -> () -> ClientView.apply(packet.data(), packet.tooltip())));
                    ctx.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void send(ServerPlayer player, ContractMasteryData data) {
        if (player.connection == null || player instanceof net.minecraftforge.common.util.FakePlayer) return; // Isolated FakePlayer fixtures have no network connection.
        var tooltip = com.maidweapon.forge.system.mastery.ContractTooltipSnapshot.server(player, data.getLevel());
        LAST_TOOLTIP.put(player.getUUID(), tooltip.copy());
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Snapshot(data, tooltip));
    }

    /** Re-synchronize on equip, mastery and datapack changes, without spamming every tick. */
    public static void refreshIfChanged(ServerPlayer player) {
        if (player.connection == null || player instanceof net.minecraftforge.common.util.FakePlayer) return;
        var data = ContractMasteryService.getProgress(player);
        var tooltip = com.maidweapon.forge.system.mastery.ContractTooltipSnapshot.server(player, data.getLevel());
        if (!tooltip.equals(LAST_TOOLTIP.get(player.getUUID()))) send(player, data);
    }

    public static void forget(ServerPlayer player) {
        LAST_TOOLTIP.remove(player.getUUID());
    }

    /** Separate class keeps LocalPlayer out of dedicated-server method verification. */
    private static final class ClientView {
        private static void apply(ContractMasteryData data, net.minecraft.nbt.CompoundTag tooltip) {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            ContractMasteryService.applyClientSnapshot(player, data);
            com.maidweapon.forge.system.mastery.ContractTooltipSnapshot.applyClient(player, tooltip);
        }
    }
    private ContractMasteryNetwork() { }
}
