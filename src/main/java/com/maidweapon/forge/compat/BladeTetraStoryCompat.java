package com.maidweapon.forge.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;
import net.minecraft.world.item.ItemStack;

/** Read-only bridge to BladeTetra's existing progress; never advances its quests. */
public final class BladeTetraStoryCompat {
    public static boolean isLoaded() {
        return ModList.get().isLoaded("blade_tetra");
    }

    public static boolean hasCompletedDivinePrologue(ServerPlayer player) {
        return isLoaded() && player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG)
                .getBoolean("blade_tetra_divine_domain_cleared");
    }

    /** Recognize an awakened blade even when its Contract Blade channel is disabled. */
    public static boolean isAkatsuki(ItemStack stack) {
        if (!isLoaded() || stack.isEmpty()) return false;
        try {
            Class<?> resolver = Class.forName("dev.bladetetra.easteregg.SoulLegacyState",
                    false, BladeTetraStoryCompat.class.getClassLoader());
            Object identity = resolver.getMethod("active", ItemStack.class).invoke(null, stack);
            return identity instanceof Enum<?> legacy && "AKATSUKI".equals(legacy.name());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private BladeTetraStoryCompat() { }
}
