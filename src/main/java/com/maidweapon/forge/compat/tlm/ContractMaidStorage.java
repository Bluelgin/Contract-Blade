package com.maidweapon.forge.compat.tlm;

import com.maidweapon.forge.system.contract.ContractCarrierData;

import com.maidweapon.forge.system.ContractNbtAudit;
import com.maidweapon.forge.system.ContractNbtGuard;
import com.maidweapon.forge.system.MaidEntityDataCodec;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.Map;
import java.util.WeakHashMap;

/** Authoritative serialization boundary for stored contract maids. */
public final class ContractMaidStorage {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long NBT_NOTICE_COOLDOWN = 200L;
    private static final Map<Player, Long> LAST_NBT_NOTICE = new WeakHashMap<>();

    public static CompoundTag read(Player player, ItemStack stack) {
        CompoundTag root = stack.getTag();
        if (root == null) return null;
        try {
            if (root.contains(MaidEntityDataCodec.LEGACY_DATA, Tag.TAG_COMPOUND)) {
                MaidEntityDataCodec.migrate(root);
            }
            return MaidEntityDataCodec.read(root);
        } catch (IOException exception) {
            LOGGER.error("[MaidWeapon] Stored maid data failed validation for {}",
                    player.getScoreboardName(), exception);
            return null;
        }
    }

    public static void remove(ItemStack stack) {
        CompoundTag root = stack.getTag();
        if (root == null) return;
        MaidEntityDataCodec.remove(root);
        stack.setTag(root);
    }

    /**
     * Atomically encodes and commits maid data. Failure leaves the previous
     * contract payload untouched.
     */
    public static boolean commit(Player player, ItemStack weapon, CompoundTag maidData) {
        CompoundTag candidate = weapon.getTag() == null
                ? new CompoundTag() : weapon.getTag().copy();
        try {
            MaidEntityDataCodec.write(candidate, maidData);
        } catch (IOException exception) {
            LOGGER.error("[MaidWeapon] Refused to discard maid because entity data could not be encoded", exception);
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.contract_nbt_encode_failed"), false);
            return false;
        }
        weapon.setTag(candidate);
        ContractNbtGuard.Result result = ContractNbtGuard.inspect(candidate);
        if (result.warning()) {
            ContractNbtAudit.Report audit = ContractNbtAudit.inspect(weapon);
            LOGGER.warn("[MaidWeapon] Large contract NBT: item={}, tag={}, maid={}, compressed={}, "
                            + "intrinsicArchive={}, externalArchive={}, duplicateProjection={}",
                    ContractNbtGuard.formatBytes(audit.completeItemBytes()),
                    ContractNbtGuard.formatBytes(audit.itemTagBytes()),
                    ContractNbtGuard.formatBytes(audit.currentMaidBytes()),
                    ContractNbtGuard.formatBytes(audit.currentCompressedBytes()),
                    ContractNbtGuard.formatBytes(audit.intrinsicArchiveBytes()),
                    ContractNbtGuard.formatBytes(audit.externalArchiveBytes()),
                    audit.duplicateProjection());
            notifySize(player, result, ContractNbtGuard.inspect(maidData), false);
        }
        return true;
    }

    public static boolean setHealth(ItemStack weapon, float health) {
        CompoundTag root = weapon.getTag();
        return root != null && MaidEntityDataCodec.update(
                root, data -> data.putFloat("Health", health));
    }

    public static void retirePreviousContracts(Player player, String maidId, ItemStack target) {
        boolean retiredAny = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack != target && maidId.equals(ContractCarrierData.getBoundMaidUUID(stack))) {
                ContractCarrierData.setContractSuperseded(stack, true);
                retiredAny = true;
            }
        }
        if (retiredAny) {
            player.displayClientMessage(
                    Component.translatable("maid_weapon.message.contract_transferred"), true);
        }
    }

    public static void inspectExternalPayload(Player player, CompoundTag payload) {
        ContractNbtGuard.Result result = ContractNbtGuard.inspect(payload);
        if (result.warning()) notifySize(player, result, null, true);
    }

    private static void notifySize(Player player, ContractNbtGuard.Result result,
                                   ContractNbtGuard.Result rawMaid, boolean force) {
        long now = player.level().getGameTime();
        if (!force && now - LAST_NBT_NOTICE.getOrDefault(player, Long.MIN_VALUE / 2)
                < NBT_NOTICE_COOLDOWN) return;
        LAST_NBT_NOTICE.put(player, now);

        player.displayClientMessage(Component.translatable(
                "maid_weapon.message.contract_nbt_" + result.warningLevel(),
                ContractNbtGuard.formatBytes(result.bytes())), false);
        if (rawMaid == null) return;
        for (ContractNbtGuard.ItemContribution item : rawMaid.largestItems()) {
            player.displayClientMessage(Component.translatable(
                    "maid_weapon.message.contract_nbt_largest",
                    item.itemId(), ContractNbtGuard.formatBytes(item.bytes()), item.path()), false);
        }
    }

    private ContractMaidStorage() {}
}
