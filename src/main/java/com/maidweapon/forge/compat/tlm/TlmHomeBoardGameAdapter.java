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
                if (p[0].isInstance(maid)
                        && p[1].isAssignableFrom(BlockState.class)
                        && p[2].isInstance(level)
                        && p[3].isAssignableFrom(BlockPos.class)) {
                    start = candidate;
                    break;
                }
            }
            if (start == null) {
                TlmHomeBehaviorController.endBoardGame(maid);
                return false;
            }
            start.invoke(block, maid, state, level, target.position());
            Entity sit = sitEntity(level, target.position());
            boolean started = sit != null && sit.isAlive()
                    && sit.hasPassenger(maid) && maid.getVehicle() == sit;
            if (!started) TlmHomeBehaviorController.endBoardGame(maid);
            return started;
        } catch (ReflectiveOperationException | RuntimeException unsupported) {
            TlmHomeBehaviorController.endBoardGame(maid);
            return false;
        }
    }

    @Override
    public boolean running(ServerLevel level, ActivityTarget target, Mob maid) {
        if (!valid(level, target, maid)) return false;
        Entity sit = sitEntity(level, target.position());
        return sit != null && maid.getVehicle() == sit && sit.hasPassenger(maid);
    }

    @Override
    public void stop(Mob maid) {
        if (maid.isPassenger() && maid.getVehicle() != null
                && SIT.equals(maid.getVehicle().getClass().getName())) maid.stopRiding();
        TlmHomeBehaviorController.endBoardGame(maid);
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
