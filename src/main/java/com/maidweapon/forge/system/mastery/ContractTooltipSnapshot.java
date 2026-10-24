package com.maidweapon.forge.system.mastery;

import com.maidweapon.forge.api.ability.*;
import com.maidweapon.forge.compat.BlackFoxSwordArtCompat;
import com.maidweapon.forge.system.akatsuki.AkatsukiDomainGrowth;
import com.maidweapon.forge.system.fox.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.HashMap;

/**
 * Read-only client tooltip projection. Server resolves datapack definitions, so UI never
 * guesses level values from Java fallbacks. Never usable as an ability permission token.
 */
public final class ContractTooltipSnapshot {
  private static final String VIEW = "maid_weapon:tooltip_mastery_view";

  public static CompoundTag project(int mastery,
      Map<String, ContractAbilityDefinition> definitions) {
    var result = new CompoundTag();
    result.putInt("level", mastery);
    for (var entry : definitions.entrySet()) {
      var route = new CompoundTag();
      if (entry.getValue() != null) {
        var resolved = entry.getValue().resolve(mastery);
        route.putBoolean("known", true);
        for (var feature : resolved.features()) route.putBoolean(feature.getPath(), true);
        // Follow datapack milestones rather than assuming the built-in Lv.1-10 order.
        for (var milestone : entry.getValue().milestones()) {
          if (milestone.level() <= mastery) continue;
          var upcoming = milestone.features().stream()
              .filter(feature -> !resolved.has(feature)).sorted().toList();
          if (upcoming.isEmpty()) continue;
          route.putInt("next_level", milestone.level());
          var names = new net.minecraft.nbt.ListTag();
          for (var feature : upcoming) names.add(net.minecraft.nbt.StringTag.valueOf(feature.getPath()));
          route.put("next_features", names);
          break;
        }
        var parameters = new CompoundTag();
        for (var number : resolved.parameters().entrySet()) {
          if (number.getKey().getNamespace().equals("maid_weapon")
              && Double.isFinite(number.getValue()))
            parameters.putDouble(number.getKey().getPath(), number.getValue());
        }
        // Akatsuki runtime has explicit built-in fallbacks for omitted datapack keys.
        if (entry.getKey().equals("red")) {
          AkatsukiDomainGrowth.parameters(mastery).forEach((key, value) -> {
            if (!parameters.contains(key.getPath())) parameters.putDouble(key.getPath(), value);
          });
        }
        route.put("values", parameters);
      }
      result.put(entry.getKey(), route);
    }
    return result;
  }

  public static CompoundTag server(ServerPlayer player, int mastery) {
    var definitions = new HashMap<String, ContractAbilityDefinition>();
    definitions.put("black", ContractAbilityRegistry.definition(BlackFoxSwordCoreGrowth.ABILITY));
    definitions.put("white", ContractAbilityRegistry.definition(WhiteFoxBlessingGrowth.ID));
    definitions.put("red", ContractAbilityRegistry.definition(AkatsukiDomainGrowth.ID));
    var result = project(mastery, definitions);
    // These booleans describe real current equipped authorization, not simply a high level.
    result.putBoolean("black_equipped",
        ContractAbilityProgressionService.query(player, player.getMainHandItem(),
            BlackFoxSwordCoreGrowth.ABILITY).status() == ContractAbilityProgressionService.Status.READY
        && BlackFoxSwordArtCompat.progressionEntitled(player.getMainHandItem(), player));
    result.putBoolean("white_equipped", WhiteFoxBlessingService.profile(player) != null);
    result.putBoolean("red_equipped",
        ContractAbilityProgressionService.query(player, player.getMainHandItem(),
            AkatsukiDomainGrowth.ID).status() == ContractAbilityProgressionService.Status.READY);
    return result;
  }

  /** Client receives only this owner-scoped projection; it grants no game abilities. */
  public static void applyClient(Player player, CompoundTag data) {
    if (player != null && player.level().isClientSide)
      player.getPersistentData().put(VIEW, data.copy());
  }

  public static CompoundTag view(Player player) {
    if (player == null) return new CompoundTag();
    var stored = player.getPersistentData().getCompound(VIEW);
    return stored.getInt("level") == ContractMasteryService.getLevel(player)
        ? stored : new CompoundTag();
  }

  public static CompoundTag route(Player player, String id) {
    return view(player).getCompound(id);
  }

  public static boolean unlocked(CompoundTag route, String flag) {
    return route.getBoolean("known") && route.getBoolean(flag);
  }

  public static double value(CompoundTag route, String key) {
    return value(route, key, 0);
  }

  public static double value(CompoundTag route, String key, double fallback) {
    var values = route.getCompound("values");
    return values.contains(key) ? values.getDouble(key) : fallback;
  }

  private ContractTooltipSnapshot() {}
}
