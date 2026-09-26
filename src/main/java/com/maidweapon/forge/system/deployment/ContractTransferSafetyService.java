package com.maidweapon.forge.system.deployment;

import com.maidweapon.forge.compat.TouhouLittleMaidHelper;
import com.maidweapon.forge.compat.TripleMagicCompat;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.item.MaidWeaponItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;

/**
 * Prevents a manifested maid from becoming the storage owner of the physical
 * contract that represents that same maid.
 *
 * <p>Inventory rescue is intentionally separate from deployment timing. It is
 * a transfer invariant and must remain valid no matter how the maid was
 * manifested.</p>
 */
public final class ContractTransferSafetyService {
    public static boolean rescueSelfStoredContract(Player player) {
        Entity menuMaid = openedMaid(player.containerMenu);
        if (!TouhouLittleMaidHelper.isOwnedMaid(menuMaid, player)) return false;

        String maidId = menuMaid.getStringUUID();
        String entityBinding = menuMaid.getPersistentData().getString(
                TouhouLittleMaidHelper.TAG_ENTITY_BINDING_ID);

        for (Slot slot : player.containerMenu.slots) {
            if (slot.container == player.getInventory()) continue;
            ItemStack stack = slot.getItem();
            if (TripleMagicCompat.isPhantom(stack)) continue;
            if (!MaidInfusion.isInfused(stack)
                    || !MaidWeaponItem.isOwner(stack, player)) continue;

            String weaponBinding = MaidWeaponItem.getBindingId(stack);
            boolean exactBinding = !entityBinding.isEmpty()
                    && entityBinding.equals(weaponBinding);
            boolean legacyBinding = maidId.equals(MaidWeaponItem.getBoundMaidUUID(stack));
            if (!exactBinding && !legacyBinding) continue;

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

    private ContractTransferSafetyService() {}
}
