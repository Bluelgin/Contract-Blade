package com.maidweapon.forge.compat.tlm;

import com.maidweapon.forge.system.interior.home.ActivityTarget;
import com.maidweapon.forge.system.interior.home.ContractHomeActivity;
import com.maidweapon.forge.system.interior.home.ContractHomeFurnitureAdapter;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** TLM bookshelf/computer/keyboard using real EntitySit and native animations. */
public final class TlmHomeJoyAdapter implements ContractHomeFurnitureAdapter {
    private static final ResourceLocation BOOKSHELF = new ResourceLocation("touhou_little_maid", "bookshelf");
    private static final ResourceLocation COMPUTER = new ResourceLocation("touhou_little_maid", "computer");
    private static final ResourceLocation KEYBOARD = new ResourceLocation("touhou_little_maid", "keyboard");
    private static final Map<ResourceLocation, ContractHomeActivity> ACTIVITIES = Map.of(
            BOOKSHELF, ContractHomeActivity.READ,
            COMPUTER, ContractHomeActivity.PLAY,
            KEYBOARD, ContractHomeActivity.PLAY);
    private static final String SIT = "com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit";

    @Override
    public Optional<ActivityTarget> blockTarget(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return Optional.empty();
        BlockState state = level.getBlockState(pos);
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        ContractHomeActivity activity = ACTIVITIES.get(id);
        if (activity == null || level.getBlockEntity(pos) == null) return Optional.empty();
        Entity sit = sitEntity(level, pos);
        return Optional.of(new ActivityTarget(pos.immutable(), activity, id.toString(),
                null, 2, sit != null && sit.isAlive()));
    }

    @Override public Optional<ActivityTarget> entityTarget(Entity entity) { return Optional.empty(); }

    @Override
    public boolean valid(ServerLevel level, ActivityTarget target, Mob maid) {
        var fresh = blockTarget(level, target.position());
        if (fresh.isEmpty() || !fresh.get().key().equals(target.key())) return false;
        if (matchesSeat(maid.getVehicle(), target.position())) return true;
        Entity sit = sitEntity(level, target.position());
        return sit == null || !sit.isAlive() || sit.hasPassenger(maid);
    }

    @Override
    public boolean start(ServerLevel level, ActivityTarget target, Mob maid) {
        if (!valid(level, target, maid)
                || target.position().distToCenterSqr(maid.position()) > 4.0) return false;
        try {
            BlockState state = level.getBlockState(target.position());
            Object block = state.getBlock();
            invokeStart(block, maid, state, level, target.position());

            Entity sit = maid.getVehicle();
            if (!matchesSeat(sit, target.position())) {
                sit = sitEntity(level, target.position());
                if (matchesSeat(sit, target.position()) && sit.isAlive()
                        && sit.getPassengers().isEmpty()) maid.startRiding(sit, true);
            }
            if (!matchesSeat(maid.getVehicle(), target.position())) {
                sit = createNativeSeat(level, target.position(), state, block);
                if (sit != null) maid.startRiding(sit, true);
            }

            sit = maid.getVehicle();
            if (!matchesSeat(sit, target.position()) || !sit.hasPassenger(maid)) return false;
            TlmHomeBehaviorController.holdManagedSeat(maid, sit);
            return true;
        } catch (ReflectiveOperationException | RuntimeException unsupported) {
            if (Boolean.getBoolean("contractblade.home.validation")) unsupported.printStackTrace();
            TlmHomeBehaviorController.releaseManagedSeat(maid);
            return false;
        }
    }

    @Override
    public boolean running(ServerLevel level, ActivityTarget target, Mob maid) {
        if (!valid(level, target, maid)) return false;
        Entity sit = maid.getVehicle();
        return matchesSeat(sit, target.position()) && sit.hasPassenger(maid)
                && TlmHomeBehaviorController.shouldKeepManagedSeat(maid, sit);
    }

    @Override
    public void stop(Mob maid) {
        TlmHomeBehaviorController.releaseManagedSeat(maid);
        if (maid.isPassenger() && maid.getVehicle() != null
                && SIT.equals(maid.getVehicle().getClass().getName())) maid.stopRiding();
    }

    private static void invokeStart(Object block, Mob maid, BlockState state,
                                    ServerLevel level, BlockPos pos)
            throws ReflectiveOperationException {
        for (Method candidate : block.getClass().getMethods()) {
            if (!candidate.getName().equals("startMaidSit") || candidate.getParameterCount() != 4) continue;
            Class<?>[] p = candidate.getParameterTypes();
            if (p[0].isInstance(maid) && p[1].isInstance(state)
                    && p[2].isInstance(level) && p[3].isInstance(pos)) {
                candidate.invoke(block, maid, state, level, pos);
                return;
            }
        }
    }

    private static Entity createNativeSeat(ServerLevel level, BlockPos pos,
                                           BlockState state, Object block)
            throws ReflectiveOperationException {
        Method sitPosition = declared(block.getClass(), "sitPosition");
        Method typeName = declared(block.getClass(), "getTypeName");
        Method sitYRot = declared(block.getClass(), "sitYRot");
        if (sitPosition == null || typeName == null || sitYRot == null) return null;
        Vec3 local = (Vec3) sitPosition.invoke(block);
        String joyType = String.valueOf(typeName.invoke(block));
        int rotOffset = ((Number) sitYRot.invoke(block)).intValue();

        Class<?> sitClass = Class.forName(SIT);
        Entity sit = (Entity) sitClass.getConstructor(Level.class, Vec3.class, String.class, BlockPos.class)
                .newInstance(level, Vec3.atLowerCornerWithOffset(pos, local.x, local.y, local.z),
                        joyType, pos);
        sit.setYRot(state.getValue(BlockStateProperties.HORIZONTAL_FACING)
                .getOpposite().toYRot() + rotOffset);
        if (!level.addFreshEntity(sit)) return null;

        var blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) { sit.discard(); return null; }
        blockEntity.getClass().getMethod("setSitId", UUID.class).invoke(blockEntity, sit.getUUID());
        blockEntity.setChanged();
        return sit;
    }

    private static Method declared(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {}
        }
        return null;
    }

    private static boolean matchesSeat(Entity entity, BlockPos pos) {
        if (entity == null || !SIT.equals(entity.getClass().getName())) return false;
        try {
            return pos.equals(entity.getClass().getMethod("getAssociatedBlockPos").invoke(entity));
        } catch (ReflectiveOperationException | RuntimeException unsupported) {
            return false;
        }
    }

    private static Entity sitEntity(ServerLevel level, BlockPos pos) {
        try {
            var blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null) return null;
            Object value = blockEntity.getClass().getMethod("getSitId").invoke(blockEntity);
            return value instanceof UUID uuid ? level.getEntity(uuid) : null;
        } catch (ReflectiveOperationException | RuntimeException unsupported) {
            return null;
        }
    }
}
