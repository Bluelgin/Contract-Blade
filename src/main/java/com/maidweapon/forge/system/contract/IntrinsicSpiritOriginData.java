package com.maidweapon.forge.system.contract;

import com.maidweapon.forge.api.event.ContractMaidCapturedEvent;
import com.maidweapon.forge.compat.tlm.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-observed intrinsic origin only. Never controls capture, recall, backup or copy counts. */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class IntrinsicSpiritOriginData extends SavedData {
  private final CompoundTag origins;
  private final boolean writable;

  private IntrinsicSpiritOriginData() {
    this(new CompoundTag(), true);
  }

  private IntrinsicSpiritOriginData(CompoundTag origins, boolean writable) {
    this.origins = origins;
    this.writable = writable;
  }

  public static IntrinsicSpiritOriginData get(MinecraftServer server) {
    return server
        .overworld()
        .getDataStorage()
        .computeIfAbsent(
            IntrinsicSpiritOriginData::load,
            IntrinsicSpiritOriginData::new,
            "maid_weapon_intrinsic_origins");
  }

  public static IntrinsicSpiritOriginData load(CompoundTag root) {
    return new IntrinsicSpiritOriginData(
        root.copy(), !root.contains("Schema") || root.getInt("Schema") == 1);
  }

  /** Called only after the native factory/capture committed a real owned TLM entity. */
  public static void observed(Player owner, Entity maid, ItemStack carrier) {
    if (owner == null
        || owner.getServer() == null
        || !TlmEntityAdapter.isOwnedMaid(maid, owner)
        || !ContractCarrierData.isOwner(carrier, owner)
        || !ContractCarrierData.isBoundMaid(carrier, maid)) return;
    String spirit = maid.getPersistentData().getString(ContractMaidKeys.EMBEDDED_SPIRIT);
    String binding = ContractCarrierData.getBindingId(carrier);
    if (spirit.isBlank()
        || binding.isBlank()
        || !binding.equals(maid.getPersistentData().getString(ContractMaidKeys.ENTITY_BINDING_ID)))
      return;
    var data = get(owner.getServer());
    if (!data.writable) return;
    var record = new CompoundTag();
    record.putUUID("Owner", owner.getUUID());
    record.putString("Binding", binding);
    record.putString("Spirit", spirit);
    var entries = data.origins.getCompound("Origins");
    if (record.equals(entries.getCompound(maid.getStringUUID()))) return;
    entries.put(maid.getStringUUID(), record);
    data.origins.put("Origins", entries);
    data.setDirty();
  }

  public boolean matches(java.util.UUID owner, String maid, String binding, String spirit) {
    if (!writable) return false;
    var record = origins.getCompound("Origins").getCompound(maid);
    return record.hasUUID("Owner")
        && owner.equals(record.getUUID("Owner"))
        && binding.equals(record.getString("Binding"))
        && spirit.equals(record.getString("Spirit"));
  }

  @SubscribeEvent
  public static void captured(ContractMaidCapturedEvent e) {
    observed(e.getPlayer(), e.getMaid(), e.getWeapon());
  }

  @Override
  public CompoundTag save(CompoundTag root) {
    if (!writable) return origins.copy();
    root.merge(origins.copy());
    root.putInt("Schema", 1);
    return root;
  }
}
