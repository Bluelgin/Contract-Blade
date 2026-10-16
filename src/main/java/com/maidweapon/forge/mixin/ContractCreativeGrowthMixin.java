package com.maidweapon.forge.mixin;

import com.maidweapon.forge.system.mastery.ContractMasteryCreativeGuard;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Both dev and packaged names; runs only on the server-thread replay after vanilla packet scheduling. */
@Mixin(value = ServerboundSetCreativeModeSlotPacket.class, remap = false)
public abstract class ContractCreativeGrowthMixin {
    @Inject(method = {"handle(Lnet/minecraft/network/protocol/game/ServerGamePacketListener;)V",
            "m_5797_(Lnet/minecraft/network/protocol/game/ServerGamePacketListener;)V"}, at = @At("HEAD"), require = 1)
    private void contractblade$sanitize(ServerGamePacketListener listener, CallbackInfo ci) {
        if (listener instanceof ServerGamePacketListenerImpl connection && connection.player.getServer().isSameThread()) {
            var packet = (ServerboundSetCreativeModeSlotPacket)(Object)this;
            ContractMasteryCreativeGuard.sanitize(connection.player, packet.getItem());
        }
    }
}
