package com.maidweapon.forge.system.akatsuki;

import static org.junit.jupiter.api.Assertions.*;
import com.maidweapon.forge.system.akatsuki.provenance.GenericSoulLimitEvidence;
import org.junit.jupiter.api.Test;

class GenericSoulLimitEvidenceTest {
  @Test void unfamiliarNativeBossCapDoesNotRequireBossId() {
    assertTrue(GenericSoulLimitEvidence.confirmed(
        100, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5));
    assertTrue(GenericSoulLimitEvidence.confirmed(
        100, 100, 5, 5, 5, 5, 5, 5, 5, 5, 5));
  }

  @Test void forgeLimitWithVerifiableFinalHealthCanAlsoEstablishEvidence() {
    assertTrue(GenericSoulLimitEvidence.confirmed(
        100, 100, 100, 5, 5, 5, 5, 5, 5, 5, 5));
  }

  @Test void ordinaryDefenseAndFullDamageNeverMintRecovery() {
    assertFalse(GenericSoulLimitEvidence.confirmed(
        100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100));
    assertFalse(GenericSoulLimitEvidence.confirmed(
        100, 100, 100, 100, 100, 20, 20, 20, 20, 20, 20));
    assertFalse(GenericSoulLimitEvidence.confirmed(
        100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 0));
  }

  @Test void rejectedAmbiguousOrInjectedSignalsAreNotRecovery() {
    assertFalse(GenericSoulLimitEvidence.confirmed(
        Double.NaN, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5));
    assertFalse(GenericSoulLimitEvidence.confirmed(
        5, 100, 100, 5, 5, 5, 5, 5, 5, 5, 5));
    assertFalse(GenericSoulLimitEvidence.confirmed(
        100, 100, 100, 5, 6, 6, 6, 6, 6, 6, 5));
    assertFalse(GenericSoulLimitEvidence.confirmed(
        100, 100, 100, 5, 5, 5, 5, 5, 5, Double.NaN, 5));
  }
}
