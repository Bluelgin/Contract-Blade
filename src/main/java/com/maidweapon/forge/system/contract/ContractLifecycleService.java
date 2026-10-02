package com.maidweapon.forge.system.contract;

import com.maidweapon.forge.compat.TouhouLittleMaidCompat;
import com.maidweapon.forge.compat.TouhouLittleMaidHelper;
import com.maidweapon.forge.item.MaidInfusion;
import com.maidweapon.forge.item.MaidWeaponItem;
import com.maidweapon.forge.system.deployment.ContractWeaponLocator;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Core contract lifecycle facade shared by every contract carrier and the
 * Contract Table.
 *
 * <p>The Core only decides which contract state transition is requested. TLM
 * serialization/film details remain behind the compat facade.</p>
 */
public final class ContractLifecycleService {
    public enum ToggleResult {
        MANIFESTED,
        RECALLED,
        NOT_CONTRACT,
        NOT_OWNER,
        SUPERSEDED,
        MAID_NOT_FOUND,
        FAILED
    }

    public static boolean capture(
            Player player,
            Entity maid,
            ItemStack contract,
            boolean notifyPlayer
    ) {
        if (player == null || maid == null || contract.isEmpty()
                || !MaidInfusion.isWeapon(contract)
                || !TouhouLittleMaidCompat.isTouhouLittleMaidLoaded()
                || !TouhouLittleMaidCompat.isMaidEntity(maid)) {
            return false;
        }
        if (MaidWeaponItem.hasMaidData(contract)
                && !MaidWeaponItem.isOwner(contract, player)) {
            return false;
        }
        return TouhouLittleMaidHelper.convertMaidToWeapon(
                player,
                maid,
                contract,
                notifyPlayer
        );
    }

    public static boolean manifest(
            Player player,
            ItemStack contract,
            boolean notifyPlayer
    ) {
        return manifest(player, contract, notifyPlayer, maid -> {});
    }

    public static boolean manifest(Player player, ItemStack contract, boolean notifyPlayer,
                                   java.util.function.Consumer<Entity> beforeSpawn) {
        if (player == null || contract.isEmpty()
                || !MaidInfusion.isInfused(contract)
                || !MaidWeaponItem.isOwner(contract, player)
                || MaidWeaponItem.isContractSuperseded(contract)) {
            return false;
        }
        return TouhouLittleMaidHelper.convertWeaponToMaid(
                player,
                contract,
                notifyPlayer,
                beforeSpawn
        );
    }

    /**
     * Toggle one concrete contract between stored and manifested authority.
     *
     * <p>Stored/live state is derived from the same contract storage boundary
     * used by the Contract Table. The input item class does not get to invent a
     * second lifecycle.</p>
     */
    public static ToggleResult toggle(Player player, ItemStack contract, boolean notifyPlayer) {
        if (player == null || contract.isEmpty() || !MaidInfusion.isInfused(contract)) {
            return ToggleResult.NOT_CONTRACT;
        }
        if (!MaidWeaponItem.isOwner(contract, player)) {
            return ToggleResult.NOT_OWNER;
        }
        if (MaidWeaponItem.isContractSuperseded(contract)) {
            return ToggleResult.SUPERSEDED;
        }

        if (MaidInfusion.containsMaid(contract)) {
            return manifest(player, contract, notifyPlayer)
                    ? ToggleResult.MANIFESTED
                    : ToggleResult.FAILED;
        }

        String maidId = MaidWeaponItem.getBoundMaidUUID(contract);
        Entity maid = maidId == null || maidId.isEmpty()
                ? null
                : ContractWeaponLocator.findManifestedMaid(player, maidId);
        if (maid == null) {
            return ToggleResult.MAID_NOT_FOUND;
        }

        return capture(player, maid, contract, notifyPlayer)
                ? ToggleResult.RECALLED
                : ToggleResult.FAILED;
    }

    public static boolean infuseFromFilm(
            Player player,
            ItemStack film,
            ItemStack contract
    ) {
        return TouhouLittleMaidHelper.infuseFromFilm(player, film, contract);
    }

    public static ItemStack extractToFilm(
            Player player,
            ItemStack contract,
            ItemStack emptyFilm
    ) {
        return TouhouLittleMaidHelper.extractMaidToFilm(
                player,
                contract,
                emptyFilm
        );
    }

    private ContractLifecycleService() {
    }
}
