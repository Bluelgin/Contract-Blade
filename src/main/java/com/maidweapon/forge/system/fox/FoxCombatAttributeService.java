package com.maidweapon.forge.system.fox;

import com.maidweapon.forge.api.ability.ContractAbilityProgressionService;
import com.maidweapon.forge.compat.BlackFoxSwordArtCompat;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Server-authoritative, player-scoped equipment attributes. No blade NBT mutations.
 * Fixed UUID modifiers cannot stack when swapping hands or holding duplicate fox blades.
 */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class FoxCombatAttributeService {
  private static final UUID BLACK_ATTACK = UUID.fromString("9486b2e4-aa11-4ce4-af2e-30feff65b101");
  private static final UUID WHITE_HEALTH = UUID.fromString("9486b2e4-aa11-4ce4-af2e-30feff65b102");
  private static final UUID WHITE_SPEED = UUID.fromString("9486b2e4-aa11-4ce4-af2e-30feff65b103");
  private static final Map<ServerPlayer, Boolean> TOUCHED = new WeakHashMap<>();

  private static void modifier(
      ServerPlayer player, Attribute attribute, UUID uuid, double bonus, String name) {
    var instance = player.getAttribute(attribute);
    if (instance == null) return;
    var current = instance.getModifier(uuid);
    if (current != null && current.getAmount() == bonus
        && current.getOperation() == AttributeModifier.Operation.MULTIPLY_TOTAL) return;
    if (current != null) instance.removeModifier(uuid);
    if (Double.isFinite(bonus) && bonus > 0)
      instance.addTransientModifier(
          new AttributeModifier(uuid, name, bonus, AttributeModifier.Operation.MULTIPLY_TOTAL));
  }

  public static void refresh(ServerPlayer player) {
    if (player == null) return;
    double black = 0, health = 0, speed = 0;
    if (player.isAlive() && !player.isSpectator()) {
      var hand = player.getMainHandItem();
      if (BlackFoxSwordArtCompat.progressionEntitled(hand, player)) {
        var result = ContractAbilityProgressionService.query(
            player, hand, BlackFoxSwordCoreGrowth.ABILITY);
        var profile = BlackFoxSwordCoreGrowth.ready(result);
        if (profile != null) black = profile.number("weapon_attack_bonus", 0);
      }
      var white = WhiteFoxBlessingService.profile(player);
      if (white != null) {
        health = Math.max(0, Math.min(.5, white.number("health_bonus")));
        speed = Math.max(0, Math.min(.3, white.number("attack_speed_bonus")));
      }
    }
    modifier(player, Attributes.ATTACK_DAMAGE, BLACK_ATTACK, black, "maid_weapon:black_fox_attack");
    modifier(player, Attributes.MAX_HEALTH, WHITE_HEALTH, health, "maid_weapon:white_fox_health");
    modifier(player, Attributes.ATTACK_SPEED, WHITE_SPEED, speed, "maid_weapon:white_fox_attack_speed");
    if (black > 0 || health > 0 || speed > 0) TOUCHED.put(player, true);
    else TOUCHED.remove(player);
  }

  private static void clear(ServerPlayer player) {
    modifier(player, Attributes.ATTACK_DAMAGE, BLACK_ATTACK, 0, "maid_weapon:black_fox_attack");
    modifier(player, Attributes.MAX_HEALTH, WHITE_HEALTH, 0, "maid_weapon:white_fox_health");
    modifier(player, Attributes.ATTACK_SPEED, WHITE_SPEED, 0, "maid_weapon:white_fox_attack_speed");
    TOUCHED.remove(player);
  }

  @SubscribeEvent
  public static void tick(TickEvent.PlayerTickEvent event) {
    if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player
        && player.tickCount % 10 == 0) refresh(player);
  }

  @SubscribeEvent
  public static void equipment(LivingEquipmentChangeEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) refresh(player);
  }

  @SubscribeEvent
  public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) refresh(player);
  }

  @SubscribeEvent
  public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) clear(player);
  }

  @SubscribeEvent
  public static void death(LivingDeathEvent event) {
    if (event.getEntity() instanceof ServerPlayer player) clear(player);
  }

  @SubscribeEvent
  public static void stopping(ServerStoppingEvent event) {
    for (var player : new ArrayList<>(TOUCHED.keySet())) clear(player);
  }

  private FoxCombatAttributeService() {}
}
