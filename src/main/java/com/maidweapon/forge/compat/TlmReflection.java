package com.maidweapon.forge.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.nbt.CompoundTag;

import java.lang.reflect.Method;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;

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
    private Method getTaskMethod = null;
    private Method setTaskMethod = null;
    private Method refreshBrainMethod = null;
    private Method getModelIdMethod = null;

    private TlmReflection() {
        try {
            maidEntityClass = Class.forName("com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid");
            tlmLoaded = true;
            // 预缓存常用方法
            getFavorabilityMethod = findMethod(maidEntityClass, "getFavorability");
            setFavorabilityMethod = findMethod(maidEntityClass, "setFavorability");
            getTaskMethod = findMethod(maidEntityClass, "getTask");
            setTaskMethod = findMethod(maidEntityClass, "setTask");
            refreshBrainMethod = findMethod(maidEntityClass, "refreshBrain");
            getModelIdMethod = findMethod(maidEntityClass, "getModelId");
        } catch (ClassNotFoundException e) {
            tlmLoaded = false;
        }
    }

    // ==================== 接口实现 ====================

    public boolean isTlmLoaded() {
        return tlmLoaded;
    }

    public static String diagnostics() {
        TlmReflection tlm = INSTANCE;
        return "maid=" + (tlm.tlmLoaded && tlm.maidEntityClass != null)
                + ", favorability=" + (tlm.getFavorabilityMethod != null && tlm.setFavorabilityMethod != null)
                + ", tasks=" + (tlm.getTaskMethod != null && tlm.setTaskMethod != null)
                + ", refreshBrain=" + (tlm.refreshBrainMethod != null);
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

    /** Returns a strict visual-only snapshot. No owner, inventory, AI or combat data is copied. */
    public CompoundTag captureAppearance(Entity maid) {
        if (!isMaidEntity(maid)) return new CompoundTag();
        CompoundTag full = new CompoundTag();
        maid.saveWithoutId(full);
        CompoundTag appearance = extractAppearance(full);
        if (!appearance.contains("ModelId") && getModelIdMethod != null) {
            try {
                Object id = getModelIdMethod.invoke(maid);
                if (id instanceof String modelId && !modelId.isBlank()) {
                    appearance.putString("ModelId", modelId);
                }
            } catch (ReflectiveOperationException ignored) { }
        }
        appearance.putInt("MaidFavorability", getMaidFavorability(maid));
        return appearance;
    }

    /** Extracts only stable TLM appearance keys from stored maid NBT. */
    public CompoundTag extractAppearance(CompoundTag source) {
        CompoundTag appearance = new CompoundTag();
        copyString(source, appearance, "ModelId");
        if (source.contains("IsYsmModel")) appearance.putBoolean("IsYsmModel", source.getBoolean("IsYsmModel"));
        copyString(source, appearance, "YsmModelId");
        copyString(source, appearance, "YsmModelTexture");
        copyString(source, appearance, "YsmModelName");
        if (source.contains("MaidFavorability")) {
            appearance.putInt("MaidFavorability", source.getInt("MaidFavorability"));
        }
        return appearance;
    }

    /** Creates an unspawned client visual proxy through reflection for old-TLM tolerance. */
    public Entity createAppearanceProxy(Level level, CompoundTag appearance) {
        if (maidEntityClass == null) return null;
        try {
            Entity maid = (Entity) maidEntityClass.getConstructor(Level.class).newInstance(level);
            String modelId = appearance.getString("ModelId");
            Method setModel = findMethod(maidEntityClass, "setModelId");
            if (!modelId.isBlank() && setModel != null) setModel.invoke(maid, modelId);
            if (appearance.getBoolean("IsYsmModel")) {
                Method setYsm = findMethod(maidEntityClass, "setYsmModel");
                if (setYsm != null) {
                    setYsm.invoke(maid, appearance.getString("YsmModelId"),
                            appearance.getString("YsmModelTexture"),
                            Component.literal(appearance.getString("YsmModelName")));
                }
            }
            return maid;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static void copyString(CompoundTag source, CompoundTag destination, String key) {
        if (source.contains(key)) destination.putString(key, source.getString(key));
    }

    public String getTaskId(Entity maid) {
        if (getTaskMethod == null) return "touhou_little_maid:idle";
        try {
            Object task = getTaskMethod.invoke(maid);
            Method getUid = task.getClass().getMethod("getUid");
            return getUid.invoke(task).toString();
        } catch (Exception ignored) {
            return "touhou_little_maid:idle";
        }
    }

    public boolean switchTask(Entity maid, String taskId) {
        if (setTaskMethod == null) return false;
        try {
            Class<?> manager = Class.forName(
                    "com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager");
            Method findTask = manager.getMethod("findTask", ResourceLocation.class);
            Object result = findTask.invoke(null, new ResourceLocation(taskId));
            if (!(result instanceof Optional<?> optional) || optional.isEmpty()) return false;
            setTaskMethod.invoke(maid, optional.get());
            if (refreshBrainMethod != null && maid.level() instanceof ServerLevel serverLevel) {
                refreshBrainMethod.invoke(maid, serverLevel);
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
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
