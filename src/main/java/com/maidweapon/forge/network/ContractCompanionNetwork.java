package com.maidweapon.forge.network;

import com.maidweapon.common.MaidWeaponConstants;
import com.maidweapon.forge.system.deployment.ContractCompanionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

/** Client sends intent only; the server chooses and validates the held contract. */
public final class ContractCompanionNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MaidWeaponConstants.MOD_ID, "companion"), () -> VERSION, VERSION::equals, VERSION::equals);
    public record Call() { }
    private static final java.util.Map<net.minecraft.server.level.ServerPlayer, Long> LAST_CALL =
            new java.util.WeakHashMap<>();

    private static synchronized boolean accept(net.minecraft.server.level.ServerPlayer player) {
        long now = System.nanoTime();
        Long previous = LAST_CALL.get(player);
        if (previous != null && now - previous < 100_000_000L) return false;
        LAST_CALL.put(player, now);
        return true;
    }
    public static void register() {
        CHANNEL.registerMessage(0, Call.class, (packet, buffer) -> { }, ContractCompanionNetwork::decode,
                ContractCompanionNetwork::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
    private static Call decode(FriendlyByteBuf buffer) { return new Call(); }
    private static void handle(Call packet, Supplier<NetworkEvent.Context> context) {
        var ctx = context.get();
        // Gate before enqueueing work, not after a flood has reached the server thread.
        if (ctx.getSender() == null || !accept(ctx.getSender())) {
            ctx.setPacketHandled(true);
            return;
        }
        ctx.enqueueWork(() -> {
            if (ctx.getSender() != null) ContractCompanionService.toggle(ctx.getSender());
        });
        ctx.setPacketHandled(true);
    }
    private ContractCompanionNetwork() { }
}
