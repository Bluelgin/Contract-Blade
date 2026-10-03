package com.maidweapon.forge.system.contract;

import com.maidweapon.forge.api.EmbeddedSpiritApi;
import com.maidweapon.forge.compat.CompatDiagnostics;
import com.maidweapon.forge.system.MaidEntityDataCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.Set;

/** Exact active-channel payload operations. Never copies unrelated weapon NBT or dormant archives. */
public final class ContractChannelStorage {
    private static final Set<String> CONTRACT_KEYS = Set.of(
            "MaidData",
            MaidEntityDataCodec.LEGACY_DATA,
            MaidEntityDataCodec.COMPRESSED_DATA,
            MaidEntityDataCodec.FORMAT,
            MaidEntityDataCodec.UNCOMPRESSED_SIZE,
            MaidEntityDataCodec.CHECKSUM,
            "MaidUUID",
            "MaidBindingId",
            "MaidContractSuperseded",
            "OwnerUUID",
            "OwnerName",
            "MaidInfusionOriginalTask",
            "MaidInfusionOriginalSchedule",
            "MaidDeploymentLocation",
            "MaidDeploymentRecoveryFailed",
            "MaidInfusionMagicTaskFailure",
            "MaidInfusionSlashBladeTaskFailure",
            "MaidInfusionTaczTaskFailure",
            "MaidInfusionTaczAmmoLinkFailure",
            EmbeddedSpiritApi.TAG_SPIRIT_ID,
            EmbeddedSpiritApi.TAG_SPIRIT_NAME,
            EmbeddedSpiritApi.TAG_DORMANT);

    public static CompoundTag captureContract(ItemStack weapon) {
        CompoundTag result = new CompoundTag();
        CompoundTag root = weapon.getTag();
        if (root == null) return result;
        for (String key : new ArrayList<>(root.getAllKeys())) {
            if (!isContractKey(key)) continue;
            Tag value = root.get(key);
            if (value != null) result.put(key, value.copy());
        }
        return result;
    }

    public static void restoreContract(ItemStack weapon, CompoundTag contract) {
        clearContract(weapon);
        if (contract == null || contract.isEmpty()) return;
        CompoundTag root = weapon.getOrCreateTag();
        for (String key : contract.getAllKeys()) {
            Tag value = contract.get(key);
            if (value != null && isContractKey(key)) root.put(key, value.copy());
        }
    }

    public static void clearContract(ItemStack weapon) {
        CompoundTag root = weapon.getTag();
        if (root == null) return;
        for (String key : new ArrayList<>(root.getAllKeys())) {
            if (isContractKey(key)) root.remove(key);
        }
    }

    public static boolean isContractKey(String key) {
        return CONTRACT_KEYS.contains(key);
    }

    public static boolean validContract(CompoundTag contract) {
        return contract != null
                && contract.contains("MaidData", Tag.TAG_COMPOUND)
                && contract.contains("MaidUUID", Tag.TAG_STRING)
                && contract.contains("MaidBindingId", Tag.TAG_STRING);
    }

    public static boolean usableContract(CompoundTag contract) {
        if (!validContract(contract) || !MaidEntityDataCodec.hasData(contract)) {
            return validContract(contract);
        }
        try {
            MaidEntityDataCodec.read(contract);
            return true;
        } catch (java.io.IOException failure) {
            CompatDiagnostics.warnOnce("intrinsic:payload-validation", failure);
            return false;
        }
    }


    private ContractChannelStorage() {}
}
