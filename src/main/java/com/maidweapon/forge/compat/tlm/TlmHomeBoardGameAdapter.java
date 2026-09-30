package com.maidweapon.forge.compat.tlm;

import com.maidweapon.forge.system.interior.home.ActivityTarget;
import com.maidweapon.forge.system.interior.home.ContractHomeActivity;
import com.maidweapon.forge.system.interior.home.ContractHomeFurnitureAdapter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Native TLM Gomoku / Chinese chess / western chess support. */
public final class TlmHomeBoardGameAdapter implements ContractHomeFurnitureAdapter {
    private static final Set<ResourceLocation> BOARDS = Set.of(
            new ResourceLocation("touhou_little_maid", "gomoku"),
            new ResourceLocation("touhou_little_maid", "cchess"),
            new ResourceLocation("touhou_little_maid", "wchess")
    );
    private static final String SIT = "com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit";

    @Override
    public Optional<ActivityTarget> blockTarget(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return Optional.empty();
        BlockState state = level.getBlockState(pos);
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (id == null || !BOARDS.contains(id) || level.getBlockEntity(pos) == null) return Optional.empty();
        Entity sit = sitEntity(level, pos);
        return Optional.of(new ActivityTarget(pos.immutable(), ContractHomeActivity.PLAY,
                id.toString(), null, 2, sit != null && sit.isAlive()));
    }

    @Override
    public Optional<ActivityTarget> entityTarget(Entity entity) {
        return Optional.empty();
    }

    @Override
    public boolean valid(ServerLevel level, ActivityTarget target, Mob maid) {
        var fresh = blockTarget(level, target.position());
        if (fresh.isEmpty() || !fresh.get().key().equals(target.key())) return false;
        Entity vehicle = maid.getVehicle();
        if (matchesSeat(vehicle, target.position())) return true;
        Entity sit = sitEntity(level, target.position());
        return sit == null || !sit.isAlive() || sit.hasPassenger(maid);
    }

    @Override
    public boolean start(ServerLevel level, ActivityTarget target, Mob maid) {
        if (!valid(level, target, maid)
                || target.position().distToCenterSqr(maid.position()) > 4.0
                || !TlmHomeBehaviorController.beginBoardGame(maid)) return false;
        try {
            BlockState state = level.getBlockState(target.position());
            Object block = state.getBlock();
            java.lang.reflect.Method start = null;
            for (java.lang.reflect.Method candidate : block.getClass().getMethods()) {
                if (!candidate.getName().equals("startMaidSit") || candidate.getParameterCount() != 4) continue;
                Class<?>[] p = candidate.getParameterTypes();
                if (p[0].isInstance(maid) && p[1].isInstance(state)
                        && p[2].isInstance(level) && p[3].isInstance(target.position())) {
                    start = candidate;
                    break;
                }
            }
            if (start != null) start.invoke(block, maid, state, level, target.position());

            // TLM's helper creates the real EntitySit before mounting. Some headless
            // paths can leave that seat alive while startRiding returned false, so the
            // live seat is authoritative and can be mounted directly.
            Entity sit = maid.getVehicle();
            if (matchesSeat(sit, target.position()) && sit.isAlive() && sit.hasPassenger(maid)) return true;

            sit = sitEntity(level, target.position());
            if (matchesSeat(sit, target.position()) && sit.isAlive()) {
                if (!sit.getPassengers().isEmpty() && !sit.hasPassenger(maid)) {
                    TlmHomeBehaviorController.endBoardGame(maid);
                    return false;
                }
                if (maid.startRiding(sit, true) && sit.hasPassenger(maid)) return true;
            }

            // Last-resort compatibility path: construct the same TLM EntitySit and
            // TileEntityJoy linkage used by BlockGomoku/BlockCChess/BlockWChess.
            sit = createNativeSeat(level, target.position(), state, target.sourceId());
            boolean started = sit != null && maid.startRiding(sit, true) && sit.hasPassenger(maid);
            if (!started) {
                if (sit != null && sit.getPassengers().isEmpty()) sit.discard();
                TlmHomeBehaviorController.endBoardGame(maid);
            }
            return started;
        } catch (ReflectiveOperationException | RuntimeException unsupported) {
            TlmHomeBehaviorController.endBoardGame(maid);
            return false;
        }
    }

    @Override
    public boolean running(ServerLevel level, ActivityTarget target, Mob maid) {
        if (!valid(level, target, maid)) return false;
        Entity sit = maid.getVehicle();
        return matchesSeat(sit, target.position()) && sit.hasPassenger(maid);
    }

    @Override
    public void stop(Mob maid) {
        if (maid.isPassenger() && maid.getVehicle() != null
                && SIT.equals(maid.getVehicle().getClass().getName())) maid.stopRiding();
        TlmHomeBehaviorController.endBoardGame(maid);
    }

    private static boolean matchesSeat(Entity entity, BlockPos pos) {
        if (entity == null || !SIT.equals(entity.getClass().getName())) return false;
        try {
            Object associated = entity.getClass().getMethod("getAssociatedBlockPos").invoke(entity);
            return pos.equals(associated);
        } catch (ReflectiveOperationException | RuntimeException unsupported) {
            return false;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Entity createNativeSeat(ServerLevel level, BlockPos pos, BlockState state, String sourceId)
            throws ReflectiveOperationException {
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        boolean gomoku = sourceId.endsWith(":gomoku");
        Direction face = gomoku ? facing.getClockWise() : facing.getOpposite();
        double distance = gomoku ? 1.5D : 2.0D;
        Vec3 seatPos = Vec3.atLowerCornerWithOffset(pos,
                0.5D + face.getStepX() * distance, 0.1D,
                0.5D + face.getStepZ() * distance);

        Class<?> typeClass = Class.forName(
                "com.github.tartaricacid.touhoulittlemaid.entity.favorability.Type");
        Object gomokuType = Enum.valueOf((Class<? extends Enum>) typeClass.asSubclass(Enum.class), "GOMOKU");
        String joyType = String.valueOf(typeClass.getMethod("getTypeName").invoke(gomokuType));

        Class<?> sitClass = Class.forName(SIT);
        Entity sit = (Entity) sitClass.getConstructor(Level.class, Vec3.class, String.class, BlockPos.class)
                .newInstance(level, seatPos, joyType, pos);
        sit.setYRot(face.getOpposite().toYRot());
        if (!level.addFreshEntity(sit)) return null;

        var blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            sit.discard();
            return null;
        }
        blockEntity.getClass().getMethod("setSitId", UUID.class).invoke(blockEntity, sit.getUUID());
        blockEntity.setChanged();
        return sit;
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
