package com.maidweapon.forge.system.fox;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Player-global oath receipt, independent of hands, weapons and death clones. */
public final class WhiteFoxBlessingCooldownData extends SavedData {
  private final CompoundTag until;
  private CompoundTag future;
  private CompoundTag crisis = new CompoundTag();

  private WhiteFoxBlessingCooldownData() {
    this(new CompoundTag());
  }

  private WhiteFoxBlessingCooldownData(CompoundTag until) {
    this.until = until;
  }

  public static WhiteFoxBlessingCooldownData get(MinecraftServer server) {
    return server
        .overworld()
        .getDataStorage()
        .computeIfAbsent(
            WhiteFoxBlessingCooldownData::load,
            WhiteFoxBlessingCooldownData::new,
            "maid_weapon_white_fox_oath");
  }

  public static WhiteFoxBlessingCooldownData load(CompoundTag data) {
    var result = new WhiteFoxBlessingCooldownData(data.getCompound("Until").copy());
    result.crisis = data.getCompound("CrisisUntil").copy();
    if (data.contains("Schema") && data.getInt("Schema") != 1) result.future = data.copy();
    return result;
  }

  public boolean ready(UUID owner, long now) {
    return future == null && now >= until.getLong(owner.toString());
  }

  public boolean consume(UUID owner, long now, long cooldown) {
    if (!ready(owner, now)) return false;
    until.putLong(owner.toString(), now + cooldown);
    setDirty();
    return true;
  }

  /** Crisis is also player-global and durable: relogging cannot farm emergency shields. */
  public boolean consumeCrisis(UUID owner, long now, long cooldown) {
    if (future != null || now < crisis.getLong(owner.toString())) return false;
    crisis.putLong(owner.toString(), now + cooldown);
    setDirty();
    return true;
  }

  @Override
  public CompoundTag save(CompoundTag root) {
    if (future != null) return future.copy();
    root.putInt("Schema", 1);
    root.put("Until", until.copy());
    root.put("CrisisUntil", crisis.copy());
    return root;
  }
}
