package com.maidweapon.forge.system.akatsuki;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class DomainBindingTest {
  @Test
  void switchingToEmptyHandClearsTargetAndRemainsNullSafe() {
    var session = new AkatsukiDomainSession();
    session.rebind("blade-a", session::suspend);
    session.lifecycle.update(true, true, 1, 0, .3, .5);
    session.contact(10, UUID.randomUUID());
    session.due("cut", 10, 20);
    session.rebind(null, session::suspend);
    session.rebind(null, session::suspend);
    assertEquals("", session.binding);
    assertFalse(session.active());
    assertNull(session.target);
    assertFalse(session.due("cut", 11, 20));
    session.rebind("blade-a", session::suspend);
    session.lifecycle.update(true, true, 1, 12, .3, .5);
    assertTrue(session.attacking());
  }

  @Test
  void sameBladePreservesTargetAndNullLegacyStateCanRecover() {
    var session = new AkatsukiDomainSession();
    session.binding = null;
    assertDoesNotThrow(() -> session.rebind(null, session::suspend));
    session.rebind("blade-a", session::suspend);
    UUID target = UUID.randomUUID();
    session.contact(1, target);
    session.rebind("blade-a", session::suspend);
    assertEquals(target, session.target);
    session.rebind("blade-b", session::suspend);
    assertNull(session.target);
    assertEquals("blade-b", session.binding);
  }
}
