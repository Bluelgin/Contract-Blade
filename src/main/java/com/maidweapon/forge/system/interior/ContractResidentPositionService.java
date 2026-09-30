package com.maidweapon.forge.system.interior;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** A plot-local position checkpoint, not another copy of maid/inventory NBT. */
public final class ContractResidentPositionService {
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
        double x = ContractInteriorSavedData.originX(plot) + 2.5;
        double y = ContractInteriorTerrainBuilder.ORIGIN_Y + 1.1;
        double z = ContractInteriorSavedData.originZ(plot) + .5;
        float yaw = maid.getYRot(), pitch = maid.getXRot();
        if (state.hasResidentPosition && maid.getStringUUID().equals(state.positionMaidId)
                && validPosition(maid, plot, state.residentX, state.residentY, state.residentZ)) {
            x = state.residentX; y = state.residentY; z = state.residentZ;
            yaw = state.residentYaw; pitch = state.residentPitch;
        }
        // Entry is an explicit player visit: load only the destination chunk, never
        // keep an unattended home loaded or reconstruct an offline maid.
        level.getChunkAt(BlockPos.containing(x, y, z));
        maid.moveTo(x, y, z, yaw, pitch);
        if (!level.noCollision(maid)) {
            BlockPos center = BlockPos.containing(x, y, z);
            boolean found = false;
            for (int dy = 0; dy <= 4 && !found; dy++) for (int dx = -2; dx <= 2 && !found; dx++)
                for (int dz = -2; dz <= 2 && !found; dz++) {
                    BlockPos candidate = center.offset(dx, dy, dz);
                    if (!level.hasChunkAt(candidate) || !validPosition(maid, plot,
                            candidate.getX() + .5, candidate.getY(), candidate.getZ() + .5)) continue;
                    maid.moveTo(candidate.getX() + .5, candidate.getY(), candidate.getZ() + .5, yaw, pitch);
                    found = level.noCollision(maid) && !level.getBlockState(candidate.below()).isAir();
                }
            if (!found) maid.moveTo(ContractInteriorSavedData.originX(plot) + 2.5,
                    ContractInteriorTerrainBuilder.ORIGIN_Y + 1.1,
                    ContractInteriorSavedData.originZ(plot) + .5, yaw, pitch);
        }
        maid.fallDistance = 0;
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
