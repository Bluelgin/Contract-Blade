package com.maidweapon.forge.system.akatsuki.soul;

import com.maidweapon.forge.system.akatsuki.AkatsukiDomainService;
import com.maidweapon.forge.system.akatsuki.provenance.DamageObservation;
import com.maidweapon.forge.system.akatsuki.provenance.DamageObservationService;
import com.maidweapon.forge.system.akatsuki.provenance.DamageObservedEvent;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Real native-hit resonance, never a speculative re-invocation of the player's
 * attack or the expected pre-cap damage. No client C2S authorization path.
 */
@Mod.EventBusSubscriber(modid = "maid_weapon")
public final class SoulResonanceService {
  private static final Map<MinecraftServer, Map<Long, Long>> SEEN = new WeakHashMap<>();

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void observed(DamageObservedEvent e) {
    var o = e.observation;
    if (o == null || e.target == null || o.proof() == null || o.server() == null
        || !o.server().isSameThread() || !DamageObservationService.issued(o, e.target)
        || !o.proof().valid() || !o.returned() || !Double.isFinite(o.actual())
        || o.actual() <= 0 || o.actual() > o.raw() + .02
        || o.entityId() != e.target.getId() || !o.target().equals(e.target.getUUID())
        || !(o.classification() == DamageObservation.Classification.NORMAL_DEFENSE
          || o.classification() == DamageObservation.Classification.VERIFIED_SPECIAL_CAP
          || o.classification() == DamageObservation.Classification.ABSORPTION)
        || !o.phaseBefore().equals(o.phaseAfter())) return;
    // Forge regression fixtures deliberately invoke hurt() with a synthetic
    // "VALIDATION_CONTROLLED_REQUEST" source. It is NOT an actual player attack.
    // Keep those debt-only tests independent of the native-hit echo, but always
    // test real SlashBlade AttackManager calls (even in validation mode).
    if ((Boolean.getBoolean("contractblade.soul.validation")
            || Boolean.getBoolean("contractblade.soulVisual.validation"))
        && "VALIDATION_CONTROLLED_REQUEST".equals(o.proof().origin())) return;
    long now = o.server().overworld().getGameTime();
    var sequences = SEEN.computeIfAbsent(o.server(), ignored -> new HashMap<>());
    if (sequences.size() >= 1024)
      sequences.entrySet().removeIf(entry -> entry.getValue() <= now);
    if (sequences.size() >= 2048 || sequences.putIfAbsent(o.sequence(), now + 200) != null)
      return;
    var p = o.proof().player();
    var session = AkatsukiDomainService.session(p);
    if (session == null || !session.attacking()) return;
    // DamageObservationService emits only after the original native hurt() call
    // finished. Resonance therefore cannot run ahead of the primary melee hit.
    var result = SoulDamageEngine.resonance(p, e.target, session, o.actual());
    if (result != null && result.loss() > 0)
      AkatsukiDomainService.resonanceCue(p, e.target);
  }

  @SubscribeEvent
  public static void stop(ServerStoppedEvent e) {
    SEEN.remove(e.getServer());
  }

  private SoulResonanceService() {}
}
