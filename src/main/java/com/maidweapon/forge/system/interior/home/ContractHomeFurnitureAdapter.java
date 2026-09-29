package com.maidweapon.forge.system.interior.home;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import java.util.Optional;

/** Server-thread extension point. Implementations must never load chunks or fake interactions. */
public interface ContractHomeFurnitureAdapter {
    Optional<ActivityTarget> blockTarget(ServerLevel level, BlockPos position);
    Optional<ActivityTarget> entityTarget(Entity entity);
    boolean valid(ServerLevel level, ActivityTarget target, Mob maid);
    boolean start(ServerLevel level, ActivityTarget target, Mob maid);
    boolean running(ServerLevel level, ActivityTarget target, Mob maid);
    void stop(Mob maid);
}
