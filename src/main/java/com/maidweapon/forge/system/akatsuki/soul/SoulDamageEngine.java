package com.maidweapon.forge.system.akatsuki.soul;

import static com.maidweapon.forge.system.akatsuki.soul.SoulDamagePolicy.Mode.*;

import com.maidweapon.common.*;
import com.maidweapon.forge.compat.*;
import com.maidweapon.forge.system.akatsuki.*;
import com.maidweapon.forge.system.akatsuki.DomainDamageAdaptation.Channel;
import com.maidweapon.forge.system.contract.ContractCarrierData;
import com.maidweapon.forge.system.mastery.ContractMasteryService;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** One authorized entry for all cuts and pre-reserved soul-burst stages. */
public final class SoulDamageEngine {
  private static final SoulDamageSafetyBudget SAFETY = new SoulDamageSafetyBudget();

  private static final class FeedbackSlot {
    final DomainDamageAdaptation adaptation = new DomainDamageAdaptation();
    long touched;
  }

  private static final Map<LivingEntity, FeedbackSlot> ADAPTATION = new WeakHashMap<>();
  private static long sequence;

  public static boolean applying() {
    return SoulStrikeContext.current() != null;
  }

  public static long now(ServerPlayer p) {
    return p.getServer().overworld().getGameTime();
  }

  public static boolean authorized(ServerPlayer p, LivingEntity t, AkatsukiDomainSession session) {
    return p != null
        && t != null
        && p.isAlive()
        && !p.isRemoved()
        && !p.isSpectator()
        && !p.isCreative()
        && !applying()
        && p.getServer().isSameThread()
        && session != null
        && session.attacking()
        && now(p) >= session.executionPulseUntil
        && AkatsukiDomainAuthorization.allows(p, p.getMainHandItem())
        && session.binding.equals(ContractCarrierData.getBindingId(p.getMainHandItem()))
        && AkatsukiDomainService.hostile(p, t)
        && !t.isRemoved()
        && Float.isFinite(t.getHealth())
        && t.getHealth() > 0;
  }

  /** Legacy independent policy helper, retained for compatibility tests; never mints recovery. */
  @Deprecated
  public static double requested(ServerPlayer p, LivingEntity t, SoulDamagePolicy.Mode mode) {
    var state = DomainTargetCompatibility.inspect(t);
    double scale =
        switch (SoulDamageConfig.STRATEGY.get()) {
          case STANDARD -> .7;
          case AGGRESSIVE -> 1;
          case UNRESTRICTED_EXPERIMENTAL -> 1.25;
        };
    return SoulDamagePolicy.requested(
        mode,
        new SoulDamagePolicy.Inputs(
            p.getAttributeValue(Attributes.ATTACK_DAMAGE),
            t.getMaxHealth(),
            SoulDamageConfig.KNOWN_ADAPTERS.get() ? state.hitCap() : 0,
            ContractMasteryService.getLevel(p),
            SoulDamageConfig.CUT_MULTIPLIER.get(),
            SoulDamageConfig.CAP_FACTOR.get(),
            SoulDamageConfig.BURST_MULTIPLIER.get(),
            SoulDamageConfig.BURST_HEALTH_FRACTION.get(),
            SoulDamageConfig.HIT_CEILING.get(),
            SoulDamageConfig.BURST_CEILING.get(),
            scale));
  }

  private static SoulDamageSafetyBudget.Limits limits() {
    return new SoulDamageSafetyBudget.Limits(
        SoulDamageConfig.HIT_CEILING.get(),
        SoulDamageConfig.TARGET_SECOND.get(),
        SoulDamageConfig.PLAYER_SECOND.get(),
        SoulDamageConfig.TARGET_WINDOW.get(),
        SoulDamageConfig.PLAYER_WINDOW.get(),
        SoulDamageConfig.CUT_GAP.get(),
        SoulDamageConfig.BURST_COOLDOWN.get());
  }

  public static SoulDamageFeedback cut(
      ServerPlayer p, LivingEntity t, AkatsukiDomainSession session) {
    if (!authorized(p, t, session)
        || now(p) - session.lastContact > AkatsukiDomainConfig.CONTACT_GAP.get()) return null;
    boolean recovery = SoulDamageDebtService.hasDebt(p, t);
    double requested =
        recovery
            ? SoulDamageDebtService.available(p, t, false)
            : Math.min(
                SoulDamageConfig.BASE_CUT_MAXIMUM.get(),
                Math.max(0, p.getAttributeValue(Attributes.ATTACK_DAMAGE))
                    * SoulDamageConfig.BASE_CUT_FRACTION.get());
    double amount =
        SAFETY.reserve(p.getUUID(), t.getUUID(), now(p), SPACE_CUT, requested, limits());
    if (amount <= 0) return null;
    if (!recovery) return execute(p, t, session, SPACE_CUT, amount, null, false);
    var reservation = SoulDamageDebtService.reserve(p, t, false, amount);
    if (reservation == null) return null;
    SoulDamageFeedback result = null;
    try (reservation) {
      try {
        return result = execute(p, t, session, SPACE_CUT, reservation.total(), null, true);
      } finally {
        reservation.settle(
            reservation.total(),
            result == null ? 0 : result.loss(),
            result != null && result.attempted(),
            result == null ? -1 : result.sequence());
      }
    }
  }

  public static SoulDamageDebtService.Reservation reserveBurst(
      ServerPlayer p, LivingEntity t, AkatsukiDomainSession s) {
    if (!authorized(p, t, s)) return null;
    double request =
        Math.min(
            SoulDamageDebtService.available(p, t, true),
            Math.min(
                SoulDamageConfig.BURST_CEILING.get(),
                SoulDamageConfig.HIT_CEILING.get() * SoulDamageConfig.BURST_STAGES.get()));
    double amount = SAFETY.reserve(p.getUUID(), t.getUUID(), now(p), SOUL_BURST, request, limits());
    return amount <= 0 ? null : SoulDamageDebtService.reserve(p, t, true, amount);
  }

  static SoulDamageFeedback burstStage(
      ServerPlayer p, LivingEntity t, AkatsukiDomainSession s, double amount, Channel channel) {
    return authorized(p, t, s) && amount <= SoulDamageConfig.HIT_CEILING.get() + .0001
        ? execute(p, t, s, SOUL_BURST, amount, channel, true)
        : null;
  }

  private static SoulDamageFeedback execute(
      ServerPlayer p,
      LivingEntity t,
      AkatsukiDomainSession session,
      SoulDamagePolicy.Mode mode,
      double amount,
      Channel preferred,
      boolean recovery) {
    if (!Double.isFinite(amount) || amount <= 0) return null;
    var beforeState = DomainTargetCompatibility.inspect(t);
    if (beforeState.protectedPhase() || DomainTargetCompatibility.initializing(t)) return null;
    var slot = ADAPTATION.computeIfAbsent(t, ignored -> new FeedbackSlot());
    slot.touched = now(p);
    var memory = slot.adaptation;
    var enabled =
        Arrays.stream(Channel.values())
            .filter(c -> c != Channel.ABSOLUTE || AkatsukiDomainConfig.STRONG.get())
            .filter(
                c ->
                    AkatsukiDomainConfig.CHANNELS.get().contains(c.name().toLowerCase(Locale.ROOT)))
            .toList();
    if (enabled.isEmpty()) return null;
    Channel channel =
        preferred != null && enabled.contains(preferred)
            ? preferred
            : AkatsukiDomainGrowth.has(AkatsukiDomainGrowth.capabilities(p), "adaptation")
                ? memory.choose(now(p), beforeState.phase(), enabled)
                : enabled.get(0);
    if (channel == null) return null;
    var key =
        ResourceKey.create(
            Registries.DAMAGE_TYPE,
            AkatsukiDomainGrowth.id("akatsuki_domain_" + channel.name().toLowerCase(Locale.ROOT)));
    var holder = p.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(key);
    if (holder.isEmpty()) return null;
    var source = new SoulDamageSource(holder.get(), p);
    var vanilla = DomainHitWindow.capture(t);
    var boss =
        SoulDamageConfig.KNOWN_ADAPTERS.get()
            ? DomainTargetCompatibility.hitWindow(t, false)
            : null;
    if (vanilla == null || SoulDamageConfig.KNOWN_ADAPTERS.get() && boss == null) return null;
    boolean aggressive =
        recovery && SoulDamageConfig.STRATEGY.get() != SoulDamageConfig.Strategy.STANDARD;
    float before = t.getHealth();
    boolean returned = false;
    boolean[] attempted = {false};
    String reason = "NO_PROGRESS";
    var context =
        new SoulStrikeContext(
            p.getUUID(),
            t,
            session.binding,
            ++sequence,
            mode,
            source,
            (float) amount,
            recovery,
            aggressive && SoulDamageConfig.EVENT_OVERRIDE.get(),
            aggressive,
            recovery
                && SoulDamageConfig.STRATEGY.get()
                    == SoulDamageConfig.Strategy.UNRESTRICTED_EXPERIMENTAL);
    DomainTargetCompatibility.State afterState;
    try (context;
        vanilla;
        boss) {
      RuntimeException[] callbackFailure = {null};
      returned =
          AkatsukiCombatCompat.guarded(
              () -> {
                try {
                  attempted[0] = true;
                  return t.hurt(source, (float) amount);
                } catch (RuntimeException failure) {
                  callbackFailure[0] = failure;
                  throw failure;
                }
              });
      if (callbackFailure[0] != null) throw callbackFailure[0];
      afterState = DomainTargetCompatibility.inspect(t);
      double lost = before - t.getHealth();
      // Only explicitly registered, audited health systems can receive ONE residual correction.
      if (context.experimental
          && !context.compensated
          && t.isAlive()
          && !t.isRemoved()
          && beforeState.phase().equals(afterState.phase())
          && lost >= 0
          && lost < amount * .65
          && !DomainTargetCompatibility.initializing(t)) {
        context.compensated = true;
        boolean applied =
            AkatsukiCombatCompat.guarded(
                () -> DomainTargetCompatibility.compensate(t, source, (float) (amount - lost)));
        if (!applied) context.compensated = false;
      }
      afterState = DomainTargetCompatibility.inspect(t);
      double actual = before - t.getHealth();
      reason =
          actual > amount * 1.05 + .01
              ? "AMBIGUOUS_EXTRA_LOSS"
              : actual > 0
                  ? actual + .01 >= amount ? "DELIVERED" : "PARTIAL"
                  : !beforeState.phase().equals(afterState.phase())
                      ? "PHASE_CHANGED"
                      : Float.isNaN(context.attack)
                          ? "PRE_EVENT_REJECTION"
                          : Float.isNaN(context.hurtInput)
                              ? "ATTACK_REJECTION"
                              : Float.isNaN(context.damageInput)
                                  ? "CUSTOM_PIPELINE_OR_ABSORPTION"
                                  : "CUSTOM_HEALTH_REJECTION";
    } catch (RuntimeException failure) {
      reason = "EXCEPTION:" + failure.getClass().getSimpleName();
      CompatDiagnostics.warnOnce("soul-strike:" + t.getClass().getName(), failure);
    }
    afterState = DomainTargetCompatibility.inspect(t);
    var feedback =
        new SoulDamageFeedback(
            context.sequence,
            mode,
            channel.name(),
            amount,
            before,
            t.getHealth(),
            returned,
            attempted[0],
            context.attack,
            context.hurtInput,
            context.hurtFinal,
            context.damageInput,
            context.damageFinal,
            context.cancellations,
            context.reductions,
            context.compensated,
            beforeState.phase(),
            afterState.phase(),
            reason);
    memory.feedback(
        channel, before, t.getHealth(), amount, beforeState.phase().equals(afterState.phase()));
    if (SoulDamageConfig.DIAGNOSTICS.get() || Boolean.getBoolean("contractblade.soul.validation"))
      LogUtils.getLogger()
          .info(
              "SOUL_DAMAGE_RESULT attacker={} target={} accounting={} {}",
              p.getUUID(),
              net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(t.getType()),
              recovery ? "VERIFIED_RECOVERY" : "BASE_CUT",
              feedback);
    return feedback;
  }

  public static Channel best(LivingEntity t, Channel fallback) {
    var slot = ADAPTATION.get(t);
    if (slot == null) return fallback;
    var m = slot.adaptation;
    return Arrays.stream(Channel.values())
        .filter(c -> m.score(c) > 0)
        .max(Comparator.comparingDouble(m::score))
        .orElse(fallback);
  }

  public static boolean burstReady(UUID target, long now) {
    return SAFETY.burstReady(target, now);
  }

  public static void finishBurst(UUID target, long now) {
    SAFETY.finishBurst(target, now);
  }

  public static void forget(LivingEntity target) {
    ADAPTATION.remove(target);
  }

  public static void prune(long now) {
    SAFETY.prune(now);
    ADAPTATION
        .entrySet()
        .removeIf(
            e ->
                e.getKey().isRemoved()
                    || !e.getKey().isAlive()
                    || now - e.getValue().touched > 200);
  }

  public static void clear() {
    SAFETY.clear();
    ADAPTATION.clear();
    SoulStrikeContext.clearThread();
  }

  private SoulDamageEngine() {}
}
