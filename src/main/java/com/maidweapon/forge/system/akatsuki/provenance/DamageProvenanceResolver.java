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
    /** A null/malformed binding cannot authorize damage, even if the new item also lacks one. */
    static boolean matchesBinding(String original, String current) {
      return original != null && !original.isBlank() && original.equals(current);
    }

    public boolean currentCarrier() {
      return player.isAlive()
          && !player.isRemoved()
          && (!player.isCreative() || com.maidweapon.common.SoulDamageConfig.CREATIVE_DAMAGE.get())
          && !player.isSpectator()
          && player.getMainHandItem() == carrier
          && matchesBinding(binding, ContractCarrierData.getBindingId(carrier))
          && java.util.Objects.equals(maid, ContractCarrierData.getBoundMaidUUID(carrier));
    }

    public boolean valid() {
      return currentCarrier() && AkatsukiDomainAuthorization.allows(player, carrier);
    }
  }

  public static Proof resolve(DamageSource source, net.minecraft.world.entity.LivingEntity target) {
    if (AkatsukiDomainDamage.domain(source)
        || com.maidweapon.forge.compat.AkatsukiCombatCompat.secondary(source)
        || source
            .typeHolder()
            .unwrapKey()
            .map(k -> k.location().toString().equals("blade_tetra:akatsuki_execution"))
            .orElse(false)) return null;
    var credit = ContractCombatCredits.resolveHit(source);
    if (credit == null) return null;
    if (source.getEntity() instanceof ServerPlayer
        && source.getDirectEntity() == source.getEntity()
        && com.maidweapon.forge.system.fox.challenge.BlackFoxAttackTrace.projectile() == null
        && !NativeAttackOrigin.claim(credit.player(), credit.carrier(), source, target))
      return null;
    var p =
        new Proof(
            credit.player(),
            credit.carrier(),
            ContractCarrierData.getBindingId(credit.carrier()),
            ContractCarrierData.getBoundMaidUUID(credit.carrier()),
            com.maidweapon.forge.system.fox.challenge.BlackFoxAttackTrace.projectile() != null
                    || source.getDirectEntity() != source.getEntity()
                ? "REGISTERED_LAUNCH"
                : NativeAttackOrigin.active() ? NativeAttackOrigin.kind() : "REGISTERED_ACTOR");
    return p.valid() ? p : null;
  }

  private DamageProvenanceResolver() {}
}
