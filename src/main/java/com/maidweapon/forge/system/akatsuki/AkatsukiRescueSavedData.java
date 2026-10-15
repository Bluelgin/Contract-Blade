package com.maidweapon.forge.system.akatsuki;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** World authority keyed by player UUID; independent of inventory, player clones and dimensions. */
public final class AkatsukiRescueSavedData extends SavedData {
  private final CompoundTag deadlines;

  public AkatsukiRescueSavedData() {
    this(new CompoundTag());
  }

  private AkatsukiRescueSavedData(CompoundTag deadlines) {
    this.deadlines = deadlines;
  }

  public static AkatsukiRescueSavedData get(MinecraftServer server) {
    return server
        .overworld()
        .getDataStorage()
        .computeIfAbsent(
            AkatsukiRescueSavedData::load,
            AkatsukiRescueSavedData::new,
            "maid_weapon_akatsuki_rescue");
  }

  public static AkatsukiRescueSavedData load(CompoundTag tag) {
    return new AkatsukiRescueSavedData(tag.getCompound("Deadlines").copy());
  }

  public long readyAt(UUID owner) {
    return deadlines.getLong(owner.toString());
  }

  public boolean claim(UUID owner, long now, int cooldown) {
    if (now < readyAt(owner)) return false;
    deadlines.putLong(owner.toString(), now + Math.max(1, cooldown));
    setDirty();
    return true;
  }

  @Override
  public CompoundTag save(CompoundTag tag) {
    tag.put("Deadlines", deadlines.copy());
    return tag;
  }
}
