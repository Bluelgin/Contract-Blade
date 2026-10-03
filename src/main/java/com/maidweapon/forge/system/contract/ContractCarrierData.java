package com.maidweapon.forge.system.contract;

import com.maidweapon.common.data.MaidWeaponData;
import com.maidweapon.common.data.MaidWeaponDataSerializer;
import com.maidweapon.common.sin.SinSlotManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import javax.annotation.Nullable;

/**
 * Contract metadata shared by every carrier, independent of its item class.
 * Existing NBT keys and legacy defaults are intentionally preserved.
 * Entity serialization remains owned by ContractMaidStorage.
 */
public final class ContractCarrierData {
    private static final String NBT_MAID_DATA = "MaidData";
    private static final String NBT_MAID_UUID = "MaidUUID";
    private static final String NBT_BINDING_ID = "MaidBindingId";
    private static final String NBT_SUPERSEDED = "MaidContractSuperseded";
    private static final String NBT_OWNER_UUID = "OwnerUUID";
    private static final String NBT_OWNER_NAME = "OwnerName";

    // ==================== NBT 数据读写 ====================

    public static MaidWeaponData getMaidData(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_MAID_DATA)) {
            return new MaidWeaponData();
        }

        CompoundTag maidTag = tag.getCompound(NBT_MAID_DATA);
        return MaidWeaponDataSerializer.fromValues(
                maidTag.getString(MaidWeaponDataSerializer.KEY_MAID_NAME),
                maidTag.getInt(MaidWeaponDataSerializer.KEY_LEVEL),
                maidTag.getInt(MaidWeaponDataSerializer.KEY_FAVORABILITY),
                maidTag.contains(MaidWeaponDataSerializer.KEY_RESONANCE)
                        ? maidTag.getInt(MaidWeaponDataSerializer.KEY_RESONANCE)
                        : MaidWeaponData.MAX_RESONANCE,
                maidTag.getInt(MaidWeaponDataSerializer.KEY_TOTAL_KILLS),
                maidTag.getInt(MaidWeaponDataSerializer.KEY_UNLOCKED_TIER),
                maidTag.getInt(MaidWeaponDataSerializer.KEY_ENDER_DRAGON_KILLS),
                maidTag.getInt(MaidWeaponDataSerializer.KEY_WITHER_KILLS),
                maidTag.getString(MaidWeaponDataSerializer.KEY_EMBEDDED_SINS)
        );
    }

    public static void setMaidData(ItemStack stack, MaidWeaponData data) {
        CompoundTag maidTag = new CompoundTag();
        maidTag.putString(MaidWeaponDataSerializer.KEY_MAID_NAME, data.getMaidName());
        maidTag.putInt(MaidWeaponDataSerializer.KEY_LEVEL, data.getLevel());
        maidTag.putInt(MaidWeaponDataSerializer.KEY_FAVORABILITY, data.getFavorability());
        maidTag.putInt(MaidWeaponDataSerializer.KEY_RESONANCE, data.getResonance());
        maidTag.putInt(MaidWeaponDataSerializer.KEY_TOTAL_KILLS, data.getTotalKills());
        maidTag.putInt(MaidWeaponDataSerializer.KEY_UNLOCKED_TIER, data.getUnlockedTier());
        maidTag.putInt(MaidWeaponDataSerializer.KEY_ENDER_DRAGON_KILLS, data.getEnderDragonKills());
        maidTag.putInt(MaidWeaponDataSerializer.KEY_WITHER_KILLS, data.getWitherKills());

        maidTag.putString(MaidWeaponDataSerializer.KEY_EMBEDDED_SINS,
                SinSlotManager.sinsToString(data.getEmbeddedSins()));

        CompoundTag rootTag = stack.getOrCreateTag();
        rootTag.put(NBT_MAID_DATA, maidTag);
    }

    public static boolean hasMaidData(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(NBT_MAID_DATA);
    }

    /** 检查武器是否当前捕获了女仆实体（有 MaidEntityData 标签） */
    public static boolean hasMaidEntityData(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return com.maidweapon.forge.system.MaidEntityDataCodec.hasData(tag);
    }

    /** Removes all maid-contract data while preserving the original weapon and its own NBT. */
    public static void clearMaidContract(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return;
        tag.remove(NBT_MAID_DATA);
        com.maidweapon.forge.system.MaidEntityDataCodec.remove(tag);
        tag.remove(NBT_MAID_UUID);
        tag.remove(NBT_BINDING_ID);
        tag.remove(NBT_SUPERSEDED);
        tag.remove(NBT_OWNER_UUID);
        tag.remove(NBT_OWNER_NAME);
        tag.remove("MaidInfusionOriginalTask");
        tag.remove("MaidInfusionOriginalSchedule");
        tag.remove("MaidDeploymentLocation");
        tag.remove("MaidDeploymentRecoveryFailed");
        tag.remove("MaidInfusionTaczTaskFailure");
        tag.remove("MaidInfusionTaczAmmoLinkFailure");
        if (tag.isEmpty()) stack.setTag(null);
    }

    // ==================== 女仆绑定（一武器一女仆） ====================

    /**
     * 设置武器绑定的女仆 UUID（首次捕获时写入，永不删除）
     */
    public static void setBoundMaidUUID(ItemStack stack, String maidUUID) {
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains(NBT_MAID_UUID)) {
            tag.putString(NBT_MAID_UUID, maidUUID);
        }
    }

    /**
     * 获取武器绑定的女仆 UUID
     */
    @Nullable
    public static String getBoundMaidUUID(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_MAID_UUID)) return null;
        return tag.getString(NBT_MAID_UUID);
    }

    /** Unique identity for this concrete weapon/maid contract, separate from the maid UUID. */
    public static String ensureBindingId(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains(NBT_BINDING_ID)) {
            tag.putString(NBT_BINDING_ID, java.util.UUID.randomUUID().toString());
        }
        return tag.getString(NBT_BINDING_ID);
    }

    @Nullable
    public static String getBindingId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_BINDING_ID)) return null;
        return tag.getString(NBT_BINDING_ID);
    }

    public static void setContractSuperseded(ItemStack stack, boolean superseded) {
        stack.getOrCreateTag().putBoolean(NBT_SUPERSEDED, superseded);
    }

    public static boolean isContractSuperseded(ItemStack stack) {
        return stack.getTag() != null && stack.getTag().getBoolean(NBT_SUPERSEDED);
    }

    /** 检查实体 UUID 是否匹配武器绑定的女仆 */
    public static boolean isBoundMaid(ItemStack stack, Entity entity) {
        String boundUUID = getBoundMaidUUID(stack);
        if (boundUUID == null) return true; // 还没绑定，任何人都行
        return boundUUID.equals(entity.getStringUUID());
    }

    // ==================== 武器主人系统 ====================

    /**
     * 设置武器主人（首次捕获女仆时写入）
     */
    public static void setOwner(ItemStack stack, Player player) {
        CompoundTag tag = stack.getOrCreateTag();
        // 只写入一次，已有主人时不再覆盖
        if (!tag.contains(NBT_OWNER_UUID)) {
            tag.putUUID(NBT_OWNER_UUID, player.getUUID());
            tag.putString(NBT_OWNER_NAME, player.getScoreboardName());
        }
    }

    /**
     * 获取武器主人的 UUID
     */
    @Nullable
    public static String getOwnerUUID(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_OWNER_UUID)) return null;
        return tag.getUUID(NBT_OWNER_UUID).toString();
    }

    /**
     * 获取武器主人的名字
     */
    @Nullable
    public static String getOwnerName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_OWNER_NAME)) return null;
        return tag.getString(NBT_OWNER_NAME);
    }

    /**
     * 检查玩家是否为武器主人（或武器无主）
     */
    public static boolean isOwner(ItemStack stack, Player player) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(NBT_OWNER_UUID)) return true; // 无主武器，谁都能用
        return tag.getUUID(NBT_OWNER_UUID).equals(player.getUUID());
    }


    private ContractCarrierData() {}
}
