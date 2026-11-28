package com.maidweapon.port.fox;

import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exercise the real NeoForge reduction pipeline, including another mod's modifier. */
class WhiteFoxAbsorptionTest {
    private DamageContainer damage(float amount) {
        return new DamageContainer(new DamageSource(Holder.direct(new DamageType("test", 0))), amount);
    }
    @Test void absorbedHitDoesNotConsumeDefenseOrRescueCooldown() {
        var hit=damage(20);
        hit.addModifier(DamageContainer.Reduction.ABSORPTION,(c,a)->
            WhiteFoxDamageBoundary.afterAbsorption(c,a,d->{fail("No health damage: must not trigger oath/guard");return d;}));
        hit.setReduction(DamageContainer.Reduction.ABSORPTION,20);
        assertEquals(0,hit.getNewDamage());
        assertEquals(20,hit.getReduction(DamageContainer.Reduction.ABSORPTION));
    }
    @Test void guardReducesHealthPortionWithoutSpendingExtraAbsorption() {
        var hit=damage(30);
        hit.addModifier(DamageContainer.Reduction.ABSORPTION,(c,a)->a/2); // preceding mod absorbs only five
        hit.addModifier(DamageContainer.Reduction.ABSORPTION,(c,a)->
            WhiteFoxDamageBoundary.afterAbsorption(c,a,d->{assertEquals(25,d);return d*.8;}));
        hit.setReduction(DamageContainer.Reduction.ABSORPTION,10);
        assertEquals(20,hit.getNewDamage(),.001);
        assertEquals(5,hit.getReduction(DamageContainer.Reduction.ABSORPTION));
    }
    @Test void rescueLeavesOneHealthAfterAbsorptionRatherThanApplyingAbsorptionTwice() {
        var hit=damage(30);
        hit.addModifier(DamageContainer.Reduction.ABSORPTION,(c,a)->
            WhiteFoxDamageBoundary.afterAbsorption(c,a,d->9)); // player has ten health
        hit.setReduction(DamageContainer.Reduction.ABSORPTION,10);
        assertEquals(9,hit.getNewDamage());
        assertEquals(10,hit.getReduction(DamageContainer.Reduction.ABSORPTION));
    }
}
