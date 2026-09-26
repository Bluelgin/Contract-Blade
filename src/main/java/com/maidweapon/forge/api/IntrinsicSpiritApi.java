package com.maidweapon.forge.api;

import com.maidweapon.forge.compat.TouhouLittleMaidHelper;
import com.maidweapon.forge.item.MaidWeaponItem;
import com.maidweapon.forge.system.InfusedMaidDeploymentSystem;
import com.maidweapon.forge.system.MaidEntityDataCodec;
import com.maidweapon.common.MaidWeaponConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Set;

/**
 * Stores addon-defined blade spirits independently from the player's ordinary
 * weapon contract while projecting only the selected channel into the legacy
 * contract fields used by the deployment system.
 *
 * <p>The projection is transactional: a manifested maid is recalled first,
 * the complete contract NBT is copied and validated, and only then is the old
 * channel cleared. A failed recall or validation leaves the original contract
 * untouched.</p>
 */
public final class IntrinsicSpiritApi {
    private static final int SCHEMA_VERSION = 1;
    private static final String ROOT = "MaidWeaponIntrinsicSpirits";
    private static final String EXTERNAL = "MaidWeaponExternalContract";
    private static final String PROJECTION = "MaidWeaponIntrinsicProjection";
    private static final String SCHEMA = "MaidWeaponIntrinsicSchema";
    private static final String CONTRACT = "Contract";
    private static final String SPIRIT_ID = "SpiritId";
    private static final String DISPLAY_NAME = "DisplayName";
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
            "MaidDeploymentLocation",
            "MaidDeploymentRecoveryFailed",
            "MaidInfusionMagicTaskFailure",
            "MaidInfusionSlashBladeTaskFailure",
            "MaidInfusionTaczTaskFailure",
            "MaidInfusionTaczAmmoLinkFailure",
            EmbeddedSpiritApi.TAG_SPIRIT_ID,
            EmbeddedSpiritApi.TAG_SPIRIT_NAME,
            EmbeddedSpiritApi.TAG_DORMANT);

    /** Creates or safely migrates an intrinsic spirit without activating it. */
    public static boolean ensureIntrinsicSpirit(
            Player owner,
            ItemStack weapon,
            String spiritId,
            String displayName) {
        if (!valid(owner, weapon, spiritId)) return false;
        migrateProjectedAuthority(weapon);
        if (hasIntrinsicSpirit(weapon, spiritId)) return true;
        CompoundTag existingRoot = weapon.getTag();
        if (existingRoot != null
                && existingRoot.getCompound(ROOT).getAllKeys().size()
                >= MaidWeaponConfig.CONTRACT_NBT_MAX_INTRINSIC_SPIRITS.get()) {
            return false;
        }

        // Legacy addon spirits occupied the generic contract channel. Migrate
        // their exact serialized contract instead of constructing a new maid.
        if (EmbeddedSpiritApi.isSpirit(weapon, spiritId)) {
            if (!ensureGenericContractStored(owner, weapon)) return false;
            CompoundTag legacy = captureContract(weapon);
            if (!validContract(legacy)) return false;
            storeIntrinsic(weapon, spiritId, displayName, legacy);
            clearContract(weapon);
            weapon.getOrCreateTag().putInt(SCHEMA, SCHEMA_VERSION);
            return true;
        }

        if (!ensureGenericContractStored(owner, weapon)) return false;
        CompoundTag external = captureContract(weapon);
        MaidEntityDataCodec.migrate(external);
        clearContract(weapon);

        boolean created = EmbeddedSpiritApi.bindPresetSpirit(
                owner, weapon, spiritId, displayName);
        CompoundTag intrinsic = created ? captureContract(weapon) : new CompoundTag();
        clearContract(weapon);
        if (!created || !validContract(intrinsic)) {
            restoreContract(weapon, external);
            return false;
        }

        storeIntrinsic(weapon, spiritId, displayName, intrinsic);
        restoreContract(weapon, external);
        weapon.getOrCreateTag().putInt(SCHEMA, SCHEMA_VERSION);
        return true;
    }

    /**
     * Selects the intrinsic or external contract channel. Exactly one channel
     * is projected into the deployment system at a time.
     */
    public static boolean setIntrinsicSpiritActive(
            Player owner,
            ItemStack weapon,
            String spiritId,
            boolean active) {
        if (!valid(owner, weapon, spiritId)) return false;
        migrateProjectedAuthority(weapon);
        if (!hasIntrinsicSpirit(weapon, spiritId)) {
            return false;
        }

        String projected = projection(weapon);
        if (active) {
            if (spiritId.equals(projected)) {
                // A stale marker must not silently claim that an unrelated
                // generic contract is the selected intrinsic spirit.
                if (!EmbeddedSpiritApi.isSpirit(weapon, spiritId)) return false;
                EmbeddedSpiritApi.setDormant(weapon, false);
                return true;
            }
            if (!projected.isEmpty()
                    && !switchProjectionToExternal(owner, weapon, projected)) {
                return false;
            }
            if (!ensureGenericContractStored(owner, weapon)) return false;

            CompoundTag external = captureContract(weapon);
            MaidEntityDataCodec.migrate(external);
            if (!external.isEmpty()) {
                weapon.getOrCreateTag().put(EXTERNAL, external.copy());
            } else {
                weapon.getOrCreateTag().remove(EXTERNAL);
            }
            clearContract(weapon);

            CompoundTag intrinsic = intrinsicContract(weapon, spiritId);
            if (!usableContract(intrinsic)) {
                restoreExternalArchive(weapon);
                return false;
            }
            restoreContract(weapon, intrinsic);
            removeArchivedContract(weapon, spiritId);
            weapon.getOrCreateTag().putString(PROJECTION, spiritId);
            EmbeddedSpiritApi.setDormant(weapon, false);
            return true;
        }

        if (!spiritId.equals(projected)) return true;
        return switchProjectionToExternal(owner, weapon, spiritId);
    }

    public static boolean hasIntrinsicSpirit(ItemStack weapon, String spiritId) {
        if (weapon.isEmpty() || spiritId == null || spiritId.isBlank()) return false;
        if (spiritId.equals(projection(weapon))) {
            return validContract(captureContract(weapon));
        }
        return validContract(intrinsicContract(weapon, spiritId));
    }

    public static boolean isIntrinsicSpiritActive(ItemStack weapon, String spiritId) {
        migrateProjectedAuthority(weapon);
        return spiritId != null
                && spiritId.equals(projection(weapon))
                && hasIntrinsicSpirit(weapon, spiritId)
                && EmbeddedSpiritApi.isSpirit(weapon, spiritId);
    }

    /**
     * Assigns an addon-provided TLM model to one intrinsic spirit without
     * changing the player's external contract appearance. Stored, projected
     * and currently manifested copies are updated together.
     */
    public static boolean setIntrinsicSpiritModel(
            Player owner,
            ItemStack weapon,
            String spiritId,
            String modelId) {
        if (!valid(owner, weapon, spiritId)
                || modelId == null || modelId.isBlank()
                || !hasIntrinsicSpirit(weapon, spiritId)) {
            return false;
        }
        migrateProjectedAuthority(weapon);

        CompoundTag root = weapon.getOrCreateTag();
        boolean active = spiritId.equals(projection(weapon));
        if (active) {
            if (MaidWeaponItem.hasMaidEntityData(weapon)
                    && !setStoredModel(root, modelId)) return false;
        } else {
            CompoundTag spirits = root.getCompound(ROOT);
            CompoundTag entry = spirits.getCompound(spiritId);
            CompoundTag contract = entry.getCompound(CONTRACT);
            if (!setStoredModel(contract, modelId)) return false;
            entry.put(CONTRACT, contract);
            spirits.put(spiritId, entry);
            root.put(ROOT, spirits);
        }

        if (active) {
            String maidId = MaidWeaponItem.getBoundMaidUUID(weapon);
            Entity manifested = maidId == null || maidId.isBlank()
                    ? null
                    : InfusedMaidDeploymentSystem.findManifestedMaid(owner, maidId);
            if (manifested != null) setManifestedModel(manifested, modelId);
        }
        return true;
    }

    /**
     * Removes one obsolete addon model assignment without touching any model
     * selected by the player. If the spirit is currently manifested it is
     * recalled first, so its complete entity data remains authoritative.
     */
    public static boolean clearIntrinsicSpiritModelIfEquals(
            Player owner,
            ItemStack weapon,
            String spiritId,
            String obsoleteModelId) {
        if (!valid(owner, weapon, spiritId)
                || obsoleteModelId == null || obsoleteModelId.isBlank()
                || !hasIntrinsicSpirit(weapon, spiritId)) {
            return false;
        }
        migrateProjectedAuthority(weapon);

        CompoundTag root = weapon.getOrCreateTag();
        if (!spiritId.equals(projection(weapon))) {
            CompoundTag spirits = root.getCompound(ROOT);
            CompoundTag entry = spirits.getCompound(spiritId);
            CompoundTag contract = entry.getCompound(CONTRACT);
            if (clearStoredModelIfEquals(contract, obsoleteModelId)) {
                entry.put(CONTRACT, contract);
                spirits.put(spiritId, entry);
                root.put(ROOT, spirits);
            }
            return true;
        }

        if (clearProjectedModelIfEquals(root, obsoleteModelId)) {
            return true;
        }

        // A manifested contract has MaidData but deliberately no serialized
        // MaidEntityData. Inspect the live entity first: recalling merely to
        // discover its model would cause callers that synchronize every tick
        // to alternate forever between recall and deployment.
        if (MaidWeaponItem.hasMaidData(weapon)
                && !MaidWeaponItem.hasMaidEntityData(weapon)) {
            String maidId = MaidWeaponItem.getBoundMaidUUID(weapon);
            Entity manifested = maidId == null || maidId.isBlank()
                    ? null
                    : InfusedMaidDeploymentSystem.findManifestedMaid(owner, maidId);
            if (manifested != null
                    && manifestedUsesModel(manifested, obsoleteModelId)) {
                if (!InfusedMaidDeploymentSystem.forceRecall(owner, maidId, 0)) {
                    return false;
                }
                clearProjectedModelIfEquals(root, obsoleteModelId);
            }
        }
        return true;
    }

    /** Used by contract tables to prevent extracting an internal blade spirit. */
    public static boolean hasActiveProjection(ItemStack weapon) {
        migrateProjectedAuthority(weapon);
        return !projection(weapon).isEmpty();
    }

    /** Explicit migration hook for addons that inspect stored weapons during world load. */
    public static void migrateStoredProjection(ItemStack weapon) {
        if (weapon != null && !weapon.isEmpty()) migrateProjectedAuthority(weapon);
    }

    private static boolean switchProjectionToExternal(
            Player owner,
            ItemStack weapon,
            String spiritId) {
        EmbeddedSpiritApi.setDormant(weapon, true);
        if (!ensureGenericContractStored(owner, weapon)) {
            EmbeddedSpiritApi.setDormant(weapon, false);
            return false;
        }

        CompoundTag updatedIntrinsic = captureContract(weapon);
        if (!usableContract(updatedIntrinsic)) {
            EmbeddedSpiritApi.setDormant(weapon, false);
            return false;
        }
        String displayName = intrinsicEntry(weapon, spiritId)
                .getString(DISPLAY_NAME);
        storeIntrinsic(weapon, spiritId, displayName, updatedIntrinsic);
        clearContract(weapon);
        weapon.getOrCreateTag().remove(PROJECTION);
        restoreExternalArchive(weapon);
        return true;
    }

    private static boolean ensureGenericContractStored(
            Player owner,
            ItemStack weapon) {
        if (!MaidWeaponItem.hasMaidData(weapon)
                || MaidWeaponItem.hasMaidEntityData(weapon)) {
            return true;
        }
        String maidId = MaidWeaponItem.getBoundMaidUUID(weapon);
        if (maidId == null || maidId.isBlank()) return false;
        Entity manifested = InfusedMaidDeploymentSystem.findManifestedMaid(owner, maidId);
        if (manifested == null) return false;
        return InfusedMaidDeploymentSystem.forceRecall(owner, maidId, 0)
                && MaidWeaponItem.hasMaidEntityData(weapon);
    }

    private static void restoreExternalArchive(ItemStack weapon) {
        CompoundTag root = weapon.getOrCreateTag();
        if (root.contains(EXTERNAL, Tag.TAG_COMPOUND)) {
            restoreContract(weapon, root.getCompound(EXTERNAL));
            root.remove(EXTERNAL);
        }
    }

    private static void storeIntrinsic(
            ItemStack weapon,
            String spiritId,
            String displayName,
            CompoundTag contract) {
        CompoundTag root = weapon.getOrCreateTag();
        CompoundTag spirits = root.getCompound(ROOT);
        CompoundTag entry = spirits.getCompound(spiritId);
        entry.putString(SPIRIT_ID, spiritId);
        entry.putString(DISPLAY_NAME,
                displayName == null || displayName.isBlank() ? spiritId : displayName);
        CompoundTag normalized = contract.copy();
        MaidEntityDataCodec.migrate(normalized);
        entry.put(CONTRACT, normalized);
        spirits.put(spiritId, entry);
        root.put(ROOT, spirits);
    }

    private static void removeArchivedContract(ItemStack weapon, String spiritId) {
        CompoundTag root = weapon.getOrCreateTag();
        CompoundTag spirits = root.getCompound(ROOT);
        CompoundTag entry = spirits.getCompound(spiritId);
        entry.remove(CONTRACT);
        spirits.put(spiritId, entry);
        root.put(ROOT, spirits);
    }

    /** Migrates legacy active items that stored both the root projection and an archive copy. */
    private static void migrateProjectedAuthority(ItemStack weapon) {
        String projected = projection(weapon);
        CompoundTag root = weapon.getTag();
        if (projected.isEmpty() || root == null || !root.contains(ROOT, Tag.TAG_COMPOUND)) return;

        CompoundTag spirits = root.getCompound(ROOT);
        if (!spirits.contains(projected, Tag.TAG_COMPOUND)) return;
        CompoundTag entry = spirits.getCompound(projected);
        CompoundTag archived = entry.getCompound(CONTRACT);
        CompoundTag current = captureContract(weapon);

        if (!usableContract(current)) {
            if (!usableContract(archived)) return;
            restoreContract(weapon, archived);
            current = captureContract(weapon);
            if (!usableContract(current)) return;
        }

        // Compression is also a migration. If it cannot be completed, retain the
        // archive so no recovery source is destroyed.
        if (root.contains(MaidEntityDataCodec.LEGACY_DATA, Tag.TAG_COMPOUND)
                && !MaidEntityDataCodec.migrate(root)) return;
        entry.putString(SPIRIT_ID, projected);
        if (!entry.contains(DISPLAY_NAME, Tag.TAG_STRING)) entry.putString(DISPLAY_NAME, projected);
        entry.remove(CONTRACT);
        spirits.put(projected, entry);
        root.put(ROOT, spirits);
        root.putInt(SCHEMA, SCHEMA_VERSION);
    }

    private static CompoundTag intrinsicEntry(ItemStack weapon, String spiritId) {
        CompoundTag root = weapon.getTag();
        if (root == null || !root.contains(ROOT, Tag.TAG_COMPOUND)) {
            return new CompoundTag();
        }
        CompoundTag spirits = root.getCompound(ROOT);
        return spirits.contains(spiritId, Tag.TAG_COMPOUND)
                ? spirits.getCompound(spiritId) : new CompoundTag();
    }

    private static CompoundTag intrinsicContract(ItemStack weapon, String spiritId) {
        CompoundTag entry = intrinsicEntry(weapon, spiritId);
        return entry.contains(CONTRACT, Tag.TAG_COMPOUND)
                ? entry.getCompound(CONTRACT) : new CompoundTag();
    }

    private static String projection(ItemStack weapon) {
        CompoundTag tag = weapon.getTag();
        return tag == null ? "" : tag.getString(PROJECTION);
    }

    private static CompoundTag captureContract(ItemStack weapon) {
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

    private static void restoreContract(ItemStack weapon, CompoundTag contract) {
        clearContract(weapon);
        if (contract == null || contract.isEmpty()) return;
        CompoundTag root = weapon.getOrCreateTag();
        for (String key : contract.getAllKeys()) {
            Tag value = contract.get(key);
            if (value != null && isContractKey(key)) root.put(key, value.copy());
        }
    }

    private static void clearContract(ItemStack weapon) {
        CompoundTag root = weapon.getTag();
        if (root == null) return;
        for (String key : new ArrayList<>(root.getAllKeys())) {
            if (isContractKey(key)) root.remove(key);
        }
    }

    private static boolean isContractKey(String key) {
        return CONTRACT_KEYS.contains(key);
    }

    private static boolean validContract(CompoundTag contract) {
        return contract != null
                && contract.contains("MaidData", Tag.TAG_COMPOUND)
                && contract.contains("MaidUUID", Tag.TAG_STRING)
                && contract.contains("MaidBindingId", Tag.TAG_STRING);
    }

    private static boolean usableContract(CompoundTag contract) {
        if (!validContract(contract) || !MaidEntityDataCodec.hasData(contract)) {
            return validContract(contract);
        }
        try {
            MaidEntityDataCodec.read(contract);
            return true;
        } catch (java.io.IOException ignored) {
            return false;
        }
    }

    private static boolean setStoredModel(CompoundTag contract, String modelId) {
        return validContract(contract) && MaidEntityDataCodec.update(
                contract, entityData -> entityData.putString("ModelId", modelId));
    }

    private static boolean clearStoredModelIfEquals(
            CompoundTag contract,
            String obsoleteModelId) {
        if (!validContract(contract) || !MaidEntityDataCodec.hasData(contract)) return false;
        final boolean[] changed = {false};
        boolean updated = MaidEntityDataCodec.update(contract, entityData -> {
            if (obsoleteModelId.equals(entityData.getString("ModelId"))) {
                entityData.remove("ModelId");
                changed[0] = true;
            }
        });
        return updated && changed[0];
    }

    private static boolean clearProjectedModelIfEquals(
            CompoundTag root,
            String obsoleteModelId) {
        if (!MaidEntityDataCodec.hasData(root)) return false;
        final boolean[] changed = {false};
        boolean updated = MaidEntityDataCodec.update(root, entityData -> {
            if (obsoleteModelId.equals(entityData.getString("ModelId"))) {
                entityData.remove("ModelId");
                changed[0] = true;
            }
        });
        return updated && changed[0];
    }

    private static boolean manifestedUsesModel(
            Entity maid,
            String modelId) {
        try {
            Object current = maid.getClass().getMethod("getModelId").invoke(maid);
            return modelId.equals(current);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Unknown TLM versions must never trigger a speculative recall.
            return false;
        }
    }

    private static void setManifestedModel(Entity maid, String modelId) {
        try {
            Object current = maid.getClass().getMethod("getModelId").invoke(maid);
            if (modelId.equals(current)) return;
            maid.getClass().getMethod("setModelId", String.class)
                    .invoke(maid, modelId);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Stored NBT remains authoritative and applies on the next deploy.
        }
    }

    private static boolean valid(Player owner, ItemStack weapon, String spiritId) {
        return owner != null && !owner.level().isClientSide
                && !weapon.isEmpty()
                && spiritId != null && !spiritId.isBlank()
                && spiritId.length() <= 64
                && net.minecraft.resources.ResourceLocation.tryParse(spiritId) != null;
    }

    private IntrinsicSpiritApi() {
    }
}
