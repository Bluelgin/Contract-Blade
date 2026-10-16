package com.maidweapon.forge.system.akatsuki;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class RescuePersistenceTest {
  @Test
  void worldReceiptSurvivesSaveReloadAndIsPerPlayerNotPerBlade() {
    var id = UUID.randomUUID();
    var data = new AkatsukiRescueSavedData();
    assertTrue(data.claim(id, 100, 3600));
    assertFalse(data.claim(id, 101, 3600));
    var loaded = AkatsukiRescueSavedData.load(data.save(new CompoundTag()));
    assertEquals(3700, loaded.readyAt(id));
    assertFalse(loaded.claim(id, 3699, 3600));
    assertTrue(loaded.claim(UUID.randomUUID(), 200, 3600));
    assertTrue(loaded.claim(id, 3700, 3600));
  }
}
