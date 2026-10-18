package com.maidweapon.forge.system.fox;

import static com.maidweapon.forge.system.fox.WhiteFoxGuardRuntime.Action.*;

import com.maidweapon.forge.api.ability.*;
import com.maidweapon.forge.api.event.*;
import com.maidweapon.forge.compat.SlashBladeCompat;
import com.maidweapon.forge.compat.TouhouLittleMaidHelper;
import com.maidweapon.forge.system.contract.ContractCarrierData;
import com.maidweapon.forge.system.mastery.ContractCombatCredits;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Server effects only; existing services own mastery, residency and combat attribution. */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class WhiteFoxBlessingService {
  private static final UUID MOVE = UUID.fromString("6305a495-dd87-46a8-9380-7006c343ae11"),
      LEGACY_ATTACK = UUID.fromString("6305a495-dd87-46a8-9380-7006c343ae12"),
      LEGACY_KNOCK = UUID.fromString("6305a495-dd87-46a8-9380-7006c343ae13");
  private static final Map<ServerPlayer, WhiteFoxGuardRuntime> RUNTIME = new WeakHashMap<>();

  private record Shield(MobEffectInstance effect, int duration) {}

  private static final Map<ServerPlayer, Shield> SHIELDS = new WeakHashMap<>();
  /** Track the exact effect instances we own, never erase stronger foreign potion buffs. */
  private static final Map<ServerPlayer, Map<MobEffect, MobEffectInstance>> PASSIVE =
      new WeakHashMap<>();
  private static final int PASSIVE_TICKS = 120;
  // Explicit vanilla dispel policy. Never infer dispellability from isBeneficial() or mod tags.
  private static final List<MobEffect> DISPEL =
      List.of(
          MobEffects.POISON,
          MobEffects.WITHER,
          MobEffects.MOVEMENT_SLOWDOWN,
          MobEffects.WEAKNESS,
          MobEffects.BLINDNESS,
          MobEffects.CONFUSION,
          MobEffects.DIG_SLOWDOWN,
          MobEffects.HUNGER);

  public static void register() {
    ContractAbilityRegistry.register(
        WhiteFoxBlessingGrowth.ID,
        (player, carrier) ->
            acquired(player)
                && SlashBladeCompat.isNamedBlade(carrier, "item.slashblade.fox_white")
                && FoxSpiritState.isOriginalHome(carrier)
                && FoxSpiritAbilityAuthorization.allows(player, carrier, FoxSpiritState.WHITE),
        WhiteFoxBlessingGrowth.FEATURES,
        WhiteFoxBlessingGrowth.PARAMETERS,
        WhiteFoxBlessingGrowth.fallback());
  }

  public static boolean acquired(ServerPlayer player) {
    return player
        .getPersistentData()
        .getCompound(Player.PERSISTED_NBT_TAG)
        .getBoolean("MaidWeaponBlackFoxDefeated");
  }

  private static WhiteFoxBlessingGrowth.Profile handProfile(ServerPlayer player, ItemStack hand) {
    var result = ContractAbilityProgressionService.query(player, hand, WhiteFoxBlessingGrowth.ID);
    return result.status() == ContractAbilityProgressionService.Status.READY
        ? new WhiteFoxBlessingGrowth.Profile(result.capabilities())
        : null;
  }

  public static WhiteFoxBlessingGrowth.Profile profile(ServerPlayer player) {
    var main = handProfile(player, player.getMainHandItem());
    return main != null ? main : handProfile(player, player.getOffhandItem());
  }

  /** White fox may legitimately be in the offhand; never charge a different maid. */
  private static ItemStack blessingCarrier(ServerPlayer player) {
    ItemStack main = player.getMainHandItem();
    if (handProfile(player, main) != null) return main;
    ItemStack off = player.getOffhandItem();
    return handProfile(player, off) != null ? off : ItemStack.EMPTY;
  }
  private static boolean affordable(ServerPlayer p,
      com.maidweapon.forge.system.resonance.SpiritAbilityResonanceCosts.Action cost) {
    return com.maidweapon.forge.system.resonance.SpiritAbilityResonanceCosts.affordable(
        p, blessingCarrier(p), cost);
  }
  private static boolean charge(ServerPlayer p,
      com.maidweapon.forge.system.resonance.SpiritAbilityResonanceCosts.Action cost) {
    return com.maidweapon.forge.system.resonance.SpiritAbilityResonanceCosts.pay(
        p, blessingCarrier(p), cost);
  }
  private static long now(ServerPlayer player) {
    return player.getServer().overworld().getGameTime();
  }

  private static WhiteFoxGuardRuntime runtime(ServerPlayer player) {
    return RUNTIME.computeIfAbsent(player, ignored -> new WhiteFoxGuardRuntime());
  }

  private static void movement(ServerPlayer player, double value) {
    var attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
    if (attribute == null) return;
    var old = attribute.getModifier(MOVE);
    if (old != null && old.getAmount() == value) return;
    if (old != null) attribute.removeModifier(MOVE);
    if (value > 0)
      attribute.addTransientModifier(
          new AttributeModifier(
              MOVE,
              "maid_weapon:white_fox_guard",
              value,
              AttributeModifier.Operation.MULTIPLY_TOTAL));
  }

  private static void clearShield(ServerPlayer player) {
    var shield = SHIELDS.remove(player);
    if (shield != null
        && player.getEffect(MobEffects.ABSORPTION) == shield.effect()
        && shield.effect().getAmplifier() == 0
        && shield.effect().getDuration() <= shield.duration())
      player.removeEffect(MobEffects.ABSORPTION);
  }

  private static void shield(ServerPlayer player, long ticks) {
    if (player.hasEffect(MobEffects.ABSORPTION))
      return; // Never replace another mod/potion's shield.
    int duration = (int) ticks;
    if (player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, duration, 0)))
      SHIELDS.put(player, new Shield(player.getEffect(MobEffects.ABSORPTION), duration));
  }

  private static boolean cleanse(LivingEntity target) {
    for (var effect : DISPEL) if (target.hasEffect(effect)) return target.removeEffect(effect);
    return false;
  }

  private static boolean needsCleanse(LivingEntity target) {
    return DISPEL.stream().anyMatch(target::hasEffect);
  }

  private static void passive(ServerPlayer player, MobEffect effect, boolean enabled) {
    var own = PASSIVE.computeIfAbsent(player, ignored -> new IdentityHashMap<>());
    var applied = own.get(effect);
    var active = player.getEffect(effect);
    boolean ours = active != null && active == applied && active.getAmplifier() == 0
        && active.getDuration() <= PASSIVE_TICKS;
    if (!enabled) {
      if (ours) player.removeEffect(effect);
      own.remove(effect);
      if (own.isEmpty()) PASSIVE.remove(player);
      return;
    }
    // If another mod or potion supplies this status, do not replace or shorten it.
    if (active != null && !ours) {
      own.remove(effect);
      return;
    }
    if (active != null && active.getDuration() > PASSIVE_TICKS / 2) return;
    player.addEffect(new MobEffectInstance(effect, PASSIVE_TICKS, 0, true, false, true));
    var granted = player.getEffect(effect);
    if (granted != null && granted.getAmplifier() == 0) own.put(effect, granted);
  }

  private static void passive(ServerPlayer player, WhiteFoxBlessingGrowth.Profile profile) {
    passive(player, MobEffects.MOVEMENT_SPEED, profile != null && profile.has("blessing_heal"));
    passive(player, MobEffects.REGENERATION, profile != null && profile.has("blessing_lantern"));
    passive(player, MobEffects.DAMAGE_RESISTANCE, profile != null && profile.has("blessing_crisis"));
  }

  public static void remove(ServerPlayer player) {
    passive(player, (WhiteFoxBlessingGrowth.Profile) null);
    clearShield(player);
    movement(player, 0);
    // Remove the old route's transient attribute ids on upgrade as well.
    var attack = player.getAttribute(Attributes.ATTACK_SPEED);
    if (attack != null) attack.removeModifier(LEGACY_ATTACK);
    var knock = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
    if (knock != null) knock.removeModifier(LEGACY_KNOCK);
    var state = RUNTIME.get(player);
    if (state != null) state.suspend();
  }

  public static void refresh(ServerPlayer player) {
    refresh(player, profile(player));
  }

  private static void refresh(ServerPlayer player, WhiteFoxBlessingGrowth.Profile p) {
    if (p == null) {
      remove(player);
      return;
    }
    passive(player, p);
    movement(
        player,
        p.has("blessing_mobility") && runtime(player).moving(now(player))
            ? p.number("move_speed")
            : 0);
    var attack = player.getAttribute(Attributes.ATTACK_SPEED);
    if (attack != null) attack.removeModifier(LEGACY_ATTACK);
    var knock = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
    if (knock != null) knock.removeModifier(LEGACY_KNOCK);
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void tick(TickEvent.PlayerTickEvent event) {
    if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player))
      return;
    var p = profile(player);
    refresh(player, p);
    if (p == null || !player.isAlive()) return;
    var state = runtime(player);
    long time = now(player);
    if (p.has("blessing_cleanse")
        && needsCleanse(player)
        && affordable(player, com.maidweapon.forge.system.resonance.SpiritAbilityResonanceCosts.Action.WHITE_CLEANSE)
        && state.consume(CLEANSE, time, p.ticks("cleanse_cooldown"))
        && charge(player, com.maidweapon.forge.system.resonance.SpiritAbilityResonanceCosts.Action.WHITE_CLEANSE)
        && cleanse(player)) WhiteFoxVisualBridge.show(player, WhiteFoxVisualBridge.Cue.CLEANSE);
    crisis(player, p, player.getHealth(), time);
  }

  private static void crisis(
      ServerPlayer player, WhiteFoxBlessingGrowth.Profile p, float health, long time) {
    if (!p.has("blessing_crisis")
        || health <= 0
        || health > player.getMaxHealth() * p.number("crisis_threshold")) return;
    if (!affordable(player,
            com.maidweapon.forge.system.resonance.SpiritAbilityResonanceCosts.Action.WHITE_CRISIS)
        || !WhiteFoxBlessingCooldownData.get(player.getServer())
        .consumeCrisis(player.getUUID(), time, p.ticks("crisis_cooldown"))
        || !charge(player,
            com.maidweapon.forge.system.resonance.SpiritAbilityResonanceCosts.Action.WHITE_CRISIS)) return;
    shield(player, p.ticks("crisis_shield_ticks"));
    cleanse(player);
    WhiteFoxVisualBridge.show(player, WhiteFoxVisualBridge.Cue.CRISIS);
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void damage(LivingDamageEvent event) {
    if (event.getEntity().level().isClientSide
        || event.isCanceled()
        || event.getAmount() <= 0
        || !Float.isFinite(event.getAmount())) return;
    onHit(event);
    if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive()) return;
    var p = profile(player);
    if (p == null
        || event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
    long time = now(player);
    var state = runtime(player);
    float amount = event.getAmount();
    if (p.has("blessing_guard") && state.consume(GUARD, time, p.ticks("guard_cooldown"))) {
      amount = p.guardedDamage(amount);
      WhiteFoxVisualBridge.show(player, WhiteFoxVisualBridge.Cue.GUARD);
      if (p.has("blessing_mobility")) state.move(time, p.ticks("move_ticks"));
      if (p.has("blessing_lantern")) shield(player, p.ticks("guard_shield_ticks"));
      refresh(player);
    }
    if (p.has("blessing_oath") && state.protectedNow(time))
      amount *= (float) (1 - p.number("protection_reduction"));
    event.setAmount(amount);
    if (amount >= player.getHealth()) {
      if (!p.has("blessing_oath")
          || player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)
          || player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) return;
      if (!WhiteFoxBlessingCooldownData.get(player.getServer())
          .consume(player.getUUID(), time, p.ticks("oath_cooldown"))) return;
      // Rescue is unconditional once the persistent oath cooldown is earned.
      // Only the additional cleanse, absorption and reduction package costs resonance.
      event.setAmount(Math.max(0, player.getHealth() - 1));
      if (charge(player,
          com.maidweapon.forge.system.resonance.SpiritAbilityResonanceCosts.Action.WHITE_OATH)) {
        cleanse(player);
        clearShield(player);
        shield(player, p.ticks("oath_shield_ticks"));
        state.protect(time, p.ticks("protection_ticks"));
      }
      WhiteFoxVisualBridge.show(player, WhiteFoxVisualBridge.Cue.OATH);
      player.getServer().overworld().getDataStorage().save();
      player.displayClientMessage(
          net.minecraft.network.chat.Component.translatable("maid_weapon.white_blessing.oath"),
          true);
    } else crisis(player, p, player.getHealth() - amount, time);
  }

  private static boolean sameCarrier(ItemStack a, ItemStack b) {
    String binding = ContractCarrierData.getBindingId(a);
    // A normal weapon or an incompletely bound contract can have no binding NBT.
    return binding != null
        && !binding.isBlank()
        && binding.equals(ContractCarrierData.getBindingId(b));
  }

  private static void onHit(LivingDamageEvent event) {
    var credit = ContractCombatCredits.resolveHit(event.getSource());
    if (credit == null) return;
    var player = credit.player();
    var target = event.getEntity();
    if (target == player
        || !target.isAlive()
        || player.level() != target.level()
        || player.isAlliedTo(target)
        || target.isAlliedTo(player)
        || target instanceof TamableAnimal tame && player.getUUID().equals(tame.getOwnerUUID()))
      return;
    var main = handProfile(player, player.getMainHandItem());
    var off = handProfile(player, player.getOffhandItem());
    var p = main != null ? main : off;
    if (p == null) return;
    boolean whiteHit =
        sameCarrier(credit.carrier(), player.getMainHandItem())
            && SlashBladeCompat.isNamedBlade(credit.carrier(), "item.slashblade.fox_white")
            && FoxSpiritAbilityAuthorization.allows(player, credit.carrier(), FoxSpiritState.WHITE)
            && FoxSpiritState.isOriginalHome(credit.carrier());
    boolean blackHit =
        sameCarrier(credit.carrier(), player.getMainHandItem())
            && SlashBladeCompat.isNamedBlade(credit.carrier(), "item.slashblade.fox_black")
            && FoxSpiritState.isOriginalHome(credit.carrier())
            && FoxSpiritAbilityAuthorization.allows(player, credit.carrier(), FoxSpiritState.BLACK);
    var mode =
        WhiteFoxGuardRuntime.hitMode(
            main != null, off != null, whiteHit, blackHit, p.has("blessing_resonance"));
    if (mode == WhiteFoxGuardRuntime.HitMode.NONE) return;
    long time = now(player);
    var state = runtime(player);
    if (p.has("blessing_heal")
        && state.hit(
            time,
            (int) p.number("heal_hits"),
            p.ticks("heal_interval"),
            player.getHealth() < player.getMaxHealth())
        && charge(player, com.maidweapon.forge.system.resonance.SpiritAbilityResonanceCosts.Action.WHITE_HEAL)) {
      player.heal((float) p.number("heal_amount"));
      WhiteFoxVisualBridge.show(player, WhiteFoxVisualBridge.Cue.HEAL);
      if (p.has("blessing_domain")) healMaid(player, p);
    }
    if (p.has("blessing_edge") && state.consume(EDGE, time, p.ticks("edge_cooldown"))) {
      // Add only to this already successful hit. No recursive hurt or invulnerability reset.
      double bonus =
          mode == WhiteFoxGuardRuntime.HitMode.WHITE
              ? p.number("edge_damage")
              : p.number("resonance_damage");
      event.setAmount((float) Math.min(Float.MAX_VALUE, event.getAmount() + bonus));
      WhiteFoxVisualBridge.show(player, WhiteFoxVisualBridge.Cue.EDGE);
    }
  }

  private static void healMaid(ServerPlayer player, WhiteFoxBlessingGrowth.Profile p) {
    player
        .level()
        .getEntitiesOfClass(
            TamableAnimal.class,
            player.getBoundingBox().inflate(p.number("ally_radius")),
            maid ->
                TouhouLittleMaidHelper.isMaidEntity(maid)
                    && maid.isAlive()
                    && player.getUUID().equals(maid.getOwnerUUID())
                    && maid.distanceToSqr(player)
                        <= p.number("ally_radius") * p.number("ally_radius")
                    && maid.getHealth() < maid.getMaxHealth())
        .stream()
        .min(Comparator.comparingDouble(maid -> maid.distanceToSqr(player)))
        .ifPresent(
            maid -> {
              if (charge(player,
                  com.maidweapon.forge.system.resonance.SpiritAbilityResonanceCosts.Action.WHITE_MAID_HEAL)) {
                maid.heal((float) p.number("ally_heal"));
                WhiteFoxVisualBridge.show(maid, WhiteFoxVisualBridge.Cue.HEAL);
              }
            });
  }

  @SubscribeEvent
  public static void equipped(LivingEquipmentChangeEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) refresh(player);
  }

  @SubscribeEvent
  public static void manifested(ContractMaidManifestedEvent event) {
    if (event.player instanceof ServerPlayer player) {
      refresh(player);
      com.maidweapon.forge.compat.BlackFoxSwordArtCompat.refreshGrowth(player);
    }
  }

  @SubscribeEvent
  public static void captured(ContractMaidCapturedEvent event) {
    if (event.getPlayer() instanceof ServerPlayer player) refresh(player);
  }

  @SubscribeEvent
  public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) {
      remove(player);
      RUNTIME.remove(player);
    }
  }

  @SubscribeEvent
  public static void death(LivingDeathEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) {
      remove(player);
      RUNTIME.remove(player);
    }
  }

  @SubscribeEvent
  public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) {
      remove(player);
      RUNTIME.remove(player);
      refresh(player);
    }
  }

  @SubscribeEvent
  public static void stopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
    for (var player : new ArrayList<>(RUNTIME.keySet())) remove(player);
  }

  @SubscribeEvent
  public static void stopped(ServerStoppedEvent event) {
    RUNTIME.clear();
    SHIELDS.clear();
    PASSIVE.clear();
    FoxSpiritAbilityAuthorization.clear();
  }

  private WhiteFoxBlessingService() {}
}
