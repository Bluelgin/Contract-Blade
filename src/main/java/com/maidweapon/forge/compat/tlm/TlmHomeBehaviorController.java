package com.maidweapon.forge.compat.tlm;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
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
    private static final Activity HOME = new Activity("contract_blade_home");

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean begin(Mob maid) {
        if (!TlmEntityAdapter.isMaidEntity(maid)) return false;
        try {
            // A persisted scope after a crash must first restore its original settings.
            restore(maid);
            if (maid.getPersistentData().contains(BACKUP)) return false;
            Object positions = maid.getClass().getMethod("getSchedulePos").invoke(maid);
            CompoundTag backup = new CompoundTag();
            positions.getClass().getMethod("save", CompoundTag.class).invoke(positions, backup);
            backup.putBoolean("HomeMode", (boolean) maid.getClass().getMethod("isHomeModeEnable").invoke(maid));
            backup.putString("Task", TlmEntityAdapter.taskId(maid));
            backup.putBoolean("Sitting", maid instanceof TamableAnimal tame && tame.isOrderedToSit());
            maid.getPersistentData().put(BACKUP, backup);
            if (!TlmEntityAdapter.switchTask(maid, "touhou_little_maid:idle")) {
                restore(maid);
                return false;
            }
            maid.getClass().getMethod("setHomeModeEnable", boolean.class).invoke(maid, true);
            if (maid instanceof TamableAnimal tame) { tame.setInSittingPose(false); tame.setOrderedToSit(false); }
            center(maid, maid.blockPosition());
            Brain brain = maid.getBrain();
            brain.stopAll((ServerLevel) maid.level(), maid);
            clearWalk(maid);
            // Excludes TLM's world-time schedule, spontaneous furniture searches and follow teleports.
            // Navigation, looking and swimming still execute as normal Minecraft behaviors.
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
        } catch (ReflectiveOperationException | RuntimeException unsupported) {
            if (Boolean.getBoolean("contractblade.home.validation")) unsupported.printStackTrace();
            restore(maid);
            return false;
        }
    }
    public static void center(Mob maid, BlockPos pos) throws ReflectiveOperationException {
        Object positions = maid.getClass().getMethod("getSchedulePos").invoke(maid);
        for (String method : new String[]{"setWorkPos", "setIdlePos", "setSleepPos"})
            positions.getClass().getMethod(method, BlockPos.class).invoke(positions, pos);
        positions.getClass().getMethod("setDimension", net.minecraft.resources.ResourceLocation.class)
                .invoke(positions, maid.level().dimension().location());
        positions.getClass().getMethod("setConfigured", boolean.class).invoke(positions, true);
    }
    public static void clearWalk(Mob maid) {
        maid.getNavigation().stop();
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        maid.getBrain().eraseMemory(MemoryModuleType.PATH);
    }
    public static void restore(Mob maid) {
        if (maid.isRemoved()) return;
        CompoundTag backup = maid.getPersistentData().getCompound(BACKUP);
        if (backup.isEmpty()) return;
        if (maid.isSleeping()) maid.stopSleeping();
        if (maid.isPassenger()) maid.stopRiding();
        clearWalk(maid);
        try {
            maid.getClass().getMethod("setHomeModeEnable", boolean.class).invoke(maid, backup.getBoolean("HomeMode"));
            Object positions = maid.getClass().getMethod("getSchedulePos").invoke(maid);
            positions.getClass().getMethod("load", CompoundTag.class, TlmEntityAdapter.maidClass())
                    .invoke(positions, backup, maid);
            TlmEntityAdapter.switchTask(maid, backup.getString("Task"));
            if (maid instanceof TamableAnimal tame) {
                tame.setInSittingPose(backup.getBoolean("Sitting"));
                tame.setOrderedToSit(backup.getBoolean("Sitting"));
            }
            maid.getClass().getMethod("refreshBrain", ServerLevel.class).invoke(maid, (ServerLevel) maid.level());
            maid.getPersistentData().remove(BACKUP);
        } catch (ReflectiveOperationException | RuntimeException unsupported) {
            // Keep the small backup for retry, never erase data on a failed restore.
        }
    }
    private TlmHomeBehaviorController() {}
}
