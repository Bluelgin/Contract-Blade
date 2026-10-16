package com.maidweapon.forge.system.fox;

import com.maidweapon.forge.api.*;
import com.maidweapon.forge.system.MaidEntityDataCodec;
import com.maidweapon.forge.system.contract.*;
import com.maidweapon.forge.system.deployment.ContractTransferSafetyService;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Shared read-only residency permission for every fox-exclusive ability. Never creates, resets or
 * selects a spirit.
 */
public final class FoxSpiritAbilityAuthorization {
  public record Result(boolean allowed, String reason) {}

  private record Proof(long time, Object tag, java.util.List<Object> key, boolean valid) {}

  private static final Map<ItemStack, Proof> PROOFS = new WeakHashMap<>();

  public static Result query(ServerPlayer owner, ItemStack carrier, String spirit) {
    if (owner == null
        || carrier == null
        || spirit == null
        || !owner.isAlive()
        || owner.isSpectator()
        || carrier.isEmpty()
        || ContractTransferSafetyService.isProjectionPhantom(carrier)
        || !ContractAuthorization.allows(owner, carrier)) return new Result(false, "carrier");
    var identity = FoxSpiritState.resident(carrier);
    if (!FoxSpiritState.valid(identity)
        || !spirit.equals(identity.getString("SpiritId"))
        || !owner.getUUID().equals(identity.getUUID("OwnerUUID"))
        || !FoxSpiritLedger.get(owner.getServer()).owns(identity, "WEAPON"))
      return new Result(false, "identity");
    // Copies with the same story identity must not mint two independent maid UUIDs or powers.
    int copies = 0;
    var seen = Collections.newSetFromMap(new IdentityHashMap<ItemStack, Boolean>());
    for (int i = 0; i < owner.getInventory().getContainerSize(); i++) {
      var item = owner.getInventory().getItem(i);
      if (!item.isEmpty()
          && seen.add(item)
          && FoxSpiritState.hasResident(item)
          && FoxSpiritState.resident(item)
              .getUUID("SpiritUUID")
              .equals(identity.getUUID("SpiritUUID"))
          && FoxSpiritLedger.get(owner.getServer()).owns(FoxSpiritState.resident(item), "WEAPON"))
        copies++;
    }
    if (copies > 1) return new Result(false, "copies");
    if (ContractAnomalyRecoveryService.transitioning(carrier)
        || !IntrinsicSpiritApi.stored(carrier, spirit)) return new Result(false, "not_stored");
    long now = owner.getServer().overworld().getGameTime();
    var key = IntrinsicSpiritApi.proofKey(carrier, spirit);
    String activeBinding = key.size() > 1 ? (String) key.get(1) : "";
    if (ContractRecoveryJournal.get(owner.getServer()).blocksAbility(activeBinding))
      return new Result(false, "recovery");
    var cached = PROOFS.get(carrier);
    if (cached != null
        && cached.tag() == carrier.getTag()
        && cached.key().equals(key)
        && now >= cached.time()
        && now - cached.time() < 20)
      return new Result(cached.valid(), cached.valid() ? "ready" : "snapshot");
    boolean valid = false;
    try {
      var contract = IntrinsicSpiritApi.readOnlyContract(carrier, spirit);
      String binding = contract.getString("MaidBindingId"), maid = contract.getString("MaidUUID");
      var incident = ContractRecoveryJournal.get(owner.getServer()).incident(binding);
      String status = incident.getString("Status");
      if (status.equals("COMMITTING")
          || status.equals("REVIEW_REQUIRED")
          || status.equals("PENDING") && incident.getBoolean("Confirmed"))
        return new Result(false, "recovery");
      var data = MaidEntityDataCodec.read(contract);
      valid =
          contract.hasUUID("OwnerUUID")
              && owner.getUUID().equals(contract.getUUID("OwnerUUID"))
              && data.hasUUID("Owner")
              && owner.getUUID().equals(data.getUUID("Owner"))
              && data.hasUUID("UUID")
              && maid.equals(data.getUUID("UUID").toString())
              && com.maidweapon.forge.system.deployment.ContractWeaponLocator.findManifestedMaid(
                      owner, maid)
                  == null
              && !binding.isEmpty()
              && (data.getCompound("ForgeData")
                      .getString(com.maidweapon.forge.compat.tlm.ContractMaidKeys.ENTITY_BINDING_ID)
                      .isEmpty()
                  || binding.equals(
                      data.getCompound("ForgeData")
                          .getString(
                              com.maidweapon.forge.compat.tlm.ContractMaidKeys.ENTITY_BINDING_ID)));
      String declared = data.getCompound("ForgeData").getString("MaidWeaponEmbeddedSpirit");
      if (!declared.isEmpty() && !declared.equals(spirit)) valid = false;
    } catch (java.io.IOException | RuntimeException rejected) {
      valid = false;
    }
    PROOFS.put(carrier, new Proof(now, carrier.getTag(), key, valid));
    return new Result(valid, valid ? "ready" : "snapshot");
  }

  public static boolean allows(ServerPlayer owner, ItemStack carrier, String spirit) {
    return query(owner, carrier, spirit).allowed();
  }

  public static void clear() {
    PROOFS.clear();
  }

  private FoxSpiritAbilityAuthorization() {}
}
