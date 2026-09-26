package com.maidweapon.forge.compat.tlm;

import com.maidweapon.forge.compat.TlmReflection;
import com.maidweapon.forge.system.MaidEntityDataCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.io.IOException;

/**
 * Narrow adapter around TLM entity/task/appearance APIs.
 *
 * <p>No contract gameplay policy belongs here; callers get version-tolerant
 * entity operations only.</p>
 */
public final class TlmEntityAdapter {
    private static final TlmReflection TLM = TlmReflection.INSTANCE;

    public static boolean isMaidEntity(Entity entity) {
        return TLM.isMaidEntity(entity);
    }

    public static Class<?> maidClass() {
        return TLM.getMaidEntityClass();
    }

    public static int favorability(Entity maid) {
        return TLM.getMaidFavorability(maid);
    }

    public static void setFavorability(Entity maid, int value) {
        TLM.setMaidFavorability(maid, value);
    }

    public static boolean isOwnedByPlayer(Entity entity, Player player) {
        if (!(entity instanceof TamableAnimal tamable)) return false;
        java.util.UUID ownerUUID = tamable.getOwnerUUID();
        return ownerUUID != null && ownerUUID.equals(player.getUUID());
    }

    public static boolean isOwnedMaid(Entity entity, Player player) {
        return isMaidEntity(entity) && isOwnedByPlayer(entity, player);
    }

    public static CompoundTag captureAppearance(Entity entity) {
        return TLM.captureAppearance(entity);
    }

    public static CompoundTag captureStoredAppearance(ItemStack stack) {
        CompoundTag root = stack.getTag();
        if (root == null) return new CompoundTag();
        if (MaidEntityDataCodec.hasData(root)) {
            try {
                return TLM.extractAppearance(MaidEntityDataCodec.read(root));
            } catch (IOException ignored) {
                return new CompoundTag();
            }
        }
        if (root.contains("MaidInfo")) {
            return TLM.extractAppearance(root.getCompound("MaidInfo"));
        }
        return new CompoundTag();
    }

    public static Entity createAppearanceProxy(Level level, CompoundTag appearance) {
        return TLM.createAppearanceProxy(level, appearance);
    }

    public static String taskId(Entity entity) {
        return TLM.getTaskId(entity);
    }

    public static boolean switchTask(Entity entity, String taskId) {
        return TLM.switchTask(entity, taskId);
    }

    public static boolean playIdleVoice(Entity entity) {
        return TLM.playTaskVoice(entity, "touhou_little_maid:idle");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean setAllDaySchedule(Entity entity) {
        try {
            Class<?> schedule = Class.forName(
                    "com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidSchedule");
            Object all = Enum.valueOf((Class<? extends Enum>) schedule.asSubclass(Enum.class), "ALL");
            entity.getClass().getMethod("setSchedule", schedule).invoke(entity, all);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    public static CompoundTag save(Entity entity) {
        CompoundTag tag = new CompoundTag();
        entity.saveWithoutId(tag);
        sanitize(tag);
        return tag;
    }

    public static void load(Entity entity, CompoundTag tag) {
        entity.load(tag);
    }

    public static void tame(Entity maid, Player player) {
        if (maid instanceof TamableAnimal tamable) tamable.tame(player);
    }

    private static void sanitize(CompoundTag tag) {
        tag.remove("Pos");
        tag.remove("Motion");
        tag.remove("Rotation");
        tag.remove("FallDistance");
        tag.remove("Fire");
        tag.remove("Air");
        tag.remove("OnGround");
        tag.remove("PortalCooldown");
    }

    private TlmEntityAdapter() {}
}
