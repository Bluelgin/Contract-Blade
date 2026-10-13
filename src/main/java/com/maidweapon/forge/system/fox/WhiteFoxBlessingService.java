package com.maidweapon.forge.system.fox;

import com.maidweapon.forge.api.ability.*;
import com.maidweapon.forge.api.event.*;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Effects only: qualification/growth/identity/storage live in their existing services. */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class WhiteFoxBlessingService {
  private static final UUID MOVE = UUID.fromString("6305a495-dd87-46a8-9380-7006c343ae11"),
      ATTACK = UUID.fromString("6305a495-dd87-46a8-9380-7006c343ae12"),
      KNOCK = UUID.fromString("6305a495-dd87-46a8-9380-7006c343ae13");

  private record Shield(net.minecraft.world.effect.MobEffectInstance effect, int duration) {}

  private static final Map<ServerPlayer, Shield> SHIELDS = new WeakHashMap<>();

  private static void clearShield(ServerPlayer player) {
    var shield = SHIELDS.remove(player);
    if (shield != null
        && player.getEffect(net.minecraft.world.effect.MobEffects.ABSORPTION) == shield.effect()
        && shield.effect().getAmplifier() == 0
        && shield.effect().getDuration() <= shield.duration())
      player.removeEffect(net.minecraft.world.effect.MobEffects.ABSORPTION);
  }

  private static final Map<ServerPlayer, Long> COMBAT = new WeakHashMap<>(),
      HEAL = new WeakHashMap<>();

  public static void register() {
    ContractAbilityRegistry.register(
        WhiteFoxBlessingGrowth.ID,
        (player, carrier) ->
            acquired(player)
                && com.maidweapon.forge.compat.SlashBladeCompat.isNamedBlade(
                    carrier, "item.slashblade.fox_white")
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

  public static WhiteFoxBlessingGrowth.Profile profile(ServerPlayer player) {
    for (ItemStack hand : List.of(player.getMainHandItem(), player.getOffhandItem())) {
      if (!FoxSpiritState.hasResident(hand)
          || !FoxSpiritState.WHITE.equals(FoxSpiritState.resident(hand).getString("SpiritId")))
        continue;
      var result = ContractAbilityProgressionService.query(player, hand, WhiteFoxBlessingGrowth.ID);
      if (result.status() == ContractAbilityProgressionService.Status.READY)
        return new WhiteFoxBlessingGrowth.Profile(result.capabilities());
    }
    return null;
  }

  private static long now(ServerPlayer player) {
    return player.getServer().overworld().getGameTime();
  }

  private static void modifier(
      ServerPlayer player,
      Attribute attribute,
      UUID id,
      double value,
      AttributeModifier.Operation operation) {
    var instance = player.getAttribute(attribute);
    if (instance == null) return;
    var old = instance.getModifier(id);
    if (old != null && old.getAmount() == value && old.getOperation() == operation) return;
    if (old != null) instance.removeModifier(id);
    if (value != 0)
      instance.addTransientModifier(
          new AttributeModifier(id, "maid_weapon:white_fox_blessing", value, operation));
  }

  public static void remove(ServerPlayer player) {
    clearShield(player);
    modifier(
        player, Attributes.MOVEMENT_SPEED, MOVE, 0, AttributeModifier.Operation.MULTIPLY_TOTAL);
    modifier(
        player, Attributes.ATTACK_SPEED, ATTACK, 0, AttributeModifier.Operation.MULTIPLY_TOTAL);
    modifier(
        player, Attributes.KNOCKBACK_RESISTANCE, KNOCK, 0, AttributeModifier.Operation.ADDITION);
  }

  private static double value(WhiteFoxBlessingGrowth.Profile p, String feature, String parameter) {
    return p != null && p.has(feature) ? p.number(parameter) : 0;
  }

  public static void refresh(ServerPlayer player) {
    apply(player, profile(player));
  }

  private static void apply(ServerPlayer player, WhiteFoxBlessingGrowth.Profile p) {
    if (p == null) clearShield(player);
    modifier(
        player,
        Attributes.MOVEMENT_SPEED,
        MOVE,
        value(p, "blessing_move", "move_speed"),
        AttributeModifier.Operation.MULTIPLY_TOTAL);
    modifier(
        player,
        Attributes.ATTACK_SPEED,
        ATTACK,
        value(p, "blessing_attack", "attack_speed"),
        AttributeModifier.Operation.MULTIPLY_TOTAL);
    modifier(
        player,
        Attributes.KNOCKBACK_RESISTANCE,
        KNOCK,
        value(p, "blessing_knockback", "knockback"),
        AttributeModifier.Operation.ADDITION);
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void tick(TickEvent.PlayerTickEvent event) {
    if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player))
      return;
    var p = profile(player);
    apply(player, p);
    if (p == null || !p.has("blessing_heal")) return;
    long time = now(player);
    COMBAT.putIfAbsent(player, time);
    if (time - COMBAT.get(player) < p.number("heal_delay")
        || time < HEAL.getOrDefault(player, 0L)
        || !player.isAlive()
        || player.getHealth() >= player.getMaxHealth()) return;
    player.heal((float) p.number("heal_amount"));
    HEAL.put(player, time + (long) p.number("heal_interval"));
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void damage(LivingDamageEvent event) {
    if (event.getEntity().level().isClientSide
        || event.getAmount() <= 0
        || !Float.isFinite(event.getAmount())) return;
    if (event.getSource().getEntity() instanceof ServerPlayer attacker)
      COMBAT.put(attacker, now(attacker));
    else {
      var credit =
          com.maidweapon.forge.api.ContractCombatCreditResolver.actor(
              event.getSource().getEntity());
      if (credit != null) COMBAT.put(credit.player(), now(credit.player()));
    }
    if (!(event.getEntity() instanceof ServerPlayer player)) return;
    COMBAT.put(player, now(player));
    var p = profile(player);
    if (p == null) return;
    var source = event.getSource();
    if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
    float amount =
        p.damage(event.getAmount(), source.is(net.minecraft.world.damagesource.DamageTypes.FALL));
    event.setAmount(amount);
    if (!p.has("blessing_oath")
        || amount < player.getHealth()
        || player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)
        || player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) return;
    if (!WhiteFoxBlessingCooldownData.get(player.getServer())
        .consume(player.getUUID(), now(player), (long) p.number("oath_cooldown"))) return;
    event.setAmount(Math.max(0, player.getHealth() - 1));
    if (!player.hasEffect(net.minecraft.world.effect.MobEffects.ABSORPTION)) {
      int duration = (int) p.number("oath_shield_ticks");
      player.addEffect(
          new net.minecraft.world.effect.MobEffectInstance(
              net.minecraft.world.effect.MobEffects.ABSORPTION, duration, 0));
      SHIELDS.put(
          player,
          new Shield(player.getEffect(net.minecraft.world.effect.MobEffects.ABSORPTION), duration));
    }
    player.getServer().overworld().getDataStorage().save();
    player.displayClientMessage(
        net.minecraft.network.chat.Component.translatable("maid_weapon.white_blessing.oath"), true);
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
      COMBAT.remove(player);
      HEAL.remove(player);
    }
  }

  @SubscribeEvent
  public static void death(LivingDeathEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) remove(player);
  }

  @SubscribeEvent
  public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) {
      remove(player);
      refresh(player);
    }
  }

  @SubscribeEvent
  public static void stopped(ServerStoppedEvent event) {
    COMBAT.clear();
    HEAL.clear();
    SHIELDS.clear();
    FoxSpiritAbilityAuthorization.clear();
  }

  private WhiteFoxBlessingService() {}
}
