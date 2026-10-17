package com.maidweapon.forge.system.resonance;

import com.maidweapon.common.MaidWeaponConfig;
import net.minecraft.world.entity.player.Player;

public final class ResonanceProgressService {
    public enum Upgrade { SUCCESS, ALREADY, SKIPPED, UNSUPPORTED }
    public static int stage(Player player) { return ResonanceProgressSavedData.get(player.getServer()).stage(player.getUUID()); }
    public static double multiplier(Player player) {
        var values = MaidWeaponConfig.RESONANCE_PROGRESS_MULTIPLIERS.get();
        return values.get(Math.min(stage(player), values.size() - 1)).doubleValue();
    }
    public static Upgrade upgrade(Player player, int requested) {
        if (player.level().isClientSide || player.getServer() == null || !player.getServer().isSameThread()) return Upgrade.UNSUPPORTED;
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
