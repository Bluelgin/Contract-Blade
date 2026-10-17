package com.maidweapon.forge.system.akatsuki;

import static org.junit.jupiter.api.Assertions.*;
import com.maidweapon.forge.system.akatsuki.soul.SoulEclipseHitSequence;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SoulEclipseHitSequenceTest {
  @Test void fiveDifferentSwordsNeedFourTicksBetweenHits() {
    var chain = new SoulEclipseHitSequence(100);
    var sword1 = UUID.randomUUID();
    assertEquals(SoulEclipseHitSequence.Result.ADVANCED, chain.hit(sword1, 100));
    assertEquals(SoulEclipseHitSequence.Result.DUPLICATE, chain.hit(sword1, 104));
    assertEquals(SoulEclipseHitSequence.Result.TOO_FAST, chain.hit(UUID.randomUUID(), 102));
    assertEquals(1, chain.hits());
    for (int i = 1; i < 5; i++) {
      assertEquals(i == 4 ? SoulEclipseHitSequence.Result.COMPLETE
          : SoulEclipseHitSequence.Result.ADVANCED,
          chain.hit(UUID.randomUUID(), 100 + i * 4));
    }
    assertEquals(5, chain.hits());
    assertEquals(SoulEclipseHitSequence.Result.CLOSED,
        chain.hit(UUID.randomUUID(), 121));
  }

  @Test void fastVolleyCannotBeReusedAfterFourTicks() {
    var chain = new SoulEclipseHitSequence(100);
    var first = UUID.randomUUID();
    var fast = UUID.randomUUID();
    assertEquals(SoulEclipseHitSequence.Result.ADVANCED, chain.hit(first, 100));
    assertEquals(SoulEclipseHitSequence.Result.TOO_FAST, chain.hit(fast, 101));
    assertEquals(SoulEclipseHitSequence.Result.DUPLICATE, chain.hit(fast, 104));
    assertEquals(1, chain.hits());
    assertEquals(SoulEclipseHitSequence.Result.ADVANCED, chain.hit(UUID.randomUUID(), 104));
  }

  @Test void deadlineDoesNotExtendAfterEachHit() {
    var chain = new SoulEclipseHitSequence(100);
    assertEquals(190, chain.deadline());
    assertEquals(SoulEclipseHitSequence.Result.ADVANCED, chain.hit(UUID.randomUUID(), 100));
    assertEquals(SoulEclipseHitSequence.Result.ADVANCED, chain.hit(UUID.randomUUID(), 180));
    assertEquals(SoulEclipseHitSequence.Result.EXPIRED, chain.hit(UUID.randomUUID(), 191));
    assertEquals(2, chain.hits());
  }

  @Test void nullSwordDoesNotAdvance() {
    var chain = new SoulEclipseHitSequence(0);
    assertEquals(SoulEclipseHitSequence.Result.DUPLICATE, chain.hit(null, 10));
    assertEquals(0, chain.hits());
  }
}
