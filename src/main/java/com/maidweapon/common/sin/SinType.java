package com.maidweapon.common.sin;

/**
 * ========================================
 * 七宗罪类型枚举（通用层）
 * ========================================
 *
 * 定义七种罪恶的类型。
 * 每种罪恶有唯一的ID、中文名、英文名。
 */
public enum SinType {

    PRIDE("pride", "紫御守·孤高", "Amulet of Pride", "§5"),        // 紫色
    WRATH("wrath", "红之刻印·崩坏", "Seal of Wrath", "§4"),       // 深红
    SLOTH("sloth", "苍蓝结界·停滞", "Barrier of Sloth", "§9"),    // 蓝色
    GREED("greed", "漆黑契约·索求", "Contract of Greed", "§8"),   // 黑色
    GLUTTONY("gluttony", "白骨之匣·无尽", "Casket of Gluttony", "§f"), // 白色
    LUST("lust", "桃色桃符·倾心", "Amulet of Lust", "§d"),        // 粉色
    ENVY("envy", "蛇纹书页·怨叹", "Page of Envy", "§5");          // 深紫色

    /** 内部ID（用于NBT存储和配置文件） */
    private final String id;

    /** 中文显示名 */
    private final String chineseName;

    /** 英文显示名 */
    private final String englishName;

    /** 颜色代码 */
    private final String color;

    SinType(String id, String chineseName, String englishName, String color) {
        this.id = id;
        this.chineseName = chineseName;
        this.englishName = englishName;
        this.color = color;
    }

    public String getId() { return id; }
    public String getChineseName() { return chineseName; }
    public String getEnglishName() { return englishName; }
    public String getColor() { return color; }

    /**
     * 是否为傲慢（特殊罪恶，允许嵌入所有其他罪恶）
     */
    public boolean isPride() {
        return this == PRIDE;
    }

    /**
     * 根据ID查找枚举
     */
    public static SinType fromId(String id) {
        for (SinType type : values()) {
            if (type.id.equals(id)) return type;
        }
        return null;
    }
}