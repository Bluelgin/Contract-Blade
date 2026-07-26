package com.maidweapon.common;

/**
 * ========================================
 * MaidWeapon 常量定义（通用层）
 * ========================================
 *
 * 本类不依赖任何 Forge/Fabric API，属于「common」通用层。
 * 无论在 Forge 端还是 Fabric 端都可以安全引用。
 *
 * 项目架构说明：
 *   common/  → 纯游戏逻辑（等级系统、适应性系统、数据结构等），不引用任何加载器API
 *   forge/   → Forge 特定代码（事件总线、注册、Capability 等）
 *   fabric/  → Fabric 特定代码（未来移植时使用）
 */
public final class MaidWeaponConstants {

    /** Mod 的唯一标识符，必须与 mods.toml / fabric.mod.json 中的 modId 一致 */
    public static final String MOD_ID = "maid_weapon";

    /** Mod 名称 */
    public static final String MOD_NAME = "车万女仆：契约之刃";

    /** Mod 版本 */
    public static final String MOD_VERSION = "0.1.0-beta.1";

    /** 拔刀剑模式 NBT 标签 */
    public static final String TAG_SLASHBLADE_MODE = "SlashBladeMode";

    // 私有构造器，防止实例化
    private MaidWeaponConstants() {}
}
