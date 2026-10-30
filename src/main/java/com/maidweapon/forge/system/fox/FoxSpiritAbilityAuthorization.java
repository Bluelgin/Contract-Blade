package com.maidweapon.forge.system.fox;

import com.maidweapon.forge.api.ContractAuthorization;
import com.maidweapon.forge.api.IntrinsicSpiritApi;
import com.maidweapon.forge.system.MaidEntityDataCodec;
import com.maidweapon.forge.system.contract.ContractAnomalyRecoveryService;
import com.maidweapon.forge.compat.tlm.ContractMaidKeys;
import net.minecraft.server.MinecraftServer;
import java.util.UUID;
import com.maidweapon.forge.system.deployment.ContractTransferSafetyService;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import com.maidweapon.forge.system.deployment.ContractWeaponLocator;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Shared read-only residency permission for every fox-exclusive ability. Never creates, resets or
 * selects a spirit.
 */
public final class FoxSpiritAbilityAuthorization {
  public record Result(boolean allowed, String reason) {}

  private record Proof(long time, MinecraftServer server, UUIDOwner owner, CompoundTag contract, boolean valid) {}

  private record UUIDOwner(UUID id, String originalMaid) {}

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
    if (ContractAnomalyRecoveryService.transitioning(carrier)) return new Result(false, "not_stored");
    // World identity is cheap and volatile: never cache a permission to spawn/use a second UUID.
    var contract = IntrinsicSpiritApi.readOnlyContract(carrier, spirit);
    String originalMaid = FoxSpiritLedger.get(owner.getServer()).maid(identity);
    String maidId = contract.getString("MaidUUID");
    if (!originalMaid.isEmpty() && !originalMaid.equals(maidId)) return new Result(false, "identity");
    if (com.maidweapon.forge.system.oath.OathService.permitsSpirit(owner, carrier, spirit))
      return new Result(true, "oath");
    if (!IntrinsicSpiritApi.stored(carrier, spirit)) return new Result(false, "not_stored");
    if (!ContractWeaponLocator.mayManifest(owner, maidId)) return new Result(false, "not_stored");
    long now = owner.getServer().overworld().getGameTime();
    var currentOwner = new UUIDOwner(owner.getUUID(), originalMaid);
    var cached = PROOFS.get(carrier);
    if (cached != null
        && cached.server() == owner.getServer()
        && cached.owner().equals(currentOwner)
        // Structural comparison detects legacy and compressed payload edits in place, even
        // when the root tag, declared checksum and decoded length all remain unchanged.
        && cached.contract().equals(contract)
        && now >= cached.time()
        && now - cached.time() < 20)
      return new Result(cached.valid(), cached.valid() ? "ready" : "snapshot");
    boolean valid = false;
    try {
      String binding = contract.getString("MaidBindingId"), maid = contract.getString("MaidUUID");
      var data = MaidEntityDataCodec.read(contract);
      valid =
          contract.hasUUID("OwnerUUID")
              && owner.getUUID().equals(contract.getUUID("OwnerUUID"))
              && data.hasUUID("Owner")
              && owner.getUUID().equals(data.getUUID("Owner"))
              && data.hasUUID("UUID")
              && maid.equals(data.getUUID("UUID").toString())
              && !binding.isEmpty()
              && (data.getCompound("ForgeData")
                      .getString(ContractMaidKeys.ENTITY_BINDING_ID)
                      .isEmpty()
                  || binding.equals(
                      data.getCompound("ForgeData")
                          .getString(
                              ContractMaidKeys.ENTITY_BINDING_ID)));
      String declared = data.getCompound("ForgeData").getString(ContractMaidKeys.EMBEDDED_SPIRIT);
      if (!declared.isEmpty() && !declared.equals(spirit)) valid = false;
    } catch (java.io.IOException | RuntimeException rejected) {
      valid = false;
    }
    PROOFS.put(carrier, new Proof(now, owner.getServer(), currentOwner, contract, valid));
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
