package com.maidweapon.forge.system.interior;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** A plot-local position checkpoint, not another copy of maid/inventory NBT. */
public final class ContractResidentPositionService {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    public static void remember(Entity maid) {
        if (!(maid.level() instanceof ServerLevel level) || !level.dimension().equals(ContractInteriorService.INTERIOR_LEVEL)) return;
        String binding = maid.getPersistentData().getString(com.maidweapon.forge.compat.tlm.ContractMaidKeys.ENTITY_BINDING_ID);
        var saved = ContractInteriorSavedData.get(level.getServer());
        var plot = saved.find(binding);
        if (plot == null || !validPosition(maid, plot, maid.getX(), maid.getY(), maid.getZ())) return;
        var state = plot.home();
        state.hasResidentPosition = true; state.positionMaidId = maid.getStringUUID();
        state.residentX = maid.getX(); state.residentY = maid.getY(); state.residentZ = maid.getZ();
        state.residentYaw = maid.getYRot(); state.residentPitch = maid.getXRot();
        saved.setDirty();
    }

    public static void restore(Entity maid, ContractInteriorSavedData.Plot plot) {
        if (!(maid.level() instanceof ServerLevel level)) return;
        var state = plot.home();
        double x = ContractInteriorSavedData.originX(plot) + 6.5;
        double y = ContractInteriorTerrainBuilder.ORIGIN_Y;
        double z = ContractInteriorSavedData.originZ(plot) + 4.5;
        float yaw = maid.getYRot(), pitch = maid.getXRot();
        boolean checkpoint = false;
        if (state.hasResidentPosition && maid.getStringUUID().equals(state.positionMaidId)
                && validPosition(maid, plot, state.residentX, state.residentY, state.residentZ)) {
            x = state.residentX; y = state.residentY; z = state.residentZ;
            yaw = state.residentYaw; pitch = state.residentPitch;
            checkpoint = true;
        }
        // Entry is an explicit player visit: load only the destination chunk, never
        // keep an unattended home loaded or reconstruct an offline maid.
        level.getChunkAt(BlockPos.containing(x, y, z));
        maid.moveTo(x, y, z, yaw, pitch);
        if (!safeStanding(maid, level)) {
            BlockPos center = BlockPos.containing(x, y, z);
            boolean found = false;
            for (int dy = -3; dy <= 4 && !found; dy++) for (int dx = -2; dx <= 2 && !found; dx++)
                for (int dz = -2; dz <= 2 && !found; dz++) {
                    BlockPos candidate = center.offset(dx, dy, dz);
                    if (!level.hasChunkAt(candidate) || !validPosition(maid, plot,
                            candidate.getX() + .5, candidate.getY(), candidate.getZ() + .5)) continue;
                    maid.moveTo(candidate.getX() + .5, candidate.getY(), candidate.getZ() + .5, yaw, pitch);
                    found = safeStanding(maid, level);
                }
            if (!found) {
                BlockPos home = new BlockPos(ContractInteriorSavedData.originX(plot) + 6,
                        ContractInteriorTerrainBuilder.ORIGIN_Y, ContractInteriorSavedData.originZ(plot) + 4);
                level.getChunkAt(home);
                for (int dy = -3; dy <= 4 && !found; dy++) for (int dx = -3; dx <= 3 && !found; dx++)
                    for (int dz = -3; dz <= 3 && !found; dz++) {
                        BlockPos candidate = home.offset(dx, dy, dz);
                        if (!level.hasChunkAt(candidate) || !validPosition(maid, plot,
                                candidate.getX() + .5, candidate.getY(), candidate.getZ() + .5)) continue;
                        maid.moveTo(candidate.getX() + .5, candidate.getY(), candidate.getZ() + .5, yaw, pitch);
                        found = safeStanding(maid, level);
                    }
                // Failing before publication leaves the weapon's only authoritative NBT intact.
                if (!found) throw new IllegalStateException("No safe home resident location for " + maid.getUUID());
            }
        }
        maid.fallDistance = 0;
        maid.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        LOGGER.info("[ContractHome] Resident placement maid={}, savedPosition={}, matchingIdentity={}, checkpoint={}, final={}",
                maid.getUUID(), state.hasResidentPosition, maid.getStringUUID().equals(state.positionMaidId), checkpoint, maid.position());
    }

    private static boolean safeStanding(Entity maid, ServerLevel level) {
        BlockPos pos = maid.blockPosition();
        if (!level.noCollision(maid) || !level.getFluidState(pos).isEmpty()) return false;
        // Old checkpoints can be just above the floor, before the next physics tick.
        // Allow a short safe settling distance, not an unsupported void drop.
        for (int depth = 1; depth <= 3; depth++) {
            BlockPos ground = pos.below(depth);
            if (!level.getFluidState(ground).isEmpty()) return false;
            if (!level.getBlockState(ground).getCollisionShape(level, ground).isEmpty()) return true;
        }
        return false;
    }

    private static boolean validPosition(Entity maid, ContractInteriorSavedData.Plot plot, double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || y < Math.max(30, maid.level().getMinBuildHeight() + 1) || y >= maid.level().getMaxBuildHeight() - 2) return false;
        int radius = ContractInteriorTerrainBuilder.radiusForStage(plot.generatedStage());
        return Math.pow(Math.abs(x - ContractInteriorSavedData.originX(plot)) / radius, 6)
                + Math.pow(Math.abs(z - ContractInteriorSavedData.originZ(plot)) / radius, 6) <= 1;
    }
    private ContractResidentPositionService() {}
}
