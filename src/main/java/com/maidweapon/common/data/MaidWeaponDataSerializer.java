package com.maidweapon.common.data;

/**
 * ========================================
 * 女仆武器数据序列化工具（通用层）
 * ========================================
 *
 * 提供纯数据层面的序列化/反序列化方法。
 * 不依赖任何 Forge/Fabric API。
 */
public final class MaidWeaponDataSerializer {

    // NBT 键名常量
    public static final String KEY_MAID_NAME = "MaidName";
    public static final String KEY_LEVEL = "Level";
    public static final String KEY_FAVORABILITY = "Favorability";
    public static final String KEY_TOTAL_KILLS = "TotalKills";
    public static final String KEY_UNLOCKED_TIER = "UnlockedTier";
    public static final String KEY_ENDER_DRAGON_KILLS = "EnderDragonKills";
    public static final String KEY_WITHER_KILLS = "WitherKills";
    public static final String KEY_EMBEDDED_SINS = "EmbeddedSins";

    private MaidWeaponDataSerializer() {}

    /**
     * 从平面数据恢复 MaidWeaponData
     */
    public static MaidWeaponData fromValues(String name, int level, int favorability,
                                             int totalKills, int unlockedTier,
                                             int enderDragonKills, int witherKills,
                                             String embeddedSins) {
        MaidWeaponData data = new MaidWeaponData(name);
        data.setLevel(level);
        data.setFavorability(favorability);
        data.setTotalKills(totalKills);
        data.setUnlockedTier(unlockedTier);
        data.setEnderDragonKills(enderDragonKills);
        data.setWitherKills(witherKills);
        data.setEmbeddedSins(com.maidweapon.common.sin.SinSlotManager.sinsFromString(embeddedSins));
        return data;
    }
}
