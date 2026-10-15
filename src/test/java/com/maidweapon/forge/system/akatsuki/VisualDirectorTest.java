package com.maidweapon.forge.system.akatsuki;

import static org.junit.jupiter.api.Assertions.*;

import com.maidweapon.forge.client.AkatsukiVisualDirector;
import org.junit.jupiter.api.Test;

class VisualDirectorTest {
  @Test
  void idleIsSilentAndRampageHasMoreCutsWithoutAnyDamageScheduler() {
    var normal = new AkatsukiVisualDirector();
    var rampage = new AkatsukiVisualDirector();
    assertEquals(0, normal.cuts(0, false));
    assertFalse(normal.swords(0, false));
    int n = 0, r = 0, ns = 0, rs = 0;
    for (int tick = 0; tick < 80; tick++) {
      if (tick % 8 == 0) {
        normal.attack(tick);
        rampage.attack(tick);
      }
      n += normal.cuts(tick, false);
      r += rampage.cuts(tick, true);
      if (normal.swords(tick, false)) ns++;
      if (rampage.swords(tick, true)) rs++;
    }
    assertTrue(r > n * 2);
    assertTrue(rs > ns);
    assertEquals(0, normal.cuts(120, false));
    assertFalse(rampage.swords(120, true));
  }
}
