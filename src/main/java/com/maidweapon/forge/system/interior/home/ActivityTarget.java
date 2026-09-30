package com.maidweapon.forge.system.interior.home;

import net.minecraft.core.BlockPos;
import java.util.UUID;

/** Immutable identity; occupancy is checked against the real world before use. */
public record ActivityTarget(BlockPos position, ContractHomeActivity activity, String sourceId,
                             UUID entityId, int weight, boolean occupied) {
    public String key() { return sourceId + ":" + (entityId == null ? position.asLong() : entityId); }
}
