package com.maidweapon.forge.system.akatsuki;

import com.maidweapon.common.AkatsukiDomainConfig;
import com.maidweapon.forge.compat.*;
import com.maidweapon.forge.system.akatsuki.DomainDamageAdaptation.Channel;
import com.maidweapon.forge.system.mastery.ContractMasteryService;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.entity.LivingEntity;

/** One reserved channel and one hurt per pulse; never writes health or invulnerableTime. */
public final class AkatsukiDomainDamage {
  private static boolean applying;
  private static final DomainDamageBudget BUDGET = new DomainDamageBudget();
  private static final Map<LivingEntity, DomainDamageAdaptation> FEEDBACK = new WeakHashMap<>();

  public static boolean applying() {
    return applying;
  }

  public static boolean domain(DamageSource source) {
    return applying
        || source
            .typeHolder()
            .unwrapKey()
            .map(
                k ->
                    k.location().getNamespace().equals("maid_weapon")
                        && k.location().getPath().startsWith("akatsuki_domain_"))
            .orElse(false);
  }

  public static void pulse(
      ServerPlayer player, LivingEntity target, AkatsukiDomainSession session, boolean adaptive) {
    if (applying
        || !AkatsukiDomainAuthorization.allows(player, player.getMainHandItem())
        || !AkatsukiDomainService.hostile(player, target)) return;
    long now = player.getServer().overworld().getGameTime();
    if (!session.active(now)
        || now - session.lastContact > AkatsukiDomainConfig.CONTACT_GAP.get()
        || !session.binding.equals(
            com.maidweapon.forge.system.contract.ContractCarrierData.getBindingId(
                player.getMainHandItem()))) return;
    if (now < session.executionPulseUntil) return;
    var beforeState = DomainTargetCompatibility.inspect(target);
    if (beforeState.protectedPhase()) return;
    var enabled = candidates(adaptive);
    if (enabled.isEmpty()) return;
    var memory = FEEDBACK.computeIfAbsent(target, ignored -> new DomainDamageAdaptation());
    var channel = memory.choose(now, beforeState.phase(), enabled);
    if (channel == null) return;
    int level = ContractMasteryService.getLevel(player);
    double ratio =
        !adaptive
            ? 0
            : level >= 10
                ? AkatsukiDomainConfig.RATIO_10.get()
                : level >= 9
                    ? AkatsukiDomainConfig.RATIO_9.get()
                    : AkatsukiDomainConfig.RATIO_5.get();
    double amount =
        BUDGET.reserve(
            player.getUUID(),
            target.getUUID(),
            now,
            session.spent,
            DomainDamageBudget.policy(
                beforeState.hitCap(),
                beforeState.dpsCap(),
                ratio,
                AkatsukiDomainConfig.FALLBACK_HIT.get(),
                AkatsukiDomainConfig.FALLBACK_SECOND.get(),
                new DomainDamageBudget.Limits(
                    AkatsukiDomainConfig.HIT_CAP.get(),
                    AkatsukiDomainConfig.TARGET_SECOND.get(),
                    AkatsukiDomainConfig.PLAYER_SECOND.get(),
                    AkatsukiDomainConfig.DOMAIN_CAP.get())));
    if (amount <= 0) return;
    session.spent += amount;
    var vanillaWindow = DomainHitWindow.capture(target);
    var bossWindow = DomainTargetCompatibility.hitWindow(target, channel == Channel.ABSOLUTE);
    if (vanillaWindow == null || bossWindow == null) return;
    applying = true;
    try (vanillaWindow;
        bossWindow) {
      var key =
          ResourceKey.create(
              Registries.DAMAGE_TYPE,
              AkatsukiDomainGrowth.id(
                  "akatsuki_domain_" + channel.name().toLowerCase(Locale.ROOT)));
      var holder =
          player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(key);
      if (holder.isEmpty()) return;
      // No direct entity; native execution cannot count a manual/projectile hit. Guard also
      // isolates all provider soul effects for the duration of the synchronous damage call.
      var source = new DamageSource(holder.get(), null, player);
      float before = target.getHealth();
      bossWindow.before();
      AkatsukiCombatCompat.guarded(() -> target.hurt(source, (float) amount));
      var afterState = DomainTargetCompatibility.inspect(target);
      memory.feedback(
          channel,
          before,
          target.getHealth(),
          amount,
          !afterState.protectedPhase() && beforeState.phase().equals(afterState.phase()));
      if (adaptive && memory.score(channel) <= 0)
        CompatDiagnostics.warnOnce(
            "domain-no-health-progress:"
                + net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(
                    target.getType())
                + ":"
                + channel,
            new IllegalStateException(
                "No reliable health progress; bounded probing/fallback only"));
    } finally {
      applying = false;
    }
  }

  private static List<Channel> candidates(boolean adaptive) {
    if (!adaptive)
      return AkatsukiDomainConfig.CHANNELS.get().contains("slash")
          ? List.of(Channel.SLASH)
          : List.of();
    return Arrays.stream(Channel.values())
        .filter(c -> c != Channel.ABSOLUTE || AkatsukiDomainConfig.STRONG.get())
        .filter(
            c -> AkatsukiDomainConfig.CHANNELS.get().contains(c.name().toLowerCase(Locale.ROOT)))
        .toList();
  }

  public static void prune(long now) {
    BUDGET.prune(now);
    FEEDBACK
        .entrySet()
        .removeIf(
            e ->
                e.getKey().isRemoved()
                    || !e.getKey().isAlive()
                    || now - e.getValue().lastUsed() > 200);
  }

  public static void forget(LivingEntity target) {
    FEEDBACK.remove(target);
  }

  public static void clear() {
    BUDGET.clear();
    FEEDBACK.clear();
  }

  private AkatsukiDomainDamage() {}
}
