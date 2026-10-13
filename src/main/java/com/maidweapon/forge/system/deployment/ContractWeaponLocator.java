package com.maidweapon.forge.system.deployment;

import com.maidweapon.forge.system.contract.ContractCarrierData;

import com.maidweapon.forge.compat.TouhouLittleMaidHelper;
import com.maidweapon.forge.item.MaidInfusion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/** Resolves the concrete contract stack and its currently manifested maid. */
public final class ContractWeaponLocator {
    /** Short-lived index; does not decode maid payloads or outlive the current operation. */
    public static java.util.Map<String, ItemStack> indexByBinding(Player player) {
        java.util.Map<String, ItemStack> result = new java.util.HashMap<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++)
            index(result, player.getInventory().getItem(i));
        index(result, player.getOffhandItem());
        index(result, player.containerMenu.getCarried());
        for (net.minecraft.world.inventory.Slot slot : player.containerMenu.slots)
            index(result, slot.getItem());
        return result;
    }

    /** Cross-dimension UUID check, including non-maid entities and other owners. */
    public static Entity findEntity(Player player, String maidId) {
        if (player == null || player.getServer() == null || maidId == null) return null;
        try {
            UUID uuid = UUID.fromString(maidId);
            for (ServerLevel level : player.getServer().getAllLevels()) {
                Entity entity = level.getEntity(uuid);
                if (entity != null && !entity.isRemoved()) return entity;
            }
        } catch (IllegalArgumentException ignored) { }
        return null;
    }
    /** A successful spawn remains world-present across unload/restart until capture or actual removal. */
    public static boolean mayManifest(Player player, String maidId) {
        if (player == null || player.getServer() == null || maidId == null || findEntity(player, maidId) != null) return false;
        try { UUID.fromString(maidId); } catch (IllegalArgumentException invalid) { return false; }
        return !com.maidweapon.forge.system.contract.ContractRecoveryJournal.get(player.getServer())
                .isMaidPresent(maidId);
    }

    private static void index(java.util.Map<String, ItemStack> result, ItemStack stack) {
        // Consumed stacks can retain NBT until their inventory slot is cleared.
        if (stack.isEmpty()) return;
        String binding = ContractCarrierData.getBindingId(stack);
        if (binding != null && !binding.isEmpty()) result.putIfAbsent(binding, stack);
    }

    public static ItemStack findBoundWeaponByBinding(Player player, String bindingId) {
        if (player == null || bindingId == null || bindingId.isEmpty()) return ItemStack.EMPTY;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (eligible(player, stack) && bindingId.equals(ContractCarrierData.getBindingId(stack))) return stack;
        }

        ItemStack offhand = player.getOffhandItem();
        if (eligible(player, offhand) && bindingId.equals(ContractCarrierData.getBindingId(offhand))) return offhand;

        ItemStack carried = player.containerMenu.getCarried();
        if (eligible(player, carried) && bindingId.equals(ContractCarrierData.getBindingId(carried))) return carried;

        for (net.minecraft.world.inventory.Slot slot : player.containerMenu.slots) {
            ItemStack stack = slot.getItem();
            if (eligible(player, stack) && bindingId.equals(ContractCarrierData.getBindingId(stack))) return stack;
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack findBoundWeapon(Player player, String maidId) {
        if (maidId == null || player == null) return ItemStack.EMPTY;
        Entity deployed = findManifestedMaid(player, maidId);
        String deployedBinding = deployed == null ? ""
                : deployed.getPersistentData().getString(
                        TouhouLittleMaidHelper.TAG_ENTITY_BINDING_ID);
        ItemStack uuidCandidate = ItemStack.EMPTY;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!eligible(player, stack)) continue;
            if (!deployedBinding.isEmpty()
                    && deployedBinding.equals(ContractCarrierData.getBindingId(stack))) return stack;
            if (maidId.equals(ContractCarrierData.getBoundMaidUUID(stack))) {
                uuidCandidate = stack;
            }
        }

        ItemStack offhand = player.getOffhandItem();
        if (eligible(player, offhand) && !deployedBinding.isEmpty()
                && deployedBinding.equals(ContractCarrierData.getBindingId(offhand))) return offhand;
        if (eligible(player, offhand) && maidId.equals(ContractCarrierData.getBoundMaidUUID(offhand)) && offhand != uuidCandidate) {
            uuidCandidate = offhand;
        }

        ItemStack carried = player.containerMenu.getCarried();
        if (eligible(player, carried) && !deployedBinding.isEmpty()
                && deployedBinding.equals(ContractCarrierData.getBindingId(carried))) return carried;
        if (eligible(player, carried) && maidId.equals(ContractCarrierData.getBoundMaidUUID(carried)) && carried != uuidCandidate) {
            uuidCandidate = carried;
        }

        for (net.minecraft.world.inventory.Slot slot : player.containerMenu.slots) {
            ItemStack stack = slot.getItem();
            if (slot.container instanceof net.minecraft.world.inventory.ResultContainer || !eligible(player, stack)) continue;
            if (!deployedBinding.isEmpty()
                    && deployedBinding.equals(ContractCarrierData.getBindingId(stack))) return stack;
            if (maidId.equals(ContractCarrierData.getBoundMaidUUID(stack)) && stack != uuidCandidate) {
                uuidCandidate = stack;
            }
        }
        return uuidCandidate;
    }

    public static Entity findManifestedMaid(Player player, String maidId) {
        if (player == null || maidId == null) return null;
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
        if (!MaidInfusion.isInfused(weapon) || !ContractCarrierData.isOwner(weapon, player)) return false;
        String maidId = ContractCarrierData.getBoundMaidUUID(weapon);
        return maidId != null && !maidId.isEmpty()
                && findManifestedMaid(player, maidId) != null;
    }

    private static boolean eligible(Player owner, ItemStack carrier) {
        return !carrier.isEmpty() && ContractCarrierData.isOwner(carrier, owner)
                && !ContractCarrierData.isContractSuperseded(carrier)
                && !ContractTransferSafetyService.isProjectionPhantom(carrier);
    }
    private ContractWeaponLocator() {}
}
