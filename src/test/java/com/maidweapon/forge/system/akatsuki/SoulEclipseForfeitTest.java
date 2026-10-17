package com.maidweapon.forge.system.akatsuki;

import static org.junit.jupiter.api.Assertions.*;
import com.maidweapon.forge.system.akatsuki.soul.SoulDamageDebt;
import org.junit.jupiter.api.Test;

class SoulEclipseForfeitTest {
  @Test void failedChainForfeitsOnlyAerialReceiptNotOrdinaryShare() {
    var debt = new SoulDamageDebt(100, 5);
    assertEquals(95, debt.available(1, true), 1e-7);
    debt.forfeitAerial();
    assertEquals(0, debt.available(1, true));
    assertEquals(95 * .12, debt.available(.12, false), 1e-7);
    assertEquals(0, debt.reserve(1, 95, true));
    assertEquals(95 * .12, debt.reserve(.12, 95, false), 1e-7);
    assertEquals(0, debt.available(.12, false));
    assertEquals(0, debt.available(1, true));
  }
}
