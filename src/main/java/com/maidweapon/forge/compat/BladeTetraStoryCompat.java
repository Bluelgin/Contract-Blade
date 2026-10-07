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
        var persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        return isLoaded() && (persisted.getBoolean("blade_tetra_divine_domain_cleared")
                || persisted.getBoolean("blade_tetra_divine_afterword_received"));
    }

    public record DivineRoute(int originX, int originZ, net.minecraft.nbt.CompoundTag returnPoint) { }

    /** Narrative familiarity only; this never grants access to a cleared route. */
    public static boolean hasVisitedDivineDomain(ServerPlayer player) {
        var data = player.getPersistentData();
        return isLoaded() && (player.level().dimension().location().toString().equals("blade_tetra:divine_domain")
                || hasCompletedDivinePrologue(player)
                || data.contains("blade_tetra_divine_origin_x") && data.contains("blade_tetra_divine_origin_z"));
    }

    /** Read-only session check: a previous clear must not unlock the jump during a replay's active fight. */
    public static DivineRoute clearedRoute(ServerPlayer player) {
        var data = player.getPersistentData();
        if (!hasCompletedDivinePrologue(player)
                || !player.level().dimension().location().toString().equals("blade_tetra:divine_domain")
                || !data.contains("blade_tetra_divine_challenge")
                || !data.contains("blade_tetra_divine_origin_x") || !data.contains("blade_tetra_divine_origin_z")) return null;
        try {
            Class<?> manager = Class.forName("dev.bladetetra.challenge.DivineDomainManager");
            var sessions = manager.getDeclaredField("SESSIONS");
            sessions.setAccessible(true);
            Object session = ((java.util.Map<?, ?>) sessions.get(null)).get(data.getLong("blade_tetra_divine_challenge"));
            if (session == null) return null;
            var state = session.getClass().getDeclaredField("state");
            var players = session.getClass().getDeclaredField("players");
            state.setAccessible(true); players.setAccessible(true);
            if (!"CLEARED".equals(((Enum<?>) state.get(session)).name())
                    || !((java.util.Set<?>) players.get(session)).contains(player.getUUID())) return null;
            var dimension = net.minecraft.resources.ResourceLocation.tryParse(data.getString("blade_tetra_origin_dimension"));
            var point = dimension == null || !data.contains("blade_tetra_origin_x")
                    ? com.maidweapon.forge.system.fox.challenge.FoxChallengeService.position(
                            net.minecraft.world.level.Level.OVERWORLD.location(),
                            player.getServer().overworld().getSharedSpawnPos().getX() + 0.5,
                            player.getServer().overworld().getSharedSpawnPos().getY() + 1.1,
                            player.getServer().overworld().getSharedSpawnPos().getZ() + 0.5, 0, 0)
                    : com.maidweapon.forge.system.fox.challenge.FoxChallengeService.position(dimension,
                            data.getDouble("blade_tetra_origin_x"), data.getDouble("blade_tetra_origin_y"),
                            data.getDouble("blade_tetra_origin_z"), data.getFloat("blade_tetra_origin_yaw"),
                            data.getFloat("blade_tetra_origin_pitch"));
            return new DivineRoute(data.getInt("blade_tetra_divine_origin_x"),
                    data.getInt("blade_tetra_divine_origin_z"), point);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            // Unsupported provider revisions keep their own fall recovery; never infer a cleared session.
            return null;
        }
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
