package com.maidweapon.forge.system.akatsuki;

import static org.junit.jupiter.api.Assertions.*;

import com.maidweapon.forge.system.contract.IntrinsicSpiritOriginData;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class IntrinsicOriginTest {
  @Test
  void immutableIdentityMustMatchServerOriginAcrossReload() {
    var root = new CompoundTag();
    root.putInt("Schema", 1);
    var entry = new CompoundTag();
    var owner = UUID.randomUUID();
    String maid = UUID.randomUUID().toString();
    entry.putUUID("Owner", owner);
    entry.putString("Binding", "binding");
    entry.putString("Spirit", "blade_tetra:akatsuki");
    var entries = new CompoundTag();
    entries.put(maid, entry);
    root.put("Origins", entries);
    var data = IntrinsicSpiritOriginData.load(root);
    assertTrue(data.matches(owner, maid, "binding", "blade_tetra:akatsuki"));
    assertFalse(data.matches(UUID.randomUUID(), maid, "binding", "blade_tetra:akatsuki"));
    assertFalse(
        data.matches(owner, UUID.randomUUID().toString(), "binding", "blade_tetra:akatsuki"));
    assertFalse(data.matches(owner, maid, "forged", "blade_tetra:akatsuki"));
    assertFalse(data.matches(owner, maid, "binding", "maid_weapon:white_fox"));
    data = IntrinsicSpiritOriginData.load(data.save(new CompoundTag()));
    assertTrue(data.matches(owner, maid, "binding", "blade_tetra:akatsuki"));
  }

  @Test
  void unknownFutureOriginsArePreservedWithoutGrantingAbilities() {
    var root = new CompoundTag();
    root.putInt("Schema", 99);
    root.putString("Future", "keep");
    var data = IntrinsicSpiritOriginData.load(root);
    assertFalse(data.matches(UUID.randomUUID(), "maid", "binding", "blade_tetra:akatsuki"));
    assertEquals(root, data.save(new CompoundTag()));
  }
}
