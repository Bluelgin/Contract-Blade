package com.maidweapon.forge.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.capabilities.Capability;
import java.lang.reflect.Method;

/** Lazy native rating bridge. Native clamping, decay and rank synchronization remain authoritative. */
public final class BlackFoxRankCompat {
    public static void reward(ServerPlayer player, boolean comboClash) {
        if (!SlashBladeCompat.isSlashBlade(player.getMainHandItem())) return;
        Object rank = player.getCapability(Api.capability).resolve().orElse(null);
        if (rank == null) return;
        try {
            long unit = (long) Api.unit.invoke(rank);
            Api.add.invoke(rank, player, (long) (unit * (comboClash ? .2 : .1)));
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Native parry rank reward", error); }
    }
    private static final class Api {
        static final Capability<?> capability;
        static final Method add, unit;
        static {
            try {
                capability = (Capability<?>) Class.forName("mods.flammpfeil.slashblade.capability.concentrationrank.CapabilityConcentrationRank")
                        .getField("RANK_POINT").get(null);
                Class<?> rank = Class.forName("mods.flammpfeil.slashblade.capability.concentrationrank.IConcentrationRank");
                add = rank.getMethod("addRankPoint", net.minecraft.world.entity.LivingEntity.class, long.class);
                unit = rank.getMethod("getUnitCapacity");
            } catch (ReflectiveOperationException error) { throw new IllegalStateException("Native rank API", error); }
        }
    }
    private BlackFoxRankCompat() { }
}
