package com.maidweapon.forge.api;

import static com.maidweapon.forge.system.contract.ContractChannelStorage.*;
import static com.maidweapon.forge.compat.tlm.IntrinsicSpiritModels.*;

import com.maidweapon.forge.system.contract.ContractCarrierData;

import com.maidweapon.forge.compat.TouhouLittleMaidHelper;
import com.maidweapon.forge.system.InfusedMaidDeploymentSystem;
import com.maidweapon.forge.system.MaidEntityDataCodec;
import com.maidweapon.common.MaidWeaponConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;


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
    private static final String DETACHED = "MaidWeaponDetachedIntrinsicSpirits";
    private static final String CONTRACT = "Contract";
    private static final String SPIRIT_ID = "SpiritId";
    private static final String DISPLAY_NAME = "DisplayName";
    /** Creates or safely migrates an intrinsic spirit without activating it. */
    public static boolean ensureIntrinsicSpirit(
            Player owner,
            ItemStack weapon,
            String spiritId,
            String displayName) {
        if (!valid(owner, weapon, spiritId)) return false;
        if (weapon.hasTag() && weapon.getTag().getCompound(DETACHED).getBoolean(spiritId)) return false;
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
            if (ContractCarrierData.hasMaidEntityData(weapon)
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
            String maidId = ContractCarrierData.getBoundMaidUUID(weapon);
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
        if (ContractCarrierData.hasMaidData(weapon)
                && !ContractCarrierData.hasMaidEntityData(weapon)) {
            String maidId = ContractCarrierData.getBoundMaidUUID(weapon);
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

    /** Recall first, in the owner's dimension and within the ordinary 32-block range. */
    public static boolean prepareSpiritTransfer(Player owner, ItemStack weapon, String spiritId) {
        if (!valid(owner, weapon, spiritId)) return false;
        CompoundTag contract = transferContract(weapon, spiritId);
        if (contract.isEmpty()) return !hasIntrinsicSpirit(weapon, spiritId)
                && !EmbeddedSpiritApi.isSpirit(weapon, spiritId)
                && (!weapon.hasTag() || !weapon.getTag().getCompound(ROOT).contains(spiritId));
        if (!validContract(contract) || !contract.hasUUID("OwnerUUID")
                || !contract.getUUID("OwnerUUID").equals(owner.getUUID())) return false;
        Entity maid = InfusedMaidDeploymentSystem.findManifestedMaid(owner, contract.getString("MaidUUID"));
        if (maid != null) {
            if (!maid.level().dimension().equals(owner.level().dimension())
                    || maid.distanceToSqr(owner) > 32 * 32
                    || !spiritId.equals(projection(weapon))
                    && !EmbeddedSpiritApi.isSpirit(weapon, spiritId)) return false;
            if (!InfusedMaidDeploymentSystem.forceRecall(owner, maid.getStringUUID(), 0)) return false;
            contract = transferContract(weapon, spiritId);
        }
        if (!MaidEntityDataCodec.hasData(contract)) return false;
        try { return MaidEntityDataCodec.read(contract) != null; }
        catch (java.io.IOException failure) { return false; }
    }

    /** Operates on a prepared stack copy, never on a live entity or unrelated channel. */
    public static CompoundTag detachStoredSpirit(ItemStack weapon, String spiritId) {
        CompoundTag contract = transferContract(weapon, spiritId).copy();
        if (!contract.isEmpty() && (!validContract(contract) || !MaidEntityDataCodec.hasData(contract))) return null;
        if (!contract.isEmpty()) {
            try { MaidEntityDataCodec.read(contract); }
            catch (java.io.IOException failure) { return null; }
        }
        boolean projected = spiritId.equals(projection(weapon))
                || EmbeddedSpiritApi.isSpirit(weapon, spiritId);
        CompoundTag root = weapon.getOrCreateTag();
        if (projected) {
            clearContract(weapon);
            root.remove(PROJECTION);
        }
        CompoundTag spirits = root.getCompound(ROOT);
        spirits.remove(spiritId);
        if (spirits.isEmpty()) root.remove(ROOT); else root.put(ROOT, spirits);
        if (projected) restoreExternalArchive(weapon);
        CompoundTag detached = root.getCompound(DETACHED);
        detached.putBoolean(spiritId, true);
        root.put(DETACHED, detached);
        return contract;
    }

    /** Restore the exact spirit contract, including its binding/home identity. */
    public static boolean attachStoredSpirit(ItemStack weapon, String spiritId, CompoundTag contract) {
        if (weapon.isEmpty() || !validContract(contract) || !MaidEntityDataCodec.hasData(contract)
                || ContractCarrierData.hasMaidData(weapon) || hasActiveProjection(weapon)
                || ContractCarrierData.hasMaidEntityData(weapon)
                || weapon.hasTag() && (weapon.getTag().contains(ROOT) || weapon.getTag().contains(EXTERNAL))) return false;
        try { MaidEntityDataCodec.read(contract); }
        catch (java.io.IOException failure) { return false; }
        String name = contract.getString(EmbeddedSpiritApi.TAG_SPIRIT_NAME);
        storeIntrinsic(weapon, spiritId, name.isBlank() ? spiritId : name, contract);
        restoreContract(weapon, contract);
        removeArchivedContract(weapon, spiritId);
        CompoundTag root = weapon.getOrCreateTag();
        root.putString(PROJECTION, spiritId);
        CompoundTag detached = root.getCompound(DETACHED);
        detached.remove(spiritId);
        if (detached.isEmpty()) root.remove(DETACHED); else root.put(DETACHED, detached);
        return true;
    }

    private static CompoundTag transferContract(ItemStack weapon, String spiritId) {
        return spiritId.equals(projection(weapon)) || EmbeddedSpiritApi.isSpirit(weapon, spiritId)
                ? captureContract(weapon) : intrinsicContract(weapon, spiritId);
    }

    /** A legitimately imported narrative identity may initialize after returning to an empty former carrier. */
    public static void allowTransferredSpiritInitialization(ItemStack weapon, String spiritId) {
        if (!weapon.hasTag()) return;
        CompoundTag detached = weapon.getTag().getCompound(DETACHED);
        detached.remove(spiritId);
        if (detached.isEmpty()) weapon.getTag().remove(DETACHED);
        else weapon.getTag().put(DETACHED, detached);
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
        if (!ContractCarrierData.hasMaidData(weapon)
                || ContractCarrierData.hasMaidEntityData(weapon)) {
            return true;
        }
        String maidId = ContractCarrierData.getBoundMaidUUID(weapon);
        if (maidId == null || maidId.isBlank()) return false;
        Entity manifested = InfusedMaidDeploymentSystem.findManifestedMaid(owner, maidId);
        if (manifested == null) return false;
        return InfusedMaidDeploymentSystem.forceRecall(owner, maidId, 0)
                && ContractCarrierData.hasMaidEntityData(weapon);
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
