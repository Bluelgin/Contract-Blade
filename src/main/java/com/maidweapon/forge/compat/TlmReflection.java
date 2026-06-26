package com.maidweapon.forge.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;

/**
 * TLM 反射实现 — 集中管理所有对 TLM 的反射调用。
 *
 * 设计原因：TLM 不是编译期依赖，所有类名/方法名只能通过反射访问。
 * 集中到此类后，TLM 版本升级只需改此处，不影响业务逻辑。
 */
public final class TlmReflection {

    static final TlmReflection INSTANCE = new TlmReflection();

    private boolean tlmLoaded = false;
    private Class<?> maidEntityClass = null;

    // ==================== 反射缓存 ====================

    private Method getFavorabilityMethod = null;
    private Method setFavorabilityMethod = null;

    private TlmReflection() {
        try {
            maidEntityClass = Class.forName("com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid");
            tlmLoaded = true;
            // 预缓存常用方法
            getFavorabilityMethod = findMethod(maidEntityClass, "getFavorability");
            setFavorabilityMethod = findMethod(maidEntityClass, "setFavorability");
        } catch (ClassNotFoundException e) {
            tlmLoaded = false;
        }
    }

    // ==================== 接口实现 ====================

    public boolean isTlmLoaded() {
        return tlmLoaded;
    }

    public boolean isMaidEntity(Entity entity) {
        if (entity == null) return false;
        // 方法1: 类继承检查（优先）
        if (tlmLoaded && maidEntityClass != null && maidEntityClass.isInstance(entity)) {
            return true;
        }
        // 方法2: 注册名匹配
        String entityTypeName = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        if (entityTypeName.contains("touhou_little_maid") && entityTypeName.contains("maid")) {
            return true;
        }
        // 方法3: 类名字符串匹配
        String className = entity.getClass().getName();
        return className.contains("EntityMaid") || className.contains("entityMaid");
    }

    public int getMaidFavorability(Entity maid) {
        if (getFavorabilityMethod == null) return 0;
        try {
            return (int) getFavorabilityMethod.invoke(maid);
        } catch (Exception e) {
            return 0;
        }
    }

    public void setMaidFavorability(Entity maid, int value) {
        if (setFavorabilityMethod == null) return;
        try {
            setFavorabilityMethod.invoke(maid, value);
        } catch (Exception ignored) {}
    }

    public Class<?> getMaidEntityClass() {
        return maidEntityClass;
    }

    // ==================== 反射工具 ====================

    /**
     * 在类本身上查找方法（不寻找父类）
     */
    public static Method findMethod(Class<?> clazz, String name) {
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }
}
