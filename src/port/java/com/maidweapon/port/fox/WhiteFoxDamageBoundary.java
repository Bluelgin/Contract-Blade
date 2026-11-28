package com.maidweapon.port.fox;

/** NeoForge absorption adapter; does not mutate health or absorption resources. */
final class WhiteFoxDamageBoundary {
    /** Preserve native absorption accounting while changing only the health portion. */
    static float afterAbsorption(net.neoforged.neoforge.common.damagesource.DamageContainer container,
        float absorption,java.util.function.DoubleUnaryOperator defense){
        float incoming=container.getNewDamage();
        if(!Float.isFinite(incoming)||!Float.isFinite(absorption)||incoming<=0||absorption<0||absorption>incoming)
            return absorption; // Unknown mod reductions retain their own behavior.
        float healthDamage=incoming-absorption;
        if(healthDamage>0){
            float defended=(float)defense.applyAsDouble(healthDamage);
            if(Float.isFinite(defended)&&defended>=0&&defended<=healthDamage)
                container.setNewDamage(absorption+defended);
        }
        return absorption;
    }
    private WhiteFoxDamageBoundary() {}
}
