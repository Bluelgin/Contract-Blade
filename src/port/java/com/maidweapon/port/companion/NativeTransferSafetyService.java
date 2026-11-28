package com.maidweapon.port.companion;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import java.lang.reflect.Method;
/** Maintained self-storage rescue; authority resolved by the native ledger. */
public final class NativeTransferSafetyService {
    public static boolean rescueSelfStoredContract(Player player) {
        Entity menuMaid = openedMaid(player.containerMenu);
        if (!(menuMaid instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid) || !player.getUUID().equals(maid.getOwnerUUID())) return false;

        for (Slot slot : player.containerMenu.slots) {
            if (slot.container == player.getInventory()) continue;
            ItemStack stack = slot.getItem();
            if (com.maidweapon.port.storage.NativeChannelService.selectedLive(player.getServer(),player.getUUID(),stack)!=menuMaid) continue;

            ItemStack rescued = stack.copy();
            slot.set(ItemStack.EMPTY);
            slot.setChanged();
            returnToPlayer(player, rescued);
            player.containerMenu.broadcastChanges();
            player.displayClientMessage(
                    Component.translatable(
                            "maid_weapon.message.self_contract_inventory_blocked"),
                    true);
            return true;
        }
        return false;
    }

    private static Entity openedMaid(AbstractContainerMenu menu) {
        if (menu == null) return null;
        try {
            Method getter = menu.getClass().getMethod("getMaid");
            Object value = getter.invoke(menu);
            return value instanceof Entity entity ? entity : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static void returnToPlayer(Player player, ItemStack stack) {
        int selected = player.getInventory().selected;
        if (player.getInventory().getItem(selected).isEmpty()) {
            player.getInventory().setItem(selected, stack);
            return;
        }
        player.getInventory().add(stack);
        if (!stack.isEmpty()) player.drop(stack, false);
    }

    private NativeTransferSafetyService() {}
}
