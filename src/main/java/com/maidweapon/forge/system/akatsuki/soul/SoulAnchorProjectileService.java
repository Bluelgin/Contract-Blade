package com.maidweapon.forge.system.akatsuki.soul;

import com.maidweapon.forge.entity.SoulAnchorEntity;
import com.maidweapon.forge.system.contract.ContractCarrierData;
import com.maidweapon.forge.system.mastery.ContractCombatCredits;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Optional SlashBlade integration without loading a SlashBlade API class.
 * Only native, launch-stamped BASIC summoned swords qualify. No area damage
 * events, arbitrary projectiles, fake owners, or synthetic DamageSource.
 *
 * START-phase swept check catches fast swords before SlashBlade consumes its
 * own collision internally. The issuer's existing launch credit is mandatory.
 * A deliberate player look ray can override native boss lock-on at launch.
 */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class SoulAnchorProjectileService {
  private static final String BASIC_SUMMONED_SWORD = "slashblade:abstract_summoned_sword";

  @SubscribeEvent
  public static void tick(TickEvent.LevelTickEvent event) {
    if (event.phase != TickEvent.Phase.START || !(event.level instanceof ServerLevel level))
      return;
    for (var core : SoulAnchorService.inLevel(level))
      sweep(level, core);
  }

  /** Also used by the actual Forge native projectile fixture. */
  public static boolean sweep(ServerLevel level, SoulAnchorEntity core) {
    if (core == null || core.level() != level || !SoulAnchorService.valid(core)) return false;
    var owner = SoulAnchorBinding.owner(core);
    if (owner == null) return false;
    // Check nearby trajectory sweeps AND fresh swords by the owner's hand.
    // Otherwise a native boss lock could send a distant new sword toward the
    // boss before it ever enters the high core's broad hit neighborhood.
    var candidates = new java.util.LinkedHashSet<Projectile>(
        level.getEntitiesOfClass(Projectile.class, core.getBoundingBox().inflate(9.5)));
    if (SoulAnchorProjectileGeometry.aimedAt(
            core.getBoundingBox(), owner.getEyePosition(), owner.getLookAngle())
        && clear(level, owner.getEyePosition(), core.getBoundingBox().getCenter(), owner))
      candidates.addAll(level.getEntitiesOfClass(
          Projectile.class, owner.getBoundingBox().inflate(4)));
    for (Projectile sword : candidates) {
      if (!BASIC_SUMMONED_SWORD.equals(
          BuiltInRegistries.ENTITY_TYPE.getKey(sword.getType()).toString())
          || sword.isRemoved() || sword.getOwner() != owner) continue;
      // Native ContractCombatCredits snapshots launcher, exact carrier identity,
      // revocation generation and owner at projectile join. A held Red Moon
      // cannot steal another sword launched before the weapon was equipped.
      var proof = ContractCombatCredits.resolveHit(
          owner.damageSources().indirectMagic(sword, owner));
      if (proof == null || proof.player() != owner
          || proof.carrier() != owner.getMainHandItem()
          || !core.binding.equals(ContractCarrierData.getBindingId(proof.carrier())))
        continue;
      // SlashBlade prioritizes an existing boss lock over the player's look ray.
      // Only a FRESH, verified basic sword is gently redirected if its owner is
      // actually looking at this core. No persistent homing or auto-aim scanning.
      var delta = sword.getDeltaMovement();
      if (sword.tickCount <= 1
          && SoulAnchorProjectileGeometry.aimedAt(
              core.getBoundingBox(), owner.getEyePosition(), owner.getLookAngle())
          && clear(level, owner.getEyePosition(),
              core.getBoundingBox().getCenter(), owner)) {
        var toCore = core.getBoundingBox().getCenter().subtract(sword.position());
        double speed = delta.length();
        if (speed >= .2 && speed <= SoulAnchorProjectileGeometry.MAX_SWEEP
            && toCore.lengthSqr() > .0001)
          sword.setDeltaMovement(delta = toCore.normalize().scale(speed));
      }
      var hit = SoulAnchorProjectileGeometry.firstHit(
          core.getBoundingBox(), sword.position(), delta);
      if (hit == null || !clear(level, sword.position(), hit, owner)) continue;
      if (SoulAnchorService.projectileHit(owner, core)) {
        // Do not leave a penetrating sword free to hit another soul core.
        sword.discard();
        if (Boolean.getBoolean("contractblade.soul.validation"))
          com.mojang.logging.LogUtils.getLogger().info(
              "SOUL_NATIVE_SUMMONED_SWORD_ACCEPTED owner={} core={} sword={}",
              owner.getUUID(), core.getId(), sword.getUUID());
        return true;
      }
    }
    return false;
  }

  private static boolean clear(ServerLevel level, Vec3 start, Vec3 hit, ServerPlayer owner) {
    var block = level.clip(new ClipContext(start, hit,
        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
    return block.getType() == HitResult.Type.MISS
        || start.distanceToSqr(block.getLocation()) + 1e-5 >= start.distanceToSqr(hit);
  }

  private SoulAnchorProjectileService() {}
}
