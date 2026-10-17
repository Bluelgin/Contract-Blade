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
import net.minecraft.world.phys.AABB;
import java.util.ArrayList;
import java.util.List;
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

  private record Zone(int star, AABB bounds) {}
  private record Impact(int star, Vec3 where) {}

  /** One fixed-orientation, server-authoritative strike box per visible star. */
  public static AABB starBox(SoulAnchorEntity core, int index) {
    var at = SoulEclipseGeometry.worldStar(
        new SoulEclipseGeometry.Point(core.getX(), core.getY(), core.getZ()),
        core.eclipseYaw(), index, core.level().getGameTime());
    return new AABB(at.x() - .23, at.y() - .25, at.z() - .23,
        at.x() + .23, at.y() + .25, at.z() + .23);
  }

  private static List<Zone> zones(SoulAnchorEntity core) {
    var regions = new ArrayList<Zone>();
    if (core.eclipse()) {
      // Shattered stars disappear from both the renderer AND server collision.
      // A later sword may pass through an old star's location without false failure.
      for (int i = Math.max(0, core.chainProgress()); i < SoulEclipseGeometry.STARS; i++)
        regions.add(new Zone(i, starBox(core, i)));
    }
    regions.add(new Zone(-1, core.getBoundingBox()));
    return regions;
  }

  private static Zone aiming(List<Zone> regions, Vec3 eye, Vec3 look, boolean eclipse) {
    for (Zone zone : regions) {
      boolean aimed = eclipse
          ? SoulAnchorProjectileGeometry.aimedAtPrecise(zone.bounds, eye, look)
          : SoulAnchorProjectileGeometry.aimedAt(zone.bounds, eye, look);
      if (aimed) return zone; // Satellites take precedence over the larger central body.
    }
    return null;
  }

  private static Impact impact(List<Zone> regions, Vec3 start, Vec3 velocity,
      boolean eclipse) {
    Impact closestStar = null, central = null;
    double bestStar = Double.POSITIVE_INFINITY;
    for (Zone zone : regions) {
      Vec3 hit = eclipse
          ? SoulAnchorProjectileGeometry.firstPreciseHit(zone.bounds, start, velocity)
          : SoulAnchorProjectileGeometry.firstHit(zone.bounds, start, velocity);
      if (hit == null) continue;
      if (zone.star == -1) {
        central = new Impact(-1, hit);
      } else {
        double distance = start.distanceToSqr(hit);
        if (distance < bestStar) {
          bestStar = distance;
          closestStar = new Impact(zone.star, hit);
        }
      }
    }
    return closestStar != null ? closestStar : central;
  }

  /** Only native launch-stamped BASIC SlashBlade swords can progress the star chain.
   *  Wrong star and early moon hits are reported to the state machine as failures.
   */
  public static boolean sweep(ServerLevel level, SoulAnchorEntity core) {
    if (core == null || core.level() != level || !SoulAnchorService.valid(core)
        || core.eclipse() && core.chainProgress() < 0) return false;
    var owner = SoulAnchorBinding.owner(core);
    if (owner == null) return false;
    List<Zone> regions = zones(core);
    Vec3 eye = owner.getEyePosition(), look = owner.getLookAngle();
    Zone aimed = aiming(regions, eye, look, core.eclipse());
    var candidates = new java.util.LinkedHashSet<Projectile>(
        level.getEntitiesOfClass(Projectile.class, core.getBoundingBox().inflate(9.5)));
    if (aimed != null && clear(level, eye, aimed.bounds.getCenter(), owner))
      candidates.addAll(level.getEntitiesOfClass(
          Projectile.class, owner.getBoundingBox().inflate(4)));
    for (Projectile sword : candidates) {
      if (!BASIC_SUMMONED_SWORD.equals(
          BuiltInRegistries.ENTITY_TYPE.getKey(sword.getType()).toString())
          || sword.isRemoved() || sword.getOwner() != owner) continue;
      var proof = ContractCombatCredits.resolveHit(
          owner.damageSources().indirectMagic(sword, owner));
      if (proof == null || proof.player() != owner
          || proof.carrier() != owner.getMainHandItem()
          || !core.binding.equals(ContractCarrierData.getBindingId(proof.carrier())))
        continue;
      var delta = sword.getDeltaMovement();
      // Guide only to the small actual star region the player's crosshair hits.
      // No lock-on to the expected next star, or auto-progress from boss damage.
      if (sword.tickCount <= 1 && aimed != null
          && clear(level, eye, aimed.bounds.getCenter(), owner)) {
        var toNode = aimed.bounds.getCenter().subtract(sword.position());
        double speed = delta.length();
        if (speed >= .2 && speed <= SoulAnchorProjectileGeometry.MAX_SWEEP
            && toNode.lengthSqr() > .0001)
          sword.setDeltaMovement(delta = toNode.normalize().scale(speed));
      }
      Impact hit = impact(regions, sword.position(), delta, core.eclipse());
      if (hit == null || !clear(level, sword.position(), hit.where, owner)) continue;
      boolean accepted = SoulAnchorService.projectileHit(owner, core, hit.star);
      // A wrong but genuinely colliding summoned sword still spends this launch.
      sword.discard();
      if (com.maidweapon.common.SoulDamageConfig.DIAGNOSTICS.get()
          || Boolean.getBoolean("contractblade.soul.validation"))
        com.mojang.logging.LogUtils.getLogger().info(
            "SOUL_NATIVE_SUMMONED_SWORD_{} owner={} core={} star={} sword={}",
            accepted ? "ACCEPTED" : "REJECTED",
            owner.getUUID(), core.getId(), hit.star, sword.getUUID());
      return accepted;
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
