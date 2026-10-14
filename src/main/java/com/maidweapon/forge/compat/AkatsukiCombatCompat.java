package com.maidweapon.forge.compat;

import net.minecraft.world.damagesource.DamageSource;
import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;

/** Public optional provider guard. No writes to provider fields or damage types. */
public final class AkatsukiCombatCompat {
    private static boolean lookedUp;
    private static Method apply, secondary;
    private static void resolve() {
        if (lookedUp || !BladeTetraStoryCompat.isLoaded()) return;
        lookedUp = true;
        try {
            var type = Class.forName("dev.bladetetra.easteregg.SoulLegacyDamageGuard");
            apply = type.getMethod("apply", BooleanSupplier.class);
            secondary = type.getMethod("isSecondary", DamageSource.class);
        } catch (ReflectiveOperationException | LinkageError failure) {
            CompatDiagnostics.warnOnce("akatsuki-secondary-guard", failure);
        }
    }
    public static boolean secondary(DamageSource source) {
        resolve();
        if (secondary == null) return true;
        try { return (Boolean) secondary.invoke(null, source); }
        catch (ReflectiveOperationException | RuntimeException failure) { return true; }
    }
    /** Fail closed if a provider version cannot isolate the domain from native execution credit. */
    public static boolean guarded(BooleanSupplier action) {
        resolve();
        if (apply == null) return false;
        try { return (Boolean) apply.invoke(null, action); }
        catch (ReflectiveOperationException | RuntimeException failure) {
            CompatDiagnostics.warnOnce("akatsuki-guarded-damage", failure);
            return false;
        }
    }
    private AkatsukiCombatCompat() {}
}
