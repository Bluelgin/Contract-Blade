package com.maidweapon.forge.system.akatsuki.provenance;

import com.maidweapon.forge.system.contract.ContractCarrierData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/** Native invocation proof: exact source and victim, single claim, no tick-based permission. */
public final class NativeAttackOrigin implements AutoCloseable {
  private static final ThreadLocal<NativeAttackOrigin> CURRENT = new ThreadLocal<>();
  private final NativeAttackOrigin previous;
  private final ServerPlayer player;
  private final ItemStack carrier;
  private final String binding, maid, kind;
  private final DamageSource source;
  private final Entity victim;
  private boolean closed, claimed;

  private NativeAttackOrigin(Entity actor, String kind, DamageSource source, Entity victim) {
    previous = CURRENT.get();
    ServerPlayer candidate =
        actor instanceof ServerPlayer p && p.getServer() != null && p.getServer().isSameThread()
            ? p
            : null;
    ItemStack held = ItemStack.EMPTY;
    String bornBinding = "", bornMaid = null;
    try {
      if (candidate != null) {
        held = candidate.getMainHandItem();
        bornBinding = ContractCarrierData.getBindingId(held);
        bornMaid = ContractCarrierData.getBoundMaidUUID(held);
      }
    } catch (RuntimeException | LinkageError failure) {
      candidate = null;
      com.maidweapon.forge.compat.CompatDiagnostics.warnOnce("native-origin-open", failure);
    }
    player = candidate;
    carrier = held;
    binding = bornBinding;
    maid = bornMaid;
    this.kind = kind;
    this.source = source;
    this.victim = victim;
    if (player != null) CURRENT.set(this);
  }

  /** Native melee calculation scope: alone it cannot authorize any observation. */
  public static NativeAttackOrigin open(Entity actor, String kind) {
    return new NativeAttackOrigin(actor, kind, null, null);
  }

  /**
   * Called at the actual native hurt operation, or by an explicitly registered server integration.
   */
  public static NativeAttackOrigin strike(
      Entity actor, String kind, DamageSource source, Entity victim) {
    return new NativeAttackOrigin(actor, kind, source, victim);
  }

  public static NativeAttackOrigin narrow(DamageSource source, Entity victim) {
    try {
      var c = CURRENT.get();
      if (c != null && c.carrierValid() && c.source == null && source.getEntity() == c.player)
        return strike(c.player, c.kind, source, victim);
    } catch (RuntimeException | LinkageError failure) {
      com.maidweapon.forge.compat.CompatDiagnostics.warnOnce("native-origin-narrow", failure);
    }
    return open(null, "UNVERIFIED");
  }

  private boolean carrierValid() {
    return player != null
        && !closed
        && carrier == player.getMainHandItem()
        && java.util.Objects.equals(binding,ContractCarrierData.getBindingId(carrier))
        && java.util.Objects.equals(maid, ContractCarrierData.getBoundMaidUUID(carrier));
  }

  public static boolean claim(
      ServerPlayer p, ItemStack carrier, DamageSource source, Entity target) {
    var c = CURRENT.get();
    if (c == null
        || c.claimed
        || !c.carrierValid()
        || c.player != p
        || c.carrier != carrier
        || c.source != source
        || c.victim != target) return false;
    c.claimed = true;
    return true;
  }

  public static String kind() {
    var c = CURRENT.get();
    return c == null ? "UNVERIFIED" : c.kind;
  }

  public static boolean active() {
    return CURRENT.get() != null;
  }

  public void close() {
    if (closed) return;
    closed = true;
    if (player == null) return;
    if (CURRENT.get() != this) {
      CURRENT.remove();
      return;
    }
    if (previous == null) CURRENT.remove();
    else CURRENT.set(previous);
  }
}
