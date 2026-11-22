package com.maidweapon.port.storage;

import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** 1.21.1-specific copy/validate/commit boundary for owned custom data. */
public final class ContractStackDataAccess {
    public static final Set<String> OWNED_KEYS = Set.of(
        "MaidData", "MaidUUID", "MaidBindingId", "OwnerUUID", "OwnerName",
        "MaidContractSuperseded", "MaidWeaponSoulTransfer",
        "MaidEntityData", "MaidEntityDataCompressed", "MaidEntityDataFormat",
        "MaidEntityDataUncompressedSize", "MaidEntityDataChecksum",
        "MaidWeaponExternalContract", "MaidWeaponIntrinsicSpirits",
        "MaidWeaponEmbeddedSpirit", "MaidWeaponEmbeddedSpiritName",
        "MaidWeaponEmbeddedSpiritDormant", "ContractCarrierStorageVersion",
        "MaidInfusionOriginalTask", "MaidInfusionOriginalSchedule",
        "MaidDeploymentLocation", "MaidDeploymentRecoveryFailed",
        "MaidInfusionMagicTaskFailure", "MaidInfusionSlashBladeTaskFailure",
        "MaidInfusionTaczTaskFailure", "MaidInfusionTaczAmmoLinkFailure"
    );

    public static CompoundTag readRoot(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    public static CompoundTag readContract(ItemStack stack) {
        return extractOwned(readRoot(stack));
    }

    public static CompoundTag extractOwned(CompoundTag original) {
        Objects.requireNonNull(original, "original");
        CompoundTag result = new CompoundTag();
        for (String key : OWNED_KEYS) {
            if (original.contains(key)) result.put(key, original.get(key).copy());
        }
        return result;
    }

    /** Retain every foreign key; refuse accidental write of foreign ownership. */
    public static CompoundTag mergeOwned(CompoundTag original, CompoundTag replacement) {
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(replacement, "replacement");
        requireSupportedSchema(original);
        requireSupportedSchema(replacement);
        for (String key : replacement.getAllKeys()) {
            if (!OWNED_KEYS.contains(key))
                throw new IllegalArgumentException("Refusing foreign key: " + key);
        }
        CompoundTag next = original.copy();
        for (String key : OWNED_KEYS) next.remove(key);
        for (String key : replacement.getAllKeys())
            next.put(key, replacement.get(key).copy());
        return next;
    }

    /**
     * Refuse destructive writes if future versions own the data. Reading the
     * raw root is still permitted for diagnostics and backups.
     */
    public static void requireSupportedSchema(CompoundTag root) {
        Objects.requireNonNull(root, "root");
        String version = "ContractCarrierStorageVersion";
        if (!root.contains(version)) return; // historical version-zero carrier
        if (!root.contains(version, net.minecraft.nbt.Tag.TAG_INT)
                || root.getInt(version) < 0 || root.getInt(version) > 1)
            throw new IllegalStateException("Unsupported contract carrier storage schema");
    }

    public static void replaceContract(ItemStack stack, CompoundTag replacement) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) throw new IllegalArgumentException("empty carrier");
        CompoundTag merged = mergeOwned(readRoot(stack), replacement);
        if (merged.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
        else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(merged));
    }

    public static void updateContract(ItemStack stack, UnaryOperator<CompoundTag> mutate) {
        Objects.requireNonNull(mutate, "mutate");
        CompoundTag next = Objects.requireNonNull(mutate.apply(readContract(stack)), "next");
        replaceContract(stack, next);
    }

    private ContractStackDataAccess() {}
}
