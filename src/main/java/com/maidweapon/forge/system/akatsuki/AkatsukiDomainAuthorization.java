package com.maidweapon.forge.system.akatsuki;

import com.maidweapon.common.AkatsukiDomainConfig;
import com.maidweapon.forge.api.*;
import com.maidweapon.forge.compat.BladeTetraStoryCompat;
import com.maidweapon.forge.compat.tlm.ContractMaidKeys;
import com.maidweapon.forge.system.MaidEntityDataCodec;
import com.maidweapon.forge.system.contract.ContractAnomalyRecoveryService;
import com.maidweapon.forge.system.deployment.ContractWeaponLocator;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Intrinsic channel only. Does not create spirits, recall maids, or borrow fox identities. */
public final class AkatsukiDomainAuthorization {
  public static final String SPIRIT = "blade_tetra:akatsuki";

  private record Proof(ServerPlayer owner, long tick, CompoundTag contract, boolean allowed) {}

  private static final Map<ItemStack, Proof> PROOFS = new WeakHashMap<>();

  public static boolean allows(ServerPlayer owner, ItemStack carrier) {
    return AkatsukiDomainConfig.ENABLED.get()
        && carrier != null
        && BladeTetraStoryCompat.isAkatsuki(carrier)
        && resident(owner, carrier);
  }

  /**
   * Package-local full residency proof; testing it cannot bypass the required provider identity
   * gate.
   */
  static boolean resident(ServerPlayer owner, ItemStack carrier) {
    if (owner == null
        || carrier == null
        || !owner.isAlive()
        || owner.isSpectator()
        || carrier != owner.getMainHandItem()
        || !ContractCombatCreditResolver.valid(
            new ContractCombatCreditResolver.Credit(owner, carrier))
        || !IntrinsicSpiritApi.isIntrinsicSpiritActive(carrier, SPIRIT)
        || EmbeddedSpiritApi.isDormant(carrier)
        || ContractAnomalyRecoveryService.transitioning(carrier)
        || !IntrinsicSpiritApi.stored(carrier, SPIRIT)) return false;
    var contract = IntrinsicSpiritApi.readOnlyContract(carrier, SPIRIT);
    if (!com.maidweapon.forge.system.contract.IntrinsicSpiritOriginData.get(owner.getServer())
        .matches(
            owner.getUUID(),
            contract.getString("MaidUUID"),
            contract.getString("MaidBindingId"),
            SPIRIT)) return false;
    if (!ContractWeaponLocator.mayManifest(owner, contract.getString("MaidUUID"))) return false;
    long now = owner.getServer().overworld().getGameTime();
    var proof = PROOFS.get(carrier);
    if (proof != null
        && proof.owner() == owner
        && now >= proof.tick()
        && now - proof.tick() < 20
        && proof.contract().equals(contract)) return proof.allowed();
    boolean valid = false;
    try {
      var data = MaidEntityDataCodec.read(contract);
      String binding = contract.getString("MaidBindingId");
      var identity = data.getCompound("ForgeData");
      valid =
          contract.hasUUID("OwnerUUID")
              && owner.getUUID().equals(contract.getUUID("OwnerUUID"))
              && data.hasUUID("Owner")
              && owner.getUUID().equals(data.getUUID("Owner"))
              && data.hasUUID("UUID")
              && data.getUUID("UUID").toString().equals(contract.getString("MaidUUID"))
              && SPIRIT.equals(contract.getString(EmbeddedSpiritApi.TAG_SPIRIT_ID))
              && SPIRIT.equals(identity.getString(ContractMaidKeys.EMBEDDED_SPIRIT))
              && !binding.isBlank()
              && binding.equals(identity.getString(ContractMaidKeys.ENTITY_BINDING_ID));
    } catch (java.io.IOException | RuntimeException rejected) {
      valid = false;
    }
    PROOFS.put(carrier, new Proof(owner, now, contract, valid));
    return valid;
  }

  public static void clear() {
    PROOFS.clear();
  }

  private AkatsukiDomainAuthorization() {}
}
