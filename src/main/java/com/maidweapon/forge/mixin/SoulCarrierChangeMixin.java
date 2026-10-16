package com.maidweapon.forge.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.maidweapon.forge.system.akatsuki.soul.SoulDamageDebtService;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Native held-item operations invalidate receipts even when switching away and back in one tick.
 */
@Mixin(
    value = {
      ServerboundSetCarriedItemPacket.class,
      ServerboundPlayerActionPacket.class,
      ServerboundContainerClickPacket.class
    },
    remap = false)
public abstract class SoulCarrierChangeMixin {
  @WrapMethod(
      method = {
        "handle(Lnet/minecraft/network/protocol/game/ServerGamePacketListener;)V",
        "m_5797_(Lnet/minecraft/network/protocol/game/ServerGamePacketListener;)V"
      },
      remap = false)
  private void cb$held(ServerGamePacketListener listener, Operation<Void> original) {
    if (listener instanceof ServerGamePacketListenerImpl server
        && server.player.getServer().isSameThread()) {
      Object packet = this;
      boolean invalidate =
          packet instanceof ServerboundSetCarriedItemPacket held
              && held.getSlot() >= 0
              && held.getSlot() < 9
              && held.getSlot() != server.player.getInventory().selected;
      if (packet instanceof ServerboundPlayerActionPacket action)
        invalidate =
            switch (action.getAction()) {
              case SWAP_ITEM_WITH_OFFHAND, DROP_ITEM, DROP_ALL_ITEMS -> true;
              default -> false;
            };
      if (packet instanceof ServerboundContainerClickPacket click
          && click.getContainerId() == server.player.containerMenu.containerId) {
        int slot = click.getSlotNum();
        boolean heldSlot =
            slot >= 0
                && slot < server.player.containerMenu.slots.size()
                && !server.player.getMainHandItem().isEmpty()
                && server.player.containerMenu.getSlot(slot).getItem()
                    == server.player.getMainHandItem();
        invalidate |=
            heldSlot
                || click.getClickType() == net.minecraft.world.inventory.ClickType.SWAP
                    && click.getButtonNum() == server.player.getInventory().selected;
      }
      if (invalidate) SoulDamageDebtService.invalidate(server.player);
    }
    original.call(listener);
  }
}
