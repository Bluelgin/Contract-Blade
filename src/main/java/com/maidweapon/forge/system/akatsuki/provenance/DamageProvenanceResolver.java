package com.maidweapon.forge.system.akatsuki.provenance;

import com.maidweapon.forge.system.akatsuki.*;
import com.maidweapon.forge.system.contract.ContractCarrierData;
import com.maidweapon.forge.system.mastery.ContractCombatCredits;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;

public final class DamageProvenanceResolver {
  public record Proof(
      ServerPlayer player, ItemStack carrier, String binding, String maid, String origin) {
    public boolean currentCarrier() {
      return player.isAlive()
          && !player.isRemoved()
          && !player.isCreative()
          && !player.isSpectator()
          && player.getMainHandItem() == carrier
          && binding.equals(ContractCarrierData.getBindingId(carrier))
          && java.util.Objects.equals(maid, ContractCarrierData.getBoundMaidUUID(carrier));
    }

    public boolean valid() {
      return currentCarrier() && AkatsukiDomainAuthorization.allows(player, carrier);
    }
  }

  public static Proof resolve(DamageSource source) {
    if (AkatsukiDomainDamage.domain(source)
        || com.maidweapon.forge.compat.AkatsukiCombatCompat.secondary(source)
        || source
            .typeHolder()
            .unwrapKey()
            .map(k -> k.location().toString().equals("blade_tetra:akatsuki_execution"))
            .orElse(false)) return null;
    var credit = ContractCombatCredits.resolveHit(source);
    if (credit == null) return null;
    var p =
        new Proof(
            credit.player(),
            credit.carrier(),
            ContractCarrierData.getBindingId(credit.carrier()),
            ContractCarrierData.getBoundMaidUUID(credit.carrier()),
            com.maidweapon.forge.system.fox.challenge.BlackFoxAttackTrace.projectile() != null
                    || source.getDirectEntity() != source.getEntity()
                ? "REGISTERED_LAUNCH"
                : "DIRECT_ACTOR");
    return p.valid() ? p : null;
  }

  private DamageProvenanceResolver() {}
}
