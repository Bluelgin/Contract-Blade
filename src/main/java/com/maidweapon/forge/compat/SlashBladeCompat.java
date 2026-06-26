package com.maidweapon.forge.compat;

/**
 * ========================================
 * 拔刀剑（SlashBlade）兼容层 — 桩代码
 * ========================================
 *
 * 待拔刀剑模型准备就绪后，按以下步骤集成：
 *   1. 将拔刀剑 jar 放入 libs/
 *   2. 在 build.gradle 添加依赖
 *   3. 创建 MaidSlashBladeItem extends ItemSlashBlade
 *   4. 创建 MaidSlashBladeProvider（提供 ISlashBladeState Capability）
 *   5. 在 ModItems 中注册
 *
 * 完整参考代码见历史记录中的 SlashBladeCompat.java 旧版本。
 *
 * 拔刀剑信息：
 *   主类：mods.flammpfeil.slashblade.SlashBlade
 *   MODID：slashblade
 *   核心物品：mods.flammpfeil.slashblade.item.ItemSlashBlade
 *   状态 Capability：ItemSlashBlade.BLADESTATE
 */
public final class SlashBladeCompat {

    private static boolean loaded = false;

    static {
        try {
            Class.forName("mods.flammpfeil.slashblade.SlashBlade");
            loaded = true;
        } catch (ClassNotFoundException e) {
            loaded = false;
        }
    }

    public static boolean isLoaded() { return loaded; }

    /** 将 MaidWeaponData 同步到拔刀剑状态 */
    public static void syncMaidToSlashBlade(Object stack) {
        if (!loaded) return;
        // TODO: 集成后通过 Capability 同步
    }

    /** 将拔刀剑状态同步回 MaidWeaponData */
    public static void syncSlashBladeToMaid(Object stack) {
        if (!loaded) return;
        // TODO: 集成后通过 Capability 同步
    }

    private SlashBladeCompat() {}
}
