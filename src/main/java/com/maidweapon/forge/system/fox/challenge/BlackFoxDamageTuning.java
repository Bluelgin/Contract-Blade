package com.maidweapon.forge.system.fox.challenge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Fixed attack baselines and isolated native-hit provenance. No skill timing or visual effects. */
public final class BlackFoxDamageTuning {
    public static final float LIGHT = 4, CROSS = 5, SIDE = 6, SKY = 7, DIMENSION = 10,
            IAIDO = 14, MINOR = 5, SEAL = 6, UNSEAL = 7, BEAM = 5, RIFT = 4,
            CORE_CONTACT = 6, CORE_TIMEOUT = 12;
    public static final double PHANTOM_SWORD = 3, PHANTOM_BLADE = 5;
    public static final double NATIVE_COMBO_MULTIPLIER = .65;
    private static final ThreadLocal<NativeHit> NATIVE = new ThreadLocal<>();
    public record NativeHit(Entity attacker, Entity target) { }
    public static final class Scope implements AutoCloseable {
        private final NativeHit previous;
        private Scope(Entity attacker, Entity target) {
            previous = NATIVE.get();
            NATIVE.set(new NativeHit(attacker, target));
        }
        public void close() { if (previous == null) NATIVE.remove(); else NATIVE.set(previous); }
    }
    public static Scope nativeHit(Entity attacker, Entity target) { return new Scope(attacker, target); }
    public static boolean nativeComboHit(BlackFoxCombatant boss, ServerPlayer target) {
        var hit = NATIVE.get();
        return hit != null && hit.attacker() == boss.body() && hit.target() == target;
    }
    public static float scaled(float amount, double multiplier, boolean nativeCombo) {
        if (!Float.isFinite(amount) || amount <= 0 || !Double.isFinite(multiplier) || multiplier < 0) return 0;
        return (float) Math.min(Float.MAX_VALUE, amount * multiplier * (nativeCombo ? NATIVE_COMBO_MULTIPLIER : 1));
    }
    private BlackFoxDamageTuning() { }
}
