package com.maidweapon.forge.system.akatsuki.soul;

import com.maidweapon.common.SoulDamageConfig;
import com.maidweapon.forge.compat.DomainTargetCompatibility;
import com.maidweapon.forge.entity.SoulAnchorEntity;
import com.maidweapon.forge.network.SoulBurstNetwork;
import com.maidweapon.forge.system.akatsuki.*;
import com.maidweapon.forge.system.akatsuki.DomainDamageAdaptation.Channel;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * One shared target reservation, 3–5 stages, at most one stage per tick; extra swings add no
 * budget.
 */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class SoulBurstService {
  private static final class Burst {
    final ServerPlayer player;
    final SoulAnchorEntity core;
    final double total;
    final int stages, duration;
    final long start;
    long next, last = Long.MIN_VALUE;
    int stage;

    Burst(ServerPlayer p, SoulAnchorEntity c, double amount, long now) {
      player = p;
      core = c;
      total = amount;
      start = now;
      next = now;
      stages = SoulDamageConfig.BURST_STAGES.get();
      duration = SoulDamageConfig.BURST_DURATION.get();
    }
  }

  private static final Map<UUID, Burst> ACTIVE = new HashMap<>();

  static boolean strike(ServerPlayer p, SoulAnchorEntity core) {
    long now = SoulDamageEngine.now(p);
    var current = ACTIVE.get(core.original);
    if (current != null) {
      if (current.player != p || current.core != core) return false;
      current.next = Math.min(current.next, now);
      return true;
    }
    if (ACTIVE.size() >= 128 || !SoulDamageEngine.burstReady(core.original, now)) return false;
    var entity = p.serverLevel().getEntity(core.original);
    if (!(entity instanceof LivingEntity t)) return false;
    double total = SoulDamageEngine.reserveBurst(p, t, AkatsukiDomainService.session(p));
    if (total <= 0) return false;
    ACTIVE.put(core.original, new Burst(p, core, total, now));
    return true;
  }

  @SubscribeEvent
  public static void tick(TickEvent.ServerTickEvent e) {
    if (e.phase != TickEvent.Phase.END) return;
    long now = e.getServer().overworld().getGameTime();
    var done = new ArrayList<UUID>();
    for (var entry : ACTIVE.entrySet()) {
      var b = entry.getValue();
      var p = b.player;
      var entity = p.serverLevel().getEntity(entry.getKey());
      if (now > b.start + 32
          || !SoulAnchorService.valid(b.core)
          || !(entity instanceof LivingEntity t)) {
        done.add(entry.getKey());
        continue;
      }
      if (now < b.next || b.last == now) continue;
      // Only mandatory initialization delays a stage. Damage-reduction phases are still attacked.
      if (DomainTargetCompatibility.initializing(t) && now < b.start + 20) {
        b.next = now + 1;
        continue;
      }
      var fallback = Channel.values()[Math.min(b.stage, Channel.values().length - 1)];
      var channel = b.stage >= 2 ? SoulDamageEngine.best(t, fallback) : fallback;
      var result =
          SoulDamageEngine.burstStage(
              p,
              t,
              AkatsukiDomainService.session(p),
              SoulDamagePolicy.stage(b.total, b.stage, b.stages),
              channel);
      // A failed attempt is consumed, never retried indefinitely or refunded for another weapon.
      SoulBurstNetwork.send(p, b.core, t, b.stage, b.stages, result != null && result.success());
      b.last = now;
      b.stage++;
      if (b.stage >= b.stages || !t.isAlive() || t.isRemoved()) done.add(entry.getKey());
      else b.next = now + Math.max(1, b.duration / (b.stages - 1));
    }
    for (var id : done) {
      var b = ACTIVE.remove(id);
      if (b != null) b.core.discard();
      SoulDamageEngine.finishBurst(id, now);
    }
  }

  public static void clear() {
    ACTIVE.clear();
  }

  private SoulBurstService() {}
}
