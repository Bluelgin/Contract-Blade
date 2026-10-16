package com.maidweapon.forge.system.akatsuki.soul;

import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.*;

/** The capability expires with its exact synchronous scope; reusing this object grants nothing. */
final class SoulDamageSource extends DamageSource {
  private static final Set<String> DEFENSE =
      Set.of(
          "bypasses_armor",
          "bypasses_cooldown",
          "bypasses_resistance",
          "bypasses_enchantments",
          "bypasses_shield",
          "bypasses_effects");

  SoulDamageSource(Holder<DamageType> type, ServerPlayer owner) {
    super(type, null, owner);
  }

  @Override
  public boolean is(TagKey<DamageType> tag) {
    var context = SoulStrikeContext.current();
    boolean scoped = context != null && context.matches(this);
    var id = tag.location();
    if (id.getNamespace().equals("minecraft")) {
      if (DEFENSE.contains(id.getPath()))
        return scoped && (context.recovery || id.getPath().equals("bypasses_cooldown"));
      if (id.getPath().equals("bypasses_invulnerability")) return scoped && context.strong;
    }
    if (id.toString().equals("cataclysm:bypasses_hurt_time")) return scoped && context.strong;
    return super.is(tag);
  }
}
