package com.maidweapon.forge.compat.tlm;

import com.maidweapon.forge.compat.CompatDiagnostics;
import com.maidweapon.forge.compat.fox.FoxModelPackBootstrap;
import com.maidweapon.forge.system.MaidEntityDataCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

/** Defaults for explicitly identified addon spirits, never ordinary named weapons. */
public final class PresetSpiritModels {
    public static final String AKATSUKI = "blade_tetra:akatsuki";

    public static String modelFor(String spiritId) {
        return AKATSUKI.equals(spiritId) ? AkatsukiModelPackBootstrap.MODEL : "";
    }

    public static boolean isAvailable(String modelId) {
        return !modelId.isEmpty() && FoxModelPackBootstrap.isRegistered(modelId);
    }

    private static boolean isDefault(String modelId) {
        // Native TLM's initial appearance and BladeTetra's withdrawn legacy assignment.
        return modelId.isEmpty() || "touhou_little_maid:hakurei_reimu".equals(modelId)
                || "maid_weapon:akatsuki".equals(modelId);
    }

    public static void initialize(Entity maid, String spiritId) {
        String model = modelFor(spiritId);
        if (isAvailable(model)) applyDefault(maid, model);
    }

    /** Returns true for a custom skin too: it has been considered and must be preserved. */
    public static boolean applyDefault(CompoundTag contract, String modelId) {
        try {
            CompoundTag data = MaidEntityDataCodec.read(contract);
            if (data == null) return false;
            if (data.getBoolean("IsYsmModel") || !isDefault(data.getString("ModelId"))) return true;
            data.putString("ModelId", modelId);
            MaidEntityDataCodec.write(contract, data);
            return true;
        } catch (java.io.IOException | RuntimeException failure) {
            CompatDiagnostics.warnOnce("intrinsic:preset-model-data", failure);
            return false;
        }
    }

    /** No recall/redeploy: an already manifested legacy spirit keeps its native entity identity. */
    public static boolean applyDefault(Entity maid, String modelId) {
        try {
            if ((Boolean) maid.getClass().getMethod("isYsmModel").invoke(maid)) return true;
            String current = (String) maid.getClass().getMethod("getModelId").invoke(maid);
            if (!isDefault(current)) return true;
            maid.getClass().getMethod("setModelId", String.class).invoke(maid, modelId);
            return modelId.equals(maid.getClass().getMethod("getModelId").invoke(maid));
        } catch (ReflectiveOperationException | RuntimeException failure) {
            CompatDiagnostics.warnOnce("intrinsic:preset-model-live", failure);
            return false;
        }
    }

    private PresetSpiritModels() { }
}
