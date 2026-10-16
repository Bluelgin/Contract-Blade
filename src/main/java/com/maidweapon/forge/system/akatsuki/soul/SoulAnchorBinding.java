package com.maidweapon.forge.system.akatsuki.soul;

import com.maidweapon.common.SoulDamageConfig;
import com.maidweapon.forge.entity.SoulAnchorEntity;
import com.maidweapon.forge.system.akatsuki.*;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class SoulAnchorBinding {
  private static final Map<UUID, ServerPlayer> FIXTURES = new HashMap<>();

  public static void fixtureOwner(ServerPlayer p) {
    if (Boolean.getBoolean("contractblade.soul.validation")) FIXTURES.put(p.getUUID(), p);
  }

  public static boolean unlocked(ServerPlayer p) {
    return !p.isCreative()
        && !p.isSpectator()
        && AkatsukiDomainGrowth.has(AkatsukiDomainGrowth.capabilities(p), "swords")
        && AkatsukiDomainAuthorization.allows(p, p.getMainHandItem());
  }

  public static ServerPlayer owner(SoulAnchorEntity c) {
    if (c.owner == null || c.getServer() == null) return null;
    var p = c.getServer().getPlayerList().getPlayer(c.owner);
    return p == null ? FIXTURES.get(c.owner) : p;
  }

  public static boolean valid(SoulAnchorEntity c) {
    var p = owner(c);
    if (p == null
        || c.original == null
        || c.isRemoved()
        || p.level() != c.level()
        || !p.isAlive()
        || !unlocked(p)
        || SoulDamageEngine.now(p) >= c.expires) return false;
    var s = AkatsukiDomainService.session(p);
    var target = p.serverLevel().getEntity(c.original);
    return c.binding.equals(s.binding)
        && target instanceof LivingEntity t
        && t.getId() == c.targetId()
        && SoulDamageEngine.authorized(p, t, s)
        && (SoulDamageDebtService.available(p, t, true) > .01
            || SoulDamageEngine.now(p) < c.burstUntil)
        && c.distanceToSqr(t)
            <= SoulDamageConfig.LINK_RANGE.get() * SoulDamageConfig.LINK_RANGE.get();
  }

  public static void clear() {
    FIXTURES.clear();
  }

  private SoulAnchorBinding() {}
}
