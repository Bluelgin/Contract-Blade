package com.maidweapon.forge.system.akatsuki.soul;

import com.maidweapon.forge.system.akatsuki.AkatsukiDomainGrowth;
import com.maidweapon.forge.system.akatsuki.provenance.DamageObservation;
import com.maidweapon.forge.system.akatsuki.provenance.DamageObservationService;
import com.maidweapon.forge.system.akatsuki.provenance.DamageObservedEvent;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Ephemeral native-hit credit for a future precise soul-core critical.
 * It is never damage by itself: no passive native resonance, no speculative replay.
 * Every new native observation replaces the previous receipt for that owner/target.
 */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class SoulCoreCriticalService {
  private record Key(UUID owner, UUID target) {}
  private record Credit(DamageObservation observation, LivingEntity victim) {}
  private static final Map<MinecraftServer, Map<Key, Credit>> CREDITS = new WeakHashMap<>();

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void observed(DamageObservedEvent event) {
    var o = event.observation;
    if (o == null || o.server() == null || o.proof() == null || event.target == null
        || !o.server().isSameThread()
        || !DamageObservationService.issued(o, event.target)) return;
    var claims = CREDITS.computeIfAbsent(o.server(), ignored -> new HashMap<>());
    var key = new Key(o.player(), o.target());
    // A subsequent capped/unknown hit must not redeem an earlier normal critical.
    claims.remove(key);
    if (!o.proof().valid() || !o.returned() || !Double.isFinite(o.actual())
        || o.actual() <= 0 || o.actual() > o.raw() + .02
        || !o.phaseBefore().equals(o.phaseAfter())
        || !(o.classification() == DamageObservation.Classification.NORMAL_DEFENSE
            || o.classification() == DamageObservation.Classification.ABSORPTION)) return;
    if (claims.size() >= 2048) prune(o.server());
    if (claims.size() < 2048) claims.put(key, new Credit(o, event.target));
  }

  private static double amount(ServerPlayer p, LivingEntity target, boolean consume) {
    if (p == null || target == null || p.getServer() == null
        || !p.getServer().isSameThread()) return 0;
    var claims = CREDITS.get(p.getServer());
    if (claims == null) return 0;
    var key = new Key(p.getUUID(), target.getUUID());
    var entry = claims.get(key);
    if (entry == null) return 0;
    var o = entry.observation();
    boolean valid = entry.victim() == target
        && o.entityId() == target.getId()
        && o.server() == p.getServer()
        && o.proof().valid()
        && p.serverLevel().getEntity(target.getUUID()) == target
        && target.isAlive() && !target.isRemoved()
        && o.expires() > p.getServer().overworld().getGameTime()
        && o.phaseAfter().equals(
            com.maidweapon.forge.compat.DomainTargetCompatibility.inspect(target).phase())
        && !com.maidweapon.forge.compat.DomainTargetCompatibility.inspect(target).protectedPhase();
    if (!valid) {
      claims.remove(key);
      return 0;
    }
    // A verified special-limit receipt always takes precedence over a normal hit.
    if (SoulDamageDebtService.hasDebt(p, target)) return 0;
    double bonus = SoulResonancePolicy.requested(
        o.actual(), AkatsukiDomainGrowth.parameter(p, "resonance_hit_ratio"));
    if (consume && bonus > 0) claims.remove(key);
    return bonus;
  }

  public static double available(ServerPlayer p, LivingEntity target) {
    return amount(p, target, false);
  }

  public static double claim(ServerPlayer p, LivingEntity target) {
    return amount(p, target, true);
  }

  private static void prune(MinecraftServer server) {
    var claims = CREDITS.get(server);
    if (claims == null) return;
    long now = server.overworld().getGameTime();
    claims.values().removeIf(c -> now >= c.observation().expires()
        || c.victim().isRemoved() || !c.victim().isAlive()
        || !c.observation().proof().currentCarrier());
  }

  @SubscribeEvent
  public static void tick(TickEvent.ServerTickEvent event) {
    if (event.phase == TickEvent.Phase.END && event.getServer().getTickCount() % 40 == 0)
      prune(event.getServer());
  }

  @SubscribeEvent
  public static void stop(net.minecraftforge.event.server.ServerStoppedEvent event) {
    CREDITS.remove(event.getServer());
  }

  private SoulCoreCriticalService() {}
}
