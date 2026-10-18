package com.maidweapon.forge.system.resonance;

import com.maidweapon.common.MaidWeaponConfig;
import net.minecraft.world.entity.player.Player;

public final class ResonanceProgressService {
    public enum Upgrade { SUCCESS, ALREADY, SKIPPED, UNSUPPORTED }
    private static boolean server(Player player) {
        return player != null && !player.level().isClientSide && player.getServer() != null && player.getServer().isSameThread();
    }
    public static int stage(Player player) {
        return server(player) ? ResonanceProgressSavedData.get(player.getServer()).stage(player.getUUID()) : 0;
    }
    public static double multiplier(Player player) {
        if (!server(player)) return 1;
        var values = MaidWeaponConfig.RESONANCE_PROGRESS_MULTIPLIERS.get();
        if (values == null || values.isEmpty()) return 1;
        var result = values.get(Math.min(stage(player), values.size() - 1));
        return result != null && Double.isFinite(result) && result > 0 ? result : 1;
    }
    public static Upgrade upgrade(Player player, int requested) {
        if (!server(player)) return Upgrade.UNSUPPORTED;
        var data = ResonanceProgressSavedData.get(player.getServer());
        if (!data.writable(player.getUUID())) return Upgrade.UNSUPPORTED;
        int current = data.stage(player.getUUID());
        if (requested <= current) return Upgrade.ALREADY;
        if (requested != current + 1 || requested >= MaidWeaponConfig.RESONANCE_PROGRESS_MULTIPLIERS.get().size()) return Upgrade.SKIPPED;
        return data.upgrade(player.getUUID(), requested, MaidWeaponConfig.RESONANCE_PROGRESS_MULTIPLIERS.get().size() - 1)
                ? Upgrade.SUCCESS : Upgrade.UNSUPPORTED;
    }
    private ResonanceProgressService() { }
}
