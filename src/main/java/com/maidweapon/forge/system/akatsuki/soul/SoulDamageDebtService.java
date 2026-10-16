package com.maidweapon.forge.system.akatsuki.soul;

import com.maidweapon.common.SoulDamageConfig;
import com.maidweapon.forge.system.akatsuki.*;
import com.maidweapon.forge.system.akatsuki.provenance.*;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Sole runtime recovery ledger, indexed by attacker/target; no weapon NBT or persistent refill. */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class SoulDamageDebtService {
  private record Entry(DamageObservation observation, LivingEntity target, SoulDamageDebt debt) {}

  private record Key(UUID owner, UUID target) {}

  private static final class State {
    final Map<Long, Entry> entries = new LinkedHashMap<>();
    final Map<Key, List<Entry>> index = new HashMap<>();
    final Map<Long, Long> seen = new HashMap<>();
    final Map<UUID, Long> ready = new HashMap<>();
    long pruned;

    void add(Entry e) {
      index
          .computeIfAbsent(
              new Key(e.observation.player(), e.observation.target()), k -> new ArrayList<>())
          .add(e);
    }

    void rebuild() {
      index.clear();
      entries.values().forEach(this::add);
    }
  }

  private static final Map<MinecraftServer, State> STATES = new WeakHashMap<>();

  private static State state(MinecraftServer s) {
    return STATES.computeIfAbsent(s, k -> new State());
  }

  private static long now(ServerPlayer p) {
    return p.getServer().overworld().getGameTime();
  }

  @SubscribeEvent
  public static void observed(DamageObservedEvent event) {
    var o = event.observation;
    if (o == null
        || event.target == null
        || !DamageObservationService.issued(o, event.target)
        || o.server() == null
        || !o.server().isSameThread()
        || !o.recoverable()
        || o.loss() <= .01
        || !o.proof().currentCarrier()
        || o.expires() <= o.server().overworld().getGameTime()) return;
    var s = state(o.server());
    if (o.server().overworld().getGameTime() - s.pruned >= 20) prune(o.server());
    if (s.entries.size() >= 2048
        || s.seen.size() >= 2048
        || s.index.entrySet().stream()
                .filter(e -> e.getKey().owner.equals(o.player()))
                .mapToInt(e -> e.getValue().size())
                .sum()
            >= 64
        || s.seen.putIfAbsent(o.sequence(), o.expires()) != null) return;
    var e = new Entry(o, event.target, new SoulDamageDebt(o.effective(), o.actual()));
    s.entries.put(o.sequence(), e);
    s.add(e);
  }

  private static boolean valid(Entry e, ServerPlayer p, LivingEntity t, boolean inflight) {
    var o = e.observation;
    var phase = com.maidweapon.forge.compat.DomainTargetCompatibility.inspect(t);
    return o.server() == p.getServer()
        && o.player().equals(p.getUUID())
        && e.target == t
        && o.target().equals(t.getUUID())
        && o.entityId() == t.getId()
        && o.dimension() == p.level().dimension()
        && p.serverLevel().getEntity(t.getUUID()) == t
        && t.isAlive()
        && !t.isRemoved()
        && state(p.getServer()).entries.get(o.sequence()) == e
        && (now(p) < o.expires() || inflight && e.debt.reserved() > 0)
        && o.proof().currentCarrier()
        && !e.debt.invalid()
        && DamageObservationService.sameNormalDefense(o, t)
        && o.phaseAfter().equals(phase.phase())
        && !phase.protectedPhase();
  }

  private static List<Entry> entries(ServerPlayer p, LivingEntity t) {
    return state(p.getServer())
        .index
        .getOrDefault(new Key(p.getUUID(), t.getUUID()), List.of())
        .stream()
        .filter(e -> valid(e, p, t, false))
        .toList();
  }

  /** One snapshot of the same verified ledger and ordinary recovery cooldown. */
  public static SoulCutDecision cutDecision(ServerPlayer p, LivingEntity t) {
    var receipts = entries(p, t);
    if (receipts.isEmpty()) return SoulCutDecision.decide(false, false, 0);
    boolean cooling = now(p) < state(p.getServer()).ready.getOrDefault(t.getUUID(), 0L);
    if (cooling) return SoulCutDecision.decide(true, true, 0);
    double rate = AkatsukiDomainGrowth.parameter(p, "soul_recovery_rate");
    double amount = receipts.stream().mapToDouble(e -> e.debt.available(rate, false)).sum();
    return SoulCutDecision.decide(true, false, amount);
  }

  public static boolean hasDebt(ServerPlayer p, LivingEntity t) {
    return !entries(p, t).isEmpty();
  }

  public static double available(ServerPlayer p, LivingEntity t, boolean aerial) {
    if (!aerial && now(p) < state(p.getServer()).ready.getOrDefault(t.getUUID(), 0L)) return 0;
    double rate =
        AkatsukiDomainGrowth.parameter(p, aerial ? "soul_aerial_rate" : "soul_recovery_rate");
    return entries(p, t).stream().mapToDouble(e -> e.debt.available(rate, aerial)).sum();
  }

  public static final class Reservation implements AutoCloseable {
    private record Portion(Entry entry, double amount) {}

    private final List<Portion> portions;
    private final double total;
    private double consumed, attempted, actual;
    private boolean closed;

    Reservation(List<Portion> p, double n) {
      portions = List.copyOf(p);
      total = n;
    }

    public double total() {
      return total;
    }

    public double attempted() {
      return attempted;
    }

    public double actual() {
      return actual;
    }

    public double consumedAllocation() {
      return consumed;
    }

    public boolean valid(ServerPlayer p, LivingEntity t) {
      return !closed
          && portions.stream().allMatch(q -> SoulDamageDebtService.valid(q.entry, p, t, true));
    }

    public void settle(double request, double loss, boolean dispatched, long strikeSequence) {
      if (closed
          || !Double.isFinite(request)
          || !Double.isFinite(loss)
          || request < 0
          || loss < 0
          || consumed + request > total + .0001)
        throw new IllegalStateException("Duplicate/invalid stage");
      double offset = 0;
      for (var q : portions) {
        double overlap =
            Math.max(
                0, Math.min(consumed + request, offset + q.amount) - Math.max(consumed, offset));
        if (overlap > 0) {
          double paid = loss * overlap / request;
          q.entry.debt.settle(overlap, paid, dispatched);
          if (SoulDamageConfig.DIAGNOSTICS.get()
              || Boolean.getBoolean("contractblade.soul.validation")
              || Boolean.getBoolean("contractblade.soulVisual.validation"))
            com.mojang.logging.LogUtils.getLogger()
                .info(
                    "SOUL_DEBT_SETTLEMENT nativeSequence={} strikeSequence={} allocated={}"
                        + " dispatched={} actualHP={} paidHP={} reservedHP={}",
                    q.entry.observation.sequence(),
                    strikeSequence,
                    overlap,
                    dispatched,
                    paid,
                    q.entry.debt.recovered(),
                    q.entry.debt.reserved());
        }
        offset += q.amount;
      }
      consumed += request;
      if (dispatched) attempted += request;
      actual += loss;
    }

    public void close() {
      if (closed) return;
      closed = true;
      double offset = 0;
      for (var q : portions) {
        q.entry.debt.abandon(Math.max(0, offset + q.amount - Math.max(consumed, offset)));
        offset += q.amount;
      }
    }
  }

  static Reservation reserve(ServerPlayer p, LivingEntity t, boolean aerial, double total) {
    double left = total,
        rate =
            AkatsukiDomainGrowth.parameter(p, aerial ? "soul_aerial_rate" : "soul_recovery_rate");
    var parts = new ArrayList<Reservation.Portion>();
    for (var e : entries(p, t)) {
      double n = e.debt.reserve(rate, left, aerial);
      if (n > 0) {
        parts.add(new Reservation.Portion(e, n));
        left -= n;
      }
      if (left < .000001) break;
    }
    if (total - left <= .000001) return null;
    if (!aerial)
      state(p.getServer()).ready.put(t.getUUID(), now(p) + SoulDamageConfig.RECOVERY_GAP.get());
    return new Reservation(parts, total - left);
  }

  public static void invalidate(ServerPlayer p) {
    var s = STATES.get(p.getServer());
    if (s != null) {
      s.entries.values().removeIf(e -> e.observation.player().equals(p.getUUID()));
      s.rebuild();
    } /* Keep seen until expiry: replay cannot resurrect a discarded receipt. */
  }

  private static void prune(MinecraftServer server) {
    var s = STATES.get(server);
    if (s == null) return;
    long now = server.overworld().getGameTime();
    s.entries
        .values()
        .removeIf(
            e ->
                !e.observation.proof().currentCarrier()
                    || !e.target.isAlive()
                    || e.target.isRemoved()
                    || now >= e.observation.expires() && e.debt.reserved() <= 0
                    || e.target.level() != e.observation.proof().player().level()
                    || e.observation.proof().player().serverLevel().getEntity(e.target.getUUID())
                        != e.target);
    s.seen.values().removeIf(t -> t <= now);
    s.ready.values().removeIf(t -> t < now - 240);
    s.rebuild();
    s.pruned = now;
  }

  @SubscribeEvent
  public static void tick(net.minecraftforge.event.TickEvent.ServerTickEvent e) {
    if (e.phase == net.minecraftforge.event.TickEvent.Phase.END
        && e.getServer().getTickCount() % 20 == 0) prune(e.getServer());
  }

  @SubscribeEvent
  public static void logout(
      net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) {
    if (e.getEntity() instanceof ServerPlayer p) invalidate(p);
  }

  @SubscribeEvent
  public static void dimension(
      net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent e) {
    if (e.getEntity() instanceof ServerPlayer p) invalidate(p);
  }

  @SubscribeEvent
  public static void stop(net.minecraftforge.event.server.ServerStoppedEvent e) {
    STATES.remove(e.getServer());
  }

  private SoulDamageDebtService() {}
}
