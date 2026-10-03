package com.maidweapon.forge.compat.tlm;

import com.maidweapon.forge.system.interior.home.ActivityTarget;
import com.maidweapon.forge.system.interior.home.ContractHomeActivity;
import com.maidweapon.forge.system.interior.home.ContractHomeFurnitureAdapter;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

/** Observe native picnic seats and food; never seat a maid or consume food ourselves. */
public final class TlmHomePicnicAdapter implements ContractHomeFurnitureAdapter {
    private static final ResourceLocation PICNIC =
            new ResourceLocation("touhou_little_maid", "picnic_mat");
    private static final String SIT =
            "com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit";

    @Override
    public Optional<ActivityTarget> blockTarget(ServerLevel level, BlockPos pos) {
        if (!TlmHomeBehaviorController.homeMealSupported() || !level.hasChunkAt(pos)) {
            return Optional.empty();
        }
        BlockState state = level.getBlockState(pos);
        if (!PICNIC.equals(ForgeRegistries.BLOCKS.getKey(state.getBlock()))) return Optional.empty();
        Object tile = level.getBlockEntity(pos);
        if (tile == null || !isCenter(tile, pos) || !hasFood(tile)) return Optional.empty();
        return Optional.of(new ActivityTarget(pos.immutable(), ContractHomeActivity.MEAL,
                PICNIC.toString(), null, 3, !hasFreeSeat(level, tile)));
    }

    @Override public Optional<ActivityTarget> entityTarget(Entity entity) { return Optional.empty(); }

    @Override
    public boolean valid(ServerLevel level, ActivityTarget target, Mob maid) {
        var fresh = blockTarget(level, target.position());
        if (fresh.isEmpty() || !fresh.get().key().equals(target.key())) return false;
        if (matchesSeat(maid.getVehicle(), target.position())) return true;
        Object tile = level.getBlockEntity(target.position());
        return tile != null && hasFreeSeat(level, tile);
    }

    @Override
    public boolean running(ServerLevel level, ActivityTarget target, Mob maid) {
        if (!valid(level, target, maid)) return false;
        Entity seat = maid.getVehicle();
        return matchesSeat(seat, target.position()) && seat.hasPassenger(maid);
    }

    private static boolean isCenter(Object tile, BlockPos pos) {
        try {
            Object value = tile.getClass().getMethod("getCenterPos").invoke(tile);
            return pos.equals(value);
        } catch (ReflectiveOperationException | RuntimeException unsupported) {
            return false;
        }
    }

    private static boolean hasFood(Object tile) {
        try {
            Object handler = tile.getClass().getMethod("getHandler").invoke(tile);
            int slots = ((Number) handler.getClass().getMethod("getSlots").invoke(handler)).intValue();
            Method get = handler.getClass().getMethod("getStackInSlot", int.class);
            for (int i = 0; i < slots; i++) {
                Object value = get.invoke(handler, i);
                if (value instanceof net.minecraft.world.item.ItemStack stack && !stack.isEmpty()) {
                    return true;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException unsupported) {
            return false;
        }
        return false;
    }

    private static boolean hasFreeSeat(ServerLevel level, Object tile) {
        try {
            Object value = tile.getClass().getMethod("getSitIds").invoke(tile);
            if (!(value instanceof UUID[] ids)) return false;
            for (UUID id : ids) {
                if (id == null || id.equals(Util.NIL_UUID)) return true;
                Entity seat = level.getEntity(id);
                if (seat == null || !seat.isAlive() || seat.getPassengers().isEmpty()) return true;
            }
        } catch (ReflectiveOperationException | RuntimeException unsupported) {
            return false;
        }
        return false;
    }

    private static boolean matchesSeat(Entity entity, BlockPos pos) {
        if (entity == null || !SIT.equals(entity.getClass().getName())) return false;
        try {
            return pos.equals(entity.getClass().getMethod("getAssociatedBlockPos").invoke(entity));
        } catch (ReflectiveOperationException | RuntimeException unsupported) {
            return false;
        }
    }
}
