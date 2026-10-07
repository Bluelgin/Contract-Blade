package com.maidweapon.forge.system.fox.challenge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import java.util.UUID;

/** The encounter owns combat; optional model providers own the visual body. */
public interface BlackFoxCombatant {
    Mob body();
    UUID challenger();
    BlockPos arenaOrigin();
    BlackFoxController combat();
    void sync(BlackFoxFight.Motion motion, boolean phaseTwo);
    default void markSlash(BlackFoxFight.Skill skill,int age) { }
}
