package com.maidweapon.forge.system.deployment;

import com.maidweapon.forge.compat.TouhouLittleMaidHelper;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.item.MaidWeaponItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/** Resolves the concrete contract stack and its currently manifested maid. */
public final class ContractWeaponLocator {
    public static ItemStack findBoundWeapon(Player player, String maidId) {
        Entity deployed = findManifestedMaid(player, maidId);
        String deployedBinding = deployed == null ? ""
                : deployed.getPersistentData().getString(
                        TouhouLittleMaidHelper.TAG_ENTITY_BINDING_ID);
        ItemStack uuidCandidate = ItemStack.EMPTY;
        int uuidMatches = 0;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!deployedBinding.isEmpty()
                    && deployedBinding.equals(MaidWeaponItem.getBindingId(stack))) return stack;
            if (maidId.equals(MaidWeaponItem.getBoundMaidUUID(stack))) {
                uuidCandidate = stack;
                uuidMatches++;
            }
        }

        ItemStack offhand = player.getOffhandItem();
        if (!deployedBinding.isEmpty()
                && deployedBinding.equals(MaidWeaponItem.getBindingId(offhand))) return offhand;
        if (maidId.equals(MaidWeaponItem.getBoundMaidUUID(offhand)) && offhand != uuidCandidate) {
            uuidCandidate = offhand;
            uuidMatches++;
        }

        ItemStack carried = player.containerMenu.getCarried();
        if (!deployedBinding.isEmpty()
                && deployedBinding.equals(MaidWeaponItem.getBindingId(carried))) return carried;
        if (maidId.equals(MaidWeaponItem.getBoundMaidUUID(carried)) && carried != uuidCandidate) {
            uuidCandidate = carried;
            uuidMatches++;
        }

        for (net.minecraft.world.inventory.Slot slot : player.containerMenu.slots) {
            ItemStack stack = slot.getItem();
            if (!deployedBinding.isEmpty()
                    && deployedBinding.equals(MaidWeaponItem.getBindingId(stack))) return stack;
            if (maidId.equals(MaidWeaponItem.getBoundMaidUUID(stack)) && stack != uuidCandidate) {
                uuidCandidate = stack;
                uuidMatches++;
            }
        }
        return uuidMatches == 1 ? uuidCandidate : ItemStack.EMPTY;
    }

    public static Entity findManifestedMaid(Player player, String maidId) {
        try {
            UUID uuid = UUID.fromString(maidId);
            if (player.getServer() == null) return null;
            for (ServerLevel level : player.getServer().getAllLevels()) {
                Entity entity = level.getEntity(uuid);
                if (TouhouLittleMaidHelper.isOwnedMaid(entity, player)) return entity;
            }
            return null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static boolean hasDeployedMaid(Player player, ItemStack weapon) {
        if (!MaidInfusion.isInfused(weapon) || !MaidWeaponItem.isOwner(weapon, player)) return false;
        String maidId = MaidWeaponItem.getBoundMaidUUID(weapon);
        return maidId != null && !maidId.isEmpty()
                && findManifestedMaid(player, maidId) != null;
    }

    private ContractWeaponLocator() {}
}
