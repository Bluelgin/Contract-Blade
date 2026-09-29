package com.maidweapon.forge.compat.tlm;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.LookAtTargetSink;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.behavior.Swim;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import java.util.Set;

/** Temporary behavior scope on the SAME maid/brain; refreshBrain restores TLM on release.
 * Only settings are saved for crash recovery, never entity/inventory NBT or a maid copy.
 */
public final class TlmHomeBehaviorController {
    private static final String BACKUP = "ContractHomeBehaviorSettings";
    private static final String BOARD_GAME_MODE = "ContractHomeBoardGame";
    private static final String MANAGED_SEAT = "ContractHomeManagedSeat";
    private static final Activity HOME = new Activity("contract_blade_home");

    public static boolean begin(Mob maid) {
        if (!TlmEntityAdapter.isMaidEntity(maid)) return false;
        try {
            if (!restore(maid)) return false;
            if (maid.getPersistentData().contains(BACKUP)) return false;
            Object positions = maid.getClass().getMethod("getSchedulePos").invoke(maid);
            CompoundTag backup = new CompoundTag();
            positions.getClass().getMethod("save", CompoundTag.class).invoke(positions, backup);
            backup.putBoolean("HomeMode", (boolean) maid.getClass().getMethod("isHomeModeEnable").invoke(maid));
            backup.putString("Task", TlmEntityAdapter.taskId(maid));
            Object schedule = maid.getClass().getMethod("getSchedule").invoke(maid);
            if (schedule instanceof Enum<?> value) backup.putString("Schedule", value.name());
            backup.putBoolean("Sitting", maid instanceof TamableAnimal tame && tame.isOrderedToSit());
            maid.getPersistentData().put(BACKUP, backup);
            if (!TlmEntityAdapter.switchTask(maid, "touhou_little_maid:idle")) {
                restore(maid);
                return false;
            }
            maid.getClass().getMethod("setHomeModeEnable", boolean.class).invoke(maid, true);
            if (maid instanceof TamableAnimal tame) {
                tame.setInSittingPose(false);
                tame.setOrderedToSit(false);
            }
            center(maid, maid.blockPosition());
            return installHomeBrain(maid);
        } catch (ReflectiveOperationException | RuntimeException unsupported) {
            if (Boolean.getBoolean("contractblade.home.validation")) unsupported.printStackTrace();
            restore(maid);
            return false;
        }
    }

    public static boolean beginBoardGame(Mob maid) {
        if (!maid.getPersistentData().contains(BACKUP)) return false;
        try {
            if (!setSchedule(maid, "ALL")
                    || !TlmEntityAdapter.switchTask(maid, "touhou_little_maid:board_games")) {
                restoreHomeScope(maid);
                return false;
            }
            if (!installHomeBrain(maid)) {
                restoreHomeScope(maid);
                return false;
            }
            maid.getPersistentData().putBoolean(BOARD_GAME_MODE, true);
            return true;
        } catch (ReflectiveOperationException | RuntimeException unsupported) {
            if (Boolean.getBoolean("contractblade.home.validation")) unsupported.printStackTrace();
            restoreHomeScope(maid);
            return false;
        }
    }

    public static boolean endBoardGame(Mob maid) {
        if (!maid.getPersistentData().getBoolean(BOARD_GAME_MODE)) return true;
        boolean restored = restoreHomeScope(maid);
        if (restored) maid.getPersistentData().remove(BOARD_GAME_MODE);
        return restored;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean setSchedule(Mob maid, String name) throws ReflectiveOperationException {
        Object current = maid.getClass().getMethod("getSchedule").invoke(maid);
        if (!(current instanceof Enum<?>)) return false;
        Class scheduleClass = current.getClass();
        Object value = Enum.valueOf((Class) scheduleClass.asSubclass(Enum.class), name);
        maid.getClass().getMethod("setSchedule", scheduleClass).invoke(maid, value);
        return true;
    }

    private static boolean restoreHomeScope(Mob maid) {
        CompoundTag backup = maid.getPersistentData().getCompound(BACKUP);
        if (backup.isEmpty() || maid.isRemoved()) return false;
        try {
            if (backup.contains("Schedule") && !setSchedule(maid, backup.getString("Schedule"))) return false;
            if (!TlmEntityAdapter.switchTask(maid, "touhou_little_maid:idle")) return false;
            return installHomeBrain(maid);
        } catch (ReflectiveOperationException | RuntimeException unsupported) {
            if (Boolean.getBoolean("contractblade.home.validation")) unsupported.printStackTrace();
            return false;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean installHomeBrain(Mob maid) throws ReflectiveOperationException {
        Brain brain = maid.getBrain();
        brain.stopAll((ServerLevel) maid.level(), maid);
        clearWalk(maid);
        BehaviorControl doors = (BehaviorControl) Class.forName(
                "com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidInteractWithDoor")
                .getMethod("create").invoke(null);
        brain.addActivity(HOME, ImmutableList.of(Pair.of(0, new Swim(0.8f)),
                Pair.of(1, new LookAtTargetSink(45, 90)), Pair.of(2, new MoveToTargetSink()),
                Pair.of(3, doors)));
        brain.setCoreActivities(Set.of(HOME));
        brain.setDefaultActivity(HOME);
        brain.setActiveActivityIfPossible(HOME);
        return true;
    }

    public static void center(Mob maid, BlockPos pos) throws ReflectiveOperationException {
        Object positions = maid.getClass().getMethod("getSchedulePos").invoke(maid);
        for (String method : new String[]{"setWorkPos", "setIdlePos", "setSleepPos"})
            positions.getClass().getMethod(method, BlockPos.class).invoke(positions, pos);
        positions.getClass().getMethod("setDimension", net.minecraft.resources.ResourceLocation.class)
                .invoke(positions, maid.level().dimension().location());
        positions.getClass().getMethod("setConfigured", boolean.class).invoke(positions, true);
    }

    public static void holdManagedSeat(Mob maid, Entity seat) {
        if (seat != null) maid.getPersistentData().putUUID(MANAGED_SEAT, seat.getUUID());
    }

    public static boolean shouldKeepManagedSeat(Mob maid, Entity seat) {
        return seat != null
                && maid.level().dimension().equals(
                        com.maidweapon.forge.system.interior.ContractInteriorService.INTERIOR_LEVEL)
                && maid.getPersistentData().hasUUID(MANAGED_SEAT)
                && maid.getPersistentData().getUUID(MANAGED_SEAT).equals(seat.getUUID());
    }

    public static void releaseManagedSeat(Mob maid) {
        maid.getPersistentData().remove(MANAGED_SEAT);
    }

    public static void clearWalk(Mob maid) {
        maid.getNavigation().stop();
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
    }

    public static boolean restore(Mob maid) {
        if (maid.isRemoved()) return false;
        CompoundTag backup = maid.getPersistentData().getCompound(BACKUP);
        if (backup.isEmpty()) {
            maid.getPersistentData().remove(BOARD_GAME_MODE);
            maid.getPersistentData().remove(MANAGED_SEAT);
            return true;
        }
        maid.getPersistentData().remove(MANAGED_SEAT);
        if (maid.isSleeping()) maid.stopSleeping();
        if (maid.isPassenger()) maid.stopRiding();
        clearWalk(maid);
        try {
            if (backup.contains("Schedule") && !setSchedule(maid, backup.getString("Schedule"))) return false;
            maid.getClass().getMethod("setHomeModeEnable", boolean.class)
                    .invoke(maid, backup.getBoolean("HomeMode"));
            Object positions = maid.getClass().getMethod("getSchedulePos").invoke(maid);
            positions.getClass().getMethod("load", CompoundTag.class, TlmEntityAdapter.maidClass())
                    .invoke(positions, backup, maid);
            if (!TlmEntityAdapter.switchTask(maid, backup.getString("Task"))) return false;
            if (maid instanceof TamableAnimal tame) {
                tame.setInSittingPose(backup.getBoolean("Sitting"));
                tame.setOrderedToSit(backup.getBoolean("Sitting"));
            }
            maid.getClass().getMethod("refreshBrain", ServerLevel.class)
                    .invoke(maid, (ServerLevel) maid.level());
            maid.getPersistentData().remove(BOARD_GAME_MODE);
            maid.getPersistentData().remove(MANAGED_SEAT);
            maid.getPersistentData().remove(BACKUP);
            return true;
        } catch (ReflectiveOperationException | RuntimeException unsupported) {
            if (Boolean.getBoolean("contractblade.home.validation")) unsupported.printStackTrace();
            return false;
        }
    }

    private TlmHomeBehaviorController() {}
}
