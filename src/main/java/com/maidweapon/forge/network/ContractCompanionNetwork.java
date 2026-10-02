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
    public static void register() {
        CHANNEL.registerMessage(0, Call.class, (packet, buffer) -> { }, ContractCompanionNetwork::decode,
                ContractCompanionNetwork::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
    private static Call decode(FriendlyByteBuf buffer) { return new Call(); }
    private static void handle(Call packet, Supplier<NetworkEvent.Context> context) {
        var ctx = context.get();
        ctx.enqueueWork(() -> {
            if (ctx.getSender() != null) ContractCompanionService.toggle(ctx.getSender());
        });
        ctx.setPacketHandled(true);
    }
    private ContractCompanionNetwork() { }
}
