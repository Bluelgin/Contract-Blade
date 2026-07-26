package com.maidweapon.forge.compat;

import net.minecraftforge.fml.ModList;

/**
 * ========================================
 * 万法皆通（WanFaJieTong / TLM 扩展）兼容层
 * ========================================
 *
 * 万法皆通是车万女仆（TLM）的附属Mod，为女仆添加魔法系统。
 *
 * 本兼容层预留的功能：
 *   1. 检测万法皆通是否安装
 *   2. 法杖女仆物品的魔法加成
 *   3. 与铁魔法+Goety的三联动特殊物品
 *
 * 万法皆通 Mod 需要与以下 Mod 配合：
 *   - Touhou Little Maid（车万女仆）
 *   - Iron's Spells 'n Spellbooks（铁魔法）
 *   - Goety（哥特魔法）
 *
 * 三者同时安装时解锁「法杖女仆」特殊物品
 */
public final class WanFaJieTongCompat {
    private static final String MOD_ID = "touhou_little_maid_spell";

    /** 检查万法皆通是否已安装 */
    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /**
     * 检测三联动条件：万法皆通 + 铁魔法 + Goety 同时安装
     * 满足时解锁「法杖女仆」特殊物品
     */
    public static boolean isTripleComboAvailable() {
        return TripleMagicCompat.active();
    }

    /**
     * 检测双联动条件：万法皆通 + 铁魔法
     */
    public static boolean isDoubleComboWithIronSpells() {
        return isLoaded() && IronsSpellsCompat.isLoaded();
    }

    /**
     * 检测双联动条件：万法皆通 + Goety
     */
    public static boolean isDoubleComboWithGoety() {
        return isLoaded() && GoetyCompat.isLoaded();
    }

    /**
     * 创建法杖女仆武器数据
     * 只有在三联动条件满足时才能创建
     *
     * @return 法杖女仆物品的属性数据，如果不满足条件返回null
     */
    public static Object createWandMaidData() {
        if (!isTripleComboAvailable()) return null;
        // TODO: 创建法杖女仆数据
        return null;
    }

    /**
     * 获取法杖女仆的法术加成倍率
     * 基于女仆等级和忠诚度
     */
    public static float getWandMaidSpellMultiplier(int maidLevel, double loyalty) {
        if (!isLoaded()) return 1.0f;
        // 基础倍率 = 1.0 + (等级 - 1) * 0.1
        float base = 1.0f + (maidLevel - 1) * 0.1f;
        // 忠诚度加成
        if (loyalty >= 80) base *= 1.2f;
        else if (loyalty >= 50) base *= 1.0f;
        else base *= 0.8f;
        return base;
    }

    /**
     * 注册万法皆通中的特殊怪物到Tier系统
     *
     * 示例（集成后替换）：
     *   ModCompatManager.registerMonsterTier("wanfa:magic_maid_boss", 4);
     */
    public static void registerMonsters() {
        if (!isLoaded()) return;
        // TODO: 注册万法皆通怪物
    }

    private WanFaJieTongCompat() {}
}
