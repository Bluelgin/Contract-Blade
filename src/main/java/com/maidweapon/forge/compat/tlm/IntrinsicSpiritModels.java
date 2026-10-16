package com.maidweapon.forge.compat.tlm;

import com.maidweapon.forge.compat.CompatDiagnostics;
import com.maidweapon.forge.system.MaidEntityDataCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import static com.maidweapon.forge.system.contract.ContractChannelStorage.validContract;

/** Model changes for stored and live spirits; does not switch contract channels. */
public final class IntrinsicSpiritModels {
    public static boolean setStoredModel(CompoundTag contract, String modelId) {
        return validContract(contract) && MaidEntityDataCodec.update(
                contract, entityData -> entityData.putString("ModelId", modelId));
    }

    public static boolean clearStoredModelIfEquals(
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

    public static boolean clearProjectedModelIfEquals(
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

    public static boolean manifestedUsesModel(
            Entity maid,
            String modelId) {
        try {
            Object current = maid.getClass().getMethod("getModelId").invoke(maid);
            return modelId.equals(current);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            CompatDiagnostics.warnOnce("intrinsic:model-access", failure);
            // Unknown TLM versions must never trigger a speculative recall.
            return false;
        }
    }

    public static void setManifestedModel(Entity maid, String modelId) {
        try {
            Object current = maid.getClass().getMethod("getModelId").invoke(maid);
            if (modelId.equals(current)) return;
            maid.getClass().getMethod("setModelId", String.class)
                    .invoke(maid, modelId);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            CompatDiagnostics.warnOnce("intrinsic:model-access", failure);
            // Stored NBT remains authoritative and applies on the next deploy.
        }
    }


    /** One-time default/migration; provider synchronization never forces the player's skin back. */
    public static void applyPresetModel(net.minecraft.world.entity.player.Player owner, net.minecraft.world.item.ItemStack weapon, String spiritId) {
        String model = PresetSpiritModels.modelFor(spiritId);
        CompoundTag entry = weapon.hasTag() ? weapon.getTag().getCompound("MaidWeaponIntrinsicSpirits").getCompound(spiritId) : new CompoundTag();
        if (model.isEmpty() || model.equals(entry.getString("DefaultModelApplied"))
                || !PresetSpiritModels.isAvailable(model)) return;
        CompoundTag candidate = weapon.getTag().copy();
        CompoundTag spirits = candidate.getCompound("MaidWeaponIntrinsicSpirits");
        entry = spirits.getCompound(spiritId);
        Entity manifested = null;
        boolean applied;
        if (spiritId.equals(weapon.getTag().getString("MaidWeaponIntrinsicProjection"))) {
            if (MaidEntityDataCodec.hasData(candidate)) {
                applied = PresetSpiritModels.applyDefault(candidate, model);
            } else {
                String maidId = com.maidweapon.forge.system.contract.ContractCarrierData.getBoundMaidUUID(weapon);
                manifested = maidId == null || maidId.isBlank() ? null
                        : com.maidweapon.forge.system.InfusedMaidDeploymentSystem.findManifestedMaid(owner, maidId);
                applied = manifested != null;
            }
        } else {
            applied = PresetSpiritModels.applyDefault(entry.getCompound("Contract"), model);
        }
        if (!applied) return;
        entry.putString("DefaultModelApplied", model);
        spirits.put(spiritId, entry);
        candidate.put("MaidWeaponIntrinsicSpirits", spirits);
        try {
            MaidEntityDataCodec.validateContainer(candidate);
        } catch (java.io.IOException rejected) {
            return; // Keep the complete original contract, including a live maid, untouched.
        }
        if (manifested != null && !PresetSpiritModels.applyDefault(manifested, model)) return;
        weapon.setTag(candidate);
    }

    private IntrinsicSpiritModels() {}
}
