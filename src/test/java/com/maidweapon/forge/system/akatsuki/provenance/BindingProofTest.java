package com.maidweapon.forge.system.akatsuki.provenance;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BindingProofTest {
  @Test
  void missingCarrierBindingCannotAuthorizeNativeDamageProvenance() {
    assertFalse(DamageProvenanceResolver.Proof.matchesBinding(null, null));
    assertFalse(DamageProvenanceResolver.Proof.matchesBinding(null, "previous"));
    assertFalse(DamageProvenanceResolver.Proof.matchesBinding("", ""));
    assertFalse(DamageProvenanceResolver.Proof.matchesBinding("old", null));
    assertFalse(DamageProvenanceResolver.Proof.matchesBinding("old", "new"));
    assertTrue(DamageProvenanceResolver.Proof.matchesBinding("old", "old"));
  }
}
