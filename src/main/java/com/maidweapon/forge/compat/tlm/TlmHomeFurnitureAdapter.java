package com.maidweapon.forge.compat.tlm;

import com.maidweapon.forge.system.interior.home.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.Optional;

/** Confirmed against TLM MaidBedTask and MaidFindSitTask (1.20 branch).
 * Entertainment is deliberately unsupported: EntitySit enforces TLM's world-time schedule.
 */
public final class TlmHomeFurnitureAdapter implements ContractHomeFurnitureAdapter {
    private static final ResourceLocation BED = new ResourceLocation("touhou_little_maid", "maid_bed");
    private static final String CHAIR = "com.github.tartaricacid.touhoulittlemaid.entity.item.EntityChair";
    @Override public Optional<ActivityTarget> blockTarget(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return Optional.empty();
        var state = level.getBlockState(pos);
        if (BED.equals(ForgeRegistries.BLOCKS.getKey(state.getBlock()))
                && state.hasProperty(BedBlock.OCCUPIED)
                && state.hasProperty(BedBlock.PART) && state.getValue(BedBlock.PART) == BedPart.HEAD) {
            return Optional.of(new ActivityTarget(pos.immutable(), ContractHomeActivity.SLEEP,
                    BED.toString(), null, 1, state.getValue(BedBlock.OCCUPIED)));
        }
        return Optional.empty();
    }
    @Override public Optional<ActivityTarget> entityTarget(Entity entity) {
        if (!entity.getClass().getName().equals(CHAIR) || !entity.isAlive()) return Optional.empty();
        try {
            if (!Boolean.TRUE.equals(entity.getClass().getMethod("isTameableCanRide").invoke(entity)))
                return Optional.empty();
        } catch (ReflectiveOperationException unsupported) { return Optional.empty(); }
        return Optional.of(new ActivityTarget(entity.blockPosition(), ContractHomeActivity.SIT,
                "touhou_little_maid:chair", entity.getUUID(), 1, !entity.getPassengers().isEmpty()));
    }
    @Override public boolean valid(ServerLevel level, ActivityTarget target, Mob maid) {
        if (!level.hasChunkAt(target.position())) return false;
        if (target.entityId() != null) {
            Entity chair = level.getEntity(target.entityId());
            return chair != null && entityTarget(chair).isPresent()
                    && chair.blockPosition().equals(target.position())
                    && (chair.getPassengers().isEmpty() || chair.hasPassenger(maid));
        }
        var fresh = blockTarget(level, target.position());
        return fresh.isPresent() && fresh.get().key().equals(target.key())
                && (!fresh.get().occupied() || maid.getSleepingPos().filter(target.position()::equals).isPresent());
    }
    @Override public boolean start(ServerLevel level, ActivityTarget target, Mob maid) {
        if (!valid(level, target, maid) || target.position().distToCenterSqr(maid.position()) > 4.0) return false;
        maid.getNavigation().stop();
        if (target.activity() == ContractHomeActivity.SLEEP) {
            // Same real sleep API used by TLM MaidBedTask, including maid sleep benefits/animation.
            maid.startSleeping(target.position());
            maid.setPos(target.position().getX() + 0.5, target.position().getY() + 0.8,
                    target.position().getZ() + 0.5);
            return maid.isSleeping();
        }
        Entity chair = target.entityId() == null ? null : level.getEntity(target.entityId());
        return chair != null && maid.startRiding(chair, true);
    }
    @Override public boolean running(ServerLevel level, ActivityTarget target, Mob maid) {
        if (!valid(level, target, maid)) return false;
        return target.activity() == ContractHomeActivity.SLEEP
                ? maid.getSleepingPos().filter(target.position()::equals).isPresent()
                : maid.getVehicle() != null && maid.getVehicle().getUUID().equals(target.entityId());
    }
    @Override public void stop(Mob maid) {
        if (maid.isSleeping()) maid.stopSleeping();
        if (maid.isPassenger()) maid.stopRiding();
    }
}
