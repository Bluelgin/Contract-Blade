package com.maidweapon.forge.compat.tlm;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DeferredAkatsukiModelTest {
    @Test void awakeningDoesNotAssignAnUnfinishedModel() {
        assertEquals("", PresetSpiritModels.modelFor(PresetSpiritModels.AKATSUKI));
        assertEquals("", PresetSpiritModels.modelFor("fixture:external"));
        // No rendering lookup or entity mutation when the preset has no model.
        assertDoesNotThrow(() -> PresetSpiritModels.initialize(null, PresetSpiritModels.AKATSUKI));
    }
}
